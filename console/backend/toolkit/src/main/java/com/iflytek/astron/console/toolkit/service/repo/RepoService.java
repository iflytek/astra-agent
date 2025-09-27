package com.iflytek.astron.console.toolkit.service.repo;

import cn.hutool.core.thread.ThreadUtil;
import com.alibaba.fastjson2.*;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.github.pagehelper.Page;
import com.iflytek.astron.console.commons.entity.dataset.DatasetStats;
import com.iflytek.astron.console.commons.service.data.IDatasetFileService;
import com.iflytek.astron.console.commons.util.space.SpaceInfoUtil;
import com.iflytek.astron.console.commons.constant.ResponseEnum;
import com.iflytek.astron.console.commons.exception.BusinessException;
import com.iflytek.astron.console.toolkit.config.properties.ApiUrl;
import com.iflytek.astron.console.toolkit.config.properties.RepoAuthorizedConfig;
import com.iflytek.astron.console.toolkit.common.constant.ProjectContent;
import com.iflytek.astron.console.toolkit.entity.common.PageData;
import com.iflytek.astron.console.toolkit.entity.core.knowledge.*;
import com.iflytek.astron.console.toolkit.entity.dto.*;
import com.iflytek.astron.console.toolkit.mapper.knowledge.KnowledgeMapper;
import com.iflytek.astron.console.toolkit.entity.table.ConfigInfo;
import com.iflytek.astron.console.toolkit.entity.table.group.GroupVisibility;
import com.iflytek.astron.console.toolkit.entity.table.relation.BotRepoRel;
import com.iflytek.astron.console.toolkit.entity.table.relation.FlowRepoRel;
import com.iflytek.astron.console.toolkit.entity.table.repo.*;
import com.iflytek.astron.console.toolkit.entity.vo.knowledge.RepoVO;
import com.iflytek.astron.console.toolkit.handler.*;
import com.iflytek.astron.console.toolkit.mapper.ConfigInfoMapper;
import com.iflytek.astron.console.toolkit.mapper.bot.SparkBotMapper;
import com.iflytek.astron.console.toolkit.mapper.relation.FlowRepoRelMapper;
import com.iflytek.astron.console.toolkit.mapper.repo.FileInfoV2Mapper;
import com.iflytek.astron.console.toolkit.mapper.repo.RepoMapper;
import com.iflytek.astron.console.toolkit.service.bot.BotRepoRelService;
import com.iflytek.astron.console.toolkit.service.bot.BotRepoSubscriptService;
import com.iflytek.astron.console.toolkit.service.extra.OpenPlatformService;
import com.iflytek.astron.console.toolkit.service.group.GroupVisibilityService;
import com.iflytek.astron.console.toolkit.tool.DataPermissionCheckTool;
import com.iflytek.astron.console.toolkit.util.OkHttpUtil;
import com.iflytek.astron.console.toolkit.util.S3Util;
import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.*;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.CollectionUtils;

import java.sql.Timestamp;
import java.util.*;
import java.util.concurrent.CountDownLatch;
import java.util.stream.Collectors;


/**
 * <p>
 * Repository Service Implementation Class Provides comprehensive repository management
 * functionality including CRUD operations, file management, knowledge base operations, and hit
 * testing capabilities.
 * </p>
 *
 * @author xxzhang23
 * @since 2023-12-06
 */
@Service
@Slf4j
public class RepoService extends ServiceImpl<RepoMapper, Repo> {
    /**
     * Get single record by query wrapper
     *
     * @param wrapper query wrapper
     * @return single Repo record or null if not found
     */
    public Repo getOnly(QueryWrapper<Repo> wrapper) {
        wrapper.last("limit 1");
        return this.getOne(wrapper);
    }

    /**
     * Get single record by lambda query wrapper
     *
     * @param wrapper lambda query wrapper
     * @return single Repo record or null if not found
     */
    public Repo getOnly(LambdaQueryWrapper<Repo> wrapper) {
        wrapper.last("limit 1");
        return this.getOne(wrapper);
    }

    @Resource
    RepoMapper repoMapper;

    @Resource
    ConfigInfoMapper configInfoMapper;
    @Resource
    RepoAuthorizedConfig repoAuthorizedConfig;
    @Resource
    KnowledgeV2ServiceCallHandler knowledgeV2ServiceCallHandler;
    @Resource
    BotRepoSubscriptService botRepoSubscriptService;
    @Resource
    BotRepoRelService botRepoRelService;
    @Resource
    HitTestHistoryService historyService;

    @Resource
    @Lazy
    FileInfoV2Service fileInfoV2Service;

    @Resource
    FileInfoV2Mapper fileInfoV2Mapper;
    @Resource
    private IDatasetFileService datasetFileService;

    @Resource
    FileDirectoryTreeService directoryTreeService;
    @Resource
    S3Util s3UtilClient;
    @Resource
    SparkBotMapper sparkBotMapper;
    @Resource
    GroupVisibilityService groupVisibilityService;
    @Resource
    DataPermissionCheckTool dataPermissionCheckTool;
    @Resource
    OpenPlatformService openPlatformService;
    @Autowired
    private FlowRepoRelMapper flowRepoRelMapper;
    @Resource
    private KnowledgeMapper knowledgeMapper;
    @Resource
    private ApiUrl apiUrl;

    /**
     * Create a new repository with the provided repository information. Validates repository name
     * uniqueness, tag validity, and creates the repository record.
     *
     * @param repoVO repository value object containing repository creation information
     * @return created Repo object with generated IDs and default settings
     * @throws BusinessException if repository name is duplicate or tag is invalid
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Repo createRepo(RepoVO repoVO) {
        Long spaceId = SpaceInfoUtil.getSpaceId();
        Repo existRepo;
        if (spaceId == null) {
            existRepo = this.getOnly(Wrappers.lambdaQuery(Repo.class).eq(Repo::getUserId, UserInfoManagerHandler.getUserId()).eq(Repo::getName, repoVO.getName()).eq(Repo::getDeleted, 0));
        } else {
            existRepo = this.getOnly(Wrappers.lambdaQuery(Repo.class).eq(Repo::getSpaceId, spaceId).eq(Repo::getName, repoVO.getName()).eq(Repo::getDeleted, 0));
        }
        if (existRepo != null) {
            throw new BusinessException(ResponseEnum.REPO_NAME_DUPLICATE);
        }

        // Check tag
        if (!ProjectContent.isCbgRagCompatible(repoVO.getTag()) && !ProjectContent.isAiuiRagCompatible(repoVO.getTag())) {
            throw new BusinessException(ResponseEnum.REPO_TYPE_NOT_MATCH);
        }
        // 1. Create knowledge base
        Repo repo = new Repo();
        repo.setAppId(repoVO.getAppId());
        repo.setSource(repoVO.getSource() == null ? 0 : repoVO.getSource());
        repo.setName(repoVO.getName());
        repo.setUserId(UserInfoManagerHandler.getUserId());
        if (StringUtils.isEmpty(repoVO.getOuterRepoId())) {
            String uuid = UUID.randomUUID().toString().replace("-", "");
            repo.setCoreRepoId(uuid);
            repo.setOuterRepoId(uuid);
        } else {
            repo.setCoreRepoId(repoVO.getOuterRepoId());
            repo.setOuterRepoId(repoVO.getOuterRepoId());
        }
        // Boolean enableAudit = repoVO.getEnableAudit();
        // repo.setEnableAudit(enableAudit == null || enableAudit);
        repo.setEnableAudit(false);
        repo.setIcon(repoVO.getAvatarIcon());
        repo.setDescription(repoVO.getDesc());
        repo.setColor(repoVO.getAvatarColor());
        repo.setStatus(ProjectContent.REPO_STATUS_CREATED);
        repo.setDeleted(false);
        Integer visibility = repoVO.getVisibility() == null ? 0 : repoVO.getVisibility();
        repo.setVisibility(visibility);
        Date now = new Date();
        repo.setCreateTime(now);
        repo.setUpdateTime(now);
        repo.setTag(repoVO.getTag());
        if (spaceId != null) {
            repo.setSpaceId(spaceId);
        }
        this.save(repo);

        groupVisibilityService.setRepoVisibility(repo.getId(), 1, visibility, repoVO.getUids());

        // 3. Core system knowledge base creation - removed knowledge base creation
        /*
         * JSONObject repoRequestObject = this.getRepoRequestObject();
         * repoRequestObject.getJSONObject("header").put("businessId",repoAuthorizedConfig.getBusinessId());
         * repoRequestObject.getJSONObject("parameter").put("type", ProjectContent.REPO_OPERATE_CREATED);
         * repoRequestObject.getJSONObject("parameter").put("repoId", repo.getCoreRepoId()); JSONObject
         * jsonObject = knowledgeServiceCallHandler.repoManage(repoRequestObject); if
         * (jsonObject.getJSONObject("header").getInteger("code") !=0) {
         * log.error("Knowledge base creation failed, message:{}",
         * jsonObject.getJSONObject("header").getString("message")); throw new
         * CustomException("Knowledge base creation failed"); }
         */
        return repo;
    }


    /**
     * Update an existing repository with new information. Validates repository existence, ownership,
     * and name uniqueness before updating.
     *
     * @param repoVO repository value object containing update information
     * @return updated Repo object
     * @throws BusinessException if repository does not exist, user has no permission, or name is
     *         duplicate
     */
    @Transactional
    public Repo updateRepo(RepoVO repoVO) {
        Repo model = this.getById(repoVO.getId());
        if (model == null) {
            throw new BusinessException(ResponseEnum.REPO_NOT_EXIST);
        }
        dataPermissionCheckTool.checkRepoBelong(model);
        Long spaceId = SpaceInfoUtil.getSpaceId();
        Repo existRepo;
        if (spaceId == null) {
            existRepo = this.getOnly(Wrappers.lambdaQuery(Repo.class).eq(Repo::getUserId, UserInfoManagerHandler.getUserId()).eq(Repo::getName, repoVO.getName()).eq(Repo::getDeleted, 0));
        } else {
            existRepo = this.getOnly(Wrappers.lambdaQuery(Repo.class).eq(Repo::getSpaceId, spaceId).eq(Repo::getName, repoVO.getName()).eq(Repo::getDeleted, 0));
        }
        if (existRepo != null) {
            if (!Objects.equals(existRepo.getId(), repoVO.getId())) {
                throw new BusinessException(ResponseEnum.REPO_NAME_DUPLICATE);
            }

        }
        Integer visibility = repoVO.getVisibility() == null ? 0 : repoVO.getVisibility();
        model.setVisibility(visibility);
        model.setName(repoVO.getName());
        model.setDescription(repoVO.getDesc());
        model.setColor(repoVO.getAvatarColor());
        model.setIcon(repoVO.getAvatarIcon());
        model.setUpdateTime(new Date());
        this.updateById(model);
        groupVisibilityService.setRepoVisibility(model.getId(), 1, visibility, repoVO.getUids());
        return model;
    }


    /**
     * Update repository status (publish/unpublish/delete). Currently returns true as the actual status
     * update logic is commented out.
     *
     * @param repoVO repository value object containing operation type
     * @return always returns true
     */
    @Transactional
    public boolean updateRepoStatus(RepoVO repoVO) {
        /*
         * Integer operType = repoVO.getOperType(); String repoOperate = ""; switch (operType) { case 2:
         * repoOperate = ProjectContent.REPO_OPERATE_PUBLISHED; break; case 3: repoOperate =
         * ProjectContent.REPO_OPERATE_UNPUBLISHED; break; case 4: repoOperate =
         * ProjectContent.REPO_OPERATE_DELETE; break; default: throw new
         * CustomException("Repository operation type is invalid"); } Repo model =
         * this.getModel(repoVO.getId()); JSONObject repoRequestObject = this.getRepoRequestObject();
         * repoRequestObject.getJSONObject("header").put("businessId",repoAuthorizedConfig.getBusinessId());
         * repoRequestObject.getJSONObject("parameter").put("type", repoOperate);
         * repoRequestObject.getJSONObject("parameter").put("repoId", model.getCoreRepoId()); JSONObject
         * jsonObject = knowledgeServiceCallHandler.repoManage(repoRequestObject); if
         * (jsonObject.getJSONObject("header").getInteger("code") !=0) {
         * log.error("Repository publish failed, message:{}",
         * jsonObject.getJSONObject("header").getString("message")); throw new
         * CustomException("Repository operation failed"); } model.setStatus(repoVO.getOperType());
         * model.setUpdateTime(new Timestamp(System.currentTimeMillis())); this.updateModel(model);
         */
        return true;
    }



    /**
     * Get paginated list of repositories with filtering and search capabilities. Combines local
     * repository data with Spark platform data.
     *
     * @param pageNo page number (starting from 1)
     * @param pageSize number of items per page
     * @param content search content for repository name filtering
     * @param orderBy ordering criteria
     * @param request HTTP servlet request for cookie-based authentication
     * @param tag repository tag filter
     * @return paginated repository data with file counts and character counts
     */
    public PageData<RepoDto> list(Integer pageNo, Integer pageSize, String content, String orderBy, HttpServletRequest request, String tag) {
        Long spaceId = SpaceInfoUtil.getSpaceId();
        List<GroupVisibility> groupVisibilityList = groupVisibilityService.getRepoVisibilityList();
        List<Long> repoIdList = new ArrayList<>();
        if (!CollectionUtils.isEmpty(groupVisibilityList)) {
            for (GroupVisibility groupVisibility : groupVisibilityList) {
                repoIdList.add(Long.valueOf(groupVisibility.getRelationId()));
            }
        }

        // PageHelper.startPage(pageNo, pageSize);
        List<RepoDto> xc_result = repoMapper.list(UserInfoManagerHandler.getUserId(), spaceId, repoIdList, content, orderBy);
        // Get corner badges
        List<ConfigInfo> ragIconInfos = configInfoMapper.getListByCategoryAndCode("ICON", "rag");
        Map<String, String> ragIconMap = ragIconInfos.stream()
                .filter(c -> c.getIsValid() != null && c.getIsValid() == 1) // Only keep valid ones
                .collect(Collectors.toMap(
                        ConfigInfo::getRemarks, // key remains as remarks
                        c -> c.getName() + c.getValue(), // value concatenated as name+value
                        (v1, v2) -> v1 // Take the first one if duplicate remarks
                ));
        // PageInfo<RepoDto> page = new PageInfo<>(xc_result);
        xc_result.forEach(e -> {
            dataPermissionCheckTool.checkRepoBelong(e);
            e.setAddress(s3UtilClient.getS3Prefix());
            e.setCorner(ragIconMap.get(e.getTag()));
            // e.setTag(FILE_SOURCE_CBG_RAG_STR);
            long charCount = 0;
            // set file counts
            List<FileDirectoryTree> fileDirectoryTrees = directoryTreeService.list(Wrappers.lambdaQuery(FileDirectoryTree.class).eq(FileDirectoryTree::getAppId, e.getId().toString()).eq(FileDirectoryTree::getIsFile, 1));
            List<Long> fileIds = fileDirectoryTrees.stream().map(FileDirectoryTree::getFileId).collect(Collectors.toList());
            e.setFileCount((long) fileIds.size());
            if (!fileIds.isEmpty()) {
                List<FileInfoV2> fileInfoV2List = fileInfoV2Mapper.listByIds(fileIds);
                for (FileInfoV2 fileInfoV2 : fileInfoV2List) {
                    charCount += fileInfoV2.getCharCount();
                }
                e.setCharCount(charCount);
            }
        });

        List<RepoDto> result;
        // Get Spark data
        JSONArray xh_result = null;
        if (null == spaceId) {
            xh_result = getStarFireData(request);
        }

        if (xh_result != null) {
            String personalIconAddress = ragIconMap.get(ProjectContent.FILE_SOURCE_SPARK_RAG_STR);
            result = convertAndMergeJsonArrays(xc_result, xh_result, content, personalIconAddress);
        } else {
            result = xc_result;
        }
        // Filter by tag
        if (null != tag && !tag.isEmpty()) {
            result.removeIf(repoDto -> !repoDto.getTag().equals(tag));
        }
        log.info("Final data: {}", result);
        long totalCount = result.size();
        // Re-implement list pagination operation
        result = result.stream()
                .skip((long) (pageNo - 1) * pageSize)
                .limit(pageSize)
                .collect(Collectors.toList());

        PageData<RepoDto> pageData = new PageData<>();
        pageData.setPageData(result);
        pageData.setTotalCount(totalCount);
        return pageData;
    }


    /**
     * Toggle the top status of a repository. Sets or unsets a repository as top priority for the
     * current user.
     *
     * @param id repository ID to toggle top status
     * @throws BusinessException if user has no permission to access the repository
     */
    public void setTop(Long id) {
        Repo repo = repoMapper.selectById(id);
        dataPermissionCheckTool.checkRepoBelong(repo);
        repo.setIsTop(!repo.getIsTop());
        repo.setUpdateTime(new Date());
        repoMapper.updateById(repo);
    }

    public JSONArray getStarFireData(HttpServletRequest request) {
        String authorization = request.getHeader("Authorization");
        Map<String, String> headers = new HashMap<>();
        if (StringUtils.isNotBlank(authorization)) {
            headers.put("Authorization", authorization);
        }
        String response = OkHttpUtil.get(apiUrl.getDatasetUrl(), headers);
        JSONObject jsonObject = JSON.parseObject(response);
        if (jsonObject.get("data") == null) {
            return null;
        } else {
            return JSONArray.parseArray(jsonObject.get("data").toString());
        }
    }

    /**
     * Convert Spark platform JSON data to RepoDto objects and merge with existing repository list.
     * Filters results by content if specified.
     *
     * @param xingchen existing repository list to merge with
     * @param arrayB JSON array from Spark platform containing dataset information
     * @param content search content for filtering by repository name
     * @param personalIconAddress icon address for Spark repositories
     * @return merged list of repositories including Spark data
     */
    public static List<RepoDto> convertAndMergeJsonArrays(List<RepoDto> xingchen, JSONArray arrayB, String content, String personalIconAddress) {
        if (arrayB != null) {
            for (int i = 0; i < arrayB.size(); i++) {
                JSONObject itemB = arrayB.getJSONObject(i);
                RepoDto repoDto = new RepoDto();
                repoDto.setId(itemB.getLong("id"));
                repoDto.setName(itemB.getString("name"));
                repoDto.setUserId(itemB.getString("uid"));
                repoDto.setAppId(null);
                repoDto.setOuterRepoId(null);
                repoDto.setCoreRepoId(itemB.getString("id"));
                repoDto.setDescription(itemB.getString("description"));
                repoDto.setIcon(null);
                repoDto.setColor(null);
                repoDto.setStatus(itemB.getInteger("status"));
                repoDto.setEmbeddedModel(null);
                repoDto.setIndexType(null);
                repoDto.setVisibility(null);
                repoDto.setSource(null);
                repoDto.setEnableAudit(null);
                repoDto.setDeleted(null);
                repoDto.setCreateTime(itemB.getDate("createTime"));
                repoDto.setUpdateTime(itemB.getDate("updateTime"));
                repoDto.setIsTop(null);
                repoDto.setTagDtoList(null);
                repoDto.setCorner(personalIconAddress);
                JSONArray botList = itemB.getJSONArray("botList");
                List<SparkBotVO> bots = new ArrayList<>();
                if (!CollectionUtils.isEmpty(botList)) {
                    for (int j = 0; j < botList.size(); j++) {
                        // Get each bot object
                        JSONObject bot = botList.getJSONObject(j);
                        SparkBotVO botVO = new SparkBotVO();
                        botVO.setName(bot.getString("name"));
                        botVO.setUuid(bot.getString("botId"));
                        bots.add(botVO);
                    }
                }
                repoDto.setBots(bots);
                repoDto.setFileCount(itemB.getLong("fileNum"));
                repoDto.setCharCount(itemB.getLong("charCount"));
                repoDto.setKnowledgeCount(null);
                repoDto.setTag(ProjectContent.FILE_SOURCE_SPARK_RAG_STR);
                xingchen.add(repoDto);
            }
            if (StringUtils.isNotBlank(content)) {
                return xingchen.stream().filter(repo -> repo.getName().contains(content)).collect(Collectors.toList());
            }
        }
        return xingchen;
    }


    /**
     * Get paginated list of repositories with enhanced performance using parallel processing. Combines
     * local repository data with Spark platform data and performs parallel data enhancement.
     *
     * @param pageNo page number (starting from 1)
     * @param pageSize number of items per page
     * @param content search content for repository name filtering
     * @param request HTTP servlet request for cookie-based authentication
     * @return paginated repository data with enhanced information including bots, file counts, and
     *         character counts
     */
    public PageData<RepoDto> listRepos(Integer pageNo, Integer pageSize, String content, HttpServletRequest request) {
        Long spaceId = SpaceInfoUtil.getSpaceId();
        List<Long> repoIdList = getAccessibleRepoIds();
        // 1) Query local repository data
        Page<RepoDto> repoDtoPage = repoMapper.getModelListByCondition(UserInfoManagerHandler.getUserId(), spaceId, repoIdList, content);
        List<RepoDto> xcResult = repoDtoPage == null ? new ArrayList<>() : repoDtoPage.getResult();
        if (xcResult == null)
            xcResult = new ArrayList<>();
        for (RepoDto repoDto : xcResult) {
            dataPermissionCheckTool.checkRepoBelong(repoDto);
        }

        // 2) Badge mapping + S3 address
        String address = s3UtilClient.getS3Prefix();
        Map<String, String> ragIconMap = buildRagIconMap();

        // 3) Parallel enhancement: A) Badge+Bots B) File count/Character count/Knowledge count
        CountDownLatch latch = new CountDownLatch(2);
        List<RepoDto> finalListA = xcResult;
        ThreadUtil.execute(() -> {
            try {
                attachBotsAndCorner(finalListA, address, ragIconMap);
            } finally {
                latch.countDown();
            }
        });
        List<RepoDto> finalListB = xcResult;
        ThreadUtil.execute(() -> {
            try {
                attachCounts(finalListB);
            } finally {
                latch.countDown();
            }
        });
        try {
            latch.await();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException(e);
        }

        // 4) Merge Spark data
        JSONArray sparkList = (spaceId == null) ? getStarFireData(request) : null;
        String personalIconAddress = ragIconMap.get(ProjectContent.FILE_SOURCE_SPARK_RAG_STR);
        List<RepoDto> merged = convertAndMergeJsonArrays(xcResult, sparkList, content, personalIconAddress);

        // 5) Paginate and return
        long totalCount = merged.size();
        List<RepoDto> pageDataList = paginate(merged, pageNo, pageSize);

        PageData<RepoDto> pageData = new PageData<>();
        pageData.setPageData(pageDataList);
        pageData.setTotalCount(totalCount);
        return pageData;
    }

    /** Get list of accessible repository IDs based on user permissions */
    private List<Long> getAccessibleRepoIds() {
        List<GroupVisibility> visibility = groupVisibilityService.getRepoVisibilityList();
        if (CollectionUtils.isEmpty(visibility))
            return Collections.emptyList();
        return visibility.stream().map(v -> Long.valueOf(v.getRelationId())).collect(Collectors.toList());
    }

    /** Build ICON/rag badge mapping (only take isValid=1) */
    private Map<String, String> buildRagIconMap() {
        List<ConfigInfo> ragIconInfos = configInfoMapper.getListByCategoryAndCode("ICON", "rag");
        if (CollectionUtils.isEmpty(ragIconInfos))
            return Collections.emptyMap();
        return ragIconInfos.stream()
                .filter(c -> c.getIsValid() != null && c.getIsValid() == 1)
                .collect(Collectors.toMap(
                        ConfigInfo::getRemarks,
                        c -> c.getName() + c.getValue(),
                        (v1, v2) -> v1));
    }

    /** Set badge/address for each RepoDto and attach Bots (including workflow bindings) */
    private void attachBotsAndCorner(List<RepoDto> repos, String address, Map<String, String> ragIconMap) {
        if (CollectionUtils.isEmpty(repos))
            return;
        for (RepoDto repoDto : repos) {
            repoDto.setCorner(ragIconMap.get(repoDto.getTag()));
            repoDto.setAddress(address);

            // Agent Bots
            List<SparkBotVO> sparkBotVOList = sparkBotMapper.listSparkBotByRepoId(repoDto.getId(), UserInfoManagerHandler.getUserId());
            if (!CollectionUtils.isEmpty(sparkBotVOList)) {
                sparkBotVOList.forEach(e -> e.setAddress(address));
            }

            // Workflow-bound "Bots"
            List<FlowRepoRel> rels = flowRepoRelMapper.selectList(
                    new LambdaQueryWrapper<FlowRepoRel>().eq(FlowRepoRel::getRepoId, repoDto.getCoreRepoId()));
            if (!CollectionUtils.isEmpty(rels)) {
                for (FlowRepoRel rel : rels) {
                    SparkBotVO bot = new SparkBotVO();
                    bot.setUuid(rel.getFlowId());
                    sparkBotVOList.add(bot);
                }
            }

            // Compatible with extended sources (reserved)
            List<JSONObject> sparkBots = new ArrayList<>();
            for (JSONObject sparkBot : sparkBots) {
                SparkBotVO bot = new SparkBotVO();
                bot.setName(sparkBot.getString("name"));
                bot.setUuid(sparkBot.getString("botId"));
                sparkBotVOList.add(bot);
            }
            repoDto.setBots(sparkBotVOList);
        }
    }

    /** Calculate file count / character count / knowledge count for each RepoDto */
    private void attachCounts(List<RepoDto> repos) {
        if (CollectionUtils.isEmpty(repos))
            return;
        for (RepoDto repoDto : repos) {
            long charCount = 0L;
            // File directory tree: only count valid files
            List<FileDirectoryTree> trees = directoryTreeService.list(
                    Wrappers.lambdaQuery(FileDirectoryTree.class)
                            .eq(FileDirectoryTree::getAppId, repoDto.getId().toString())
                            .eq(FileDirectoryTree::getIsFile, 1)
                            .eq(FileDirectoryTree::getStatus, 1));
            List<Long> fileIds = trees.stream().map(FileDirectoryTree::getFileId).collect(Collectors.toList());
            repoDto.setFileCount((long) fileIds.size());

            if (!fileIds.isEmpty()) {
                List<FileInfoV2> files = fileInfoV2Mapper.listByIds(fileIds);
                for (FileInfoV2 f : files) {
                    charCount += f.getCharCount();
                }
                repoDto.setCharCount(charCount);

                // Count knowledge entries (by file uuid)
                List<String> fileUuids = files.stream().map(FileInfoV2::getUuid).collect(Collectors.toList());
                // long knowledgeCount = mongoTemplate.count(new Query(Criteria.where("fileId").in(fileUuids)),
                // Knowledge.class);
                long knowledgeCount = knowledgeMapper.countByFileIdIn(fileUuids);
                repoDto.setKnowledgeCount(knowledgeCount);
            } else {
                repoDto.setCharCount(0L);
                repoDto.setKnowledgeCount(0L);
            }
        }
    }

    /** Simple stream-based pagination */
    private List<RepoDto> paginate(List<RepoDto> all, Integer pageNo, Integer pageSize) {
        if (CollectionUtils.isEmpty(all))
            return Collections.emptyList();
        int p = (pageNo == null || pageNo < 1) ? 1 : pageNo;
        int sz = (pageSize == null || pageSize < 1) ? 10 : pageSize;
        return all.stream().skip((long) (p - 1) * sz).limit(sz).collect(Collectors.toList());
    }

    /**
     * Get detailed repository information including file counts, character counts, and knowledge
     * counts. Handles both local repositories and Spark platform repositories based on tag.
     *
     * @param id repository ID
     * @param tag repository tag to determine data source
     * @param request HTTP servlet request for cookie-based authentication (for Spark repositories)
     * @return detailed repository information with statistics
     * @throws BusinessException if repository does not exist or user has no permission
     */
    public RepoDto getDetail(Long id, String tag, HttpServletRequest request) {
        RepoDto repoDto = new RepoDto();
        long fileCount = 0;
        long charCount = 0;
        long knowledgeCount = 0;
        if (ProjectContent.isSparkRagCompatible(tag)) {
            List<RelatedDocDto> sparkCbgResponse = new ArrayList<RelatedDocDto>();
            String url = apiUrl.getDatasetFileUrl().concat("?datasetId=").concat(id.toString());
            log.info("sparkDeskRepoFileGet request url:{}", url);
            Map<String, String> header = new HashMap<>();
            String authorization = request.getHeader("Authorization");
            if (StringUtils.isNotBlank(authorization)) {
                header.put("Authorization", authorization);
            }
            String resp = OkHttpUtil.get(url, header);
            JSONObject respObject = JSON.parseObject(resp);
            log.info("sparkDeskRepoFileGet response data:{}", resp);

            if (respObject.getBooleanValue("flag") && respObject.getInteger("code") == 0) {
                sparkCbgResponse = JSON.parseArray(respObject.getString("data"), RelatedDocDto.class);
            }

            JSONArray xh_result = getStarFireData(request);
            if (xh_result != null) {
                for (int i = 0; i < xh_result.size(); i++) {
                    JSONObject itemB = xh_result.getJSONObject(i);
                    if (id.equals(itemB.getLong("id"))) {
                        repoDto.setName(itemB.getString("name"));
                    }
                }
            }
            repoDto.setBots(new ArrayList<>());

            if (!CollectionUtils.isEmpty(sparkCbgResponse)) {
                fileCount = sparkCbgResponse.size();
                for (RelatedDocDto relatedDocDto : sparkCbgResponse) {
                    charCount += relatedDocDto.getCharCount();
                    knowledgeCount += relatedDocDto.getParaCount();
                }
            }
        } else {
            Repo repo = this.getById(id);
            if (repo == null) {
                throw new BusinessException(ResponseEnum.REPO_NOT_EXIST);
            }
            dataPermissionCheckTool.checkRepoBelong(repo);
            dataPermissionCheckTool.checkRepoVisible(repo);

            BeanUtils.copyProperties(repo, repoDto);
            String address = s3UtilClient.getS3Prefix();
            repoDto.setAddress(address);
            List<SparkBotVO> sparkBotVOList = sparkBotMapper.listSparkBotByRepoId(id, UserInfoManagerHandler.getUserId());

            if (!CollectionUtils.isEmpty(sparkBotVOList)) {
                sparkBotVOList.forEach(e -> e.setAddress(address));
            }


            List<FileInfoV2> fileInfos = fileInfoV2Mapper.getFileInfoV2ByRepoId(repo.getId());
            fileCount = (long) fileInfos.size();
            for (FileInfoV2 fileInfoV2 : fileInfos) {
                charCount += fileInfoV2.getCharCount();
                // knowledgeCount += mongoTemplate.count(new
                // Query(Criteria.where("fileId").in(fileInfoV2.getUuid())), Knowledge.class);
                knowledgeCount += knowledgeMapper.countByFileId(fileInfoV2.getUuid());
            }
            repoDto.setBots(sparkBotVOList);
        }

        repoDto.setCharCount(charCount);
        repoDto.setKnowledgeCount(knowledgeCount);
        repoDto.setFileCount(fileCount);
        repoDto.setTag(tag);

        return repoDto;
    }


    /**
     * Perform knowledge retrieval test on a repository with given query. Tests the repository's
     * knowledge base search capabilities and records hit history.
     *
     * @param id repository ID to test
     * @param query search query string
     * @param topN maximum number of results to return
     * @param isBelongLoginUser whether to check if repository belongs to current user
     * @return list of matching knowledge chunks with file information
     * @throws BusinessException if repository does not exist, user has no permission, or no enabled
     *         files found
     */
    @Transactional
    public Object hitTest(Long id, String query, Integer topN, boolean isBelongLoginUser) {
        Repo repo = this.getById(id);
        if (repo == null) {
            throw new BusinessException(ResponseEnum.REPO_NOT_EXIST);
        }
        if (isBelongLoginUser) {
            dataPermissionCheckTool.checkRepoBelong(repo);
        }

        List<FileDirectoryTree> fileDirectoryTrees = directoryTreeService.list(Wrappers.lambdaQuery(FileDirectoryTree.class).eq(FileDirectoryTree::getAppId, repo.getId()).eq(FileDirectoryTree::getIsFile, 1));
        if (CollectionUtils.isEmpty(fileDirectoryTrees)) {
            return new JSONArray();
        }
        boolean hasEnabledFile = false;
        for (FileDirectoryTree fileDirectoryTree : fileDirectoryTrees) {
            FileInfoV2 fileInfoV2 = fileInfoV2Service.getById(fileDirectoryTree.getFileId());
            if (fileInfoV2 != null && fileInfoV2.getEnabled() == 1) {
                hasEnabledFile = true;
                break;
            }
        }
        if (!hasEnabledFile) {
            throw new BusinessException(ResponseEnum.REPO_FILE_DISABLED);
        }

        QueryRequest request = this.getKnowledgeQueryObject(repo, topN, query);

        KnowledgeResponse resp = knowledgeV2ServiceCallHandler.knowledgeQuery(request);
        if (resp.getCode() != 0) {
            log.error("Knowledge retrieval failed, message:{}", resp.getMessage());
            throw new BusinessException(ResponseEnum.REPO_KNOWLEDGE_QUERY_FAILED);
        }

        HitTestHistory hitTestHistory = new HitTestHistory();
        hitTestHistory.setRepoId(id);
        hitTestHistory.setUserId(UserInfoManagerHandler.getUserId());
        hitTestHistory.setQuery(query);
        hitTestHistory.setCreateTime(new Timestamp(System.currentTimeMillis()));
        historyService.save(hitTestHistory);

        QueryRespData data = JSON.parseObject(resp.getData().toString(), QueryRespData.class);
        List<ChunkInfo> results = data.getResults();
        Map<Long, FileDirectoryTree> processedFileIds = new HashMap<>();
        if (!CollectionUtils.isEmpty(results)) {
            for (ChunkInfo info : results) {
                String docId = info.getDocId();
                FileInfoV2 fileInfoV2 = fileInfoV2Service.getOnly(new QueryWrapper<FileInfoV2>().eq("uuid", docId));
                // Skip if this fileId has already been processed
                if (!processedFileIds.containsKey(fileInfoV2.getId())) {
                    FileDirectoryTree fileDirectoryTree = directoryTreeService.getOnly(Wrappers.lambdaQuery(FileDirectoryTree.class)
                            .eq(FileDirectoryTree::getAppId, repo.getId())
                            .eq(FileDirectoryTree::getFileId, fileInfoV2.getId()));
                    fileDirectoryTree.setHitCount(fileDirectoryTree.getHitCount() + 1);
                    directoryTreeService.updateById(fileDirectoryTree);
                    processedFileIds.put(fileInfoV2.getId(), fileDirectoryTree);
                }
                if (ProjectContent.isCbgRagCompatible(repo.getTag())) {
                    JSONObject references = info.getReferences();
                    if (!CollectionUtils.isEmpty(references)) {
                        Set<String> referenceUnusedSet = references.keySet();

                        JSONObject newReference = new JSONObject();
                        for (String referenceUnused : referenceUnusedSet) {
                            String link = references.getString(referenceUnused);
                            JSONObject newReferenceV = new JSONObject();
                            newReferenceV.put("format", "image");
                            newReferenceV.put("link", link);
                            newReferenceV.put("suffix", "png");
                            newReferenceV.put("content", "");

                            // Replace original value with new nested object
                            newReference.put(referenceUnused, newReferenceV);
                        }
                        info.setReferences(newReference);

                    }
                } else if (ProjectContent.isAiuiRagCompatible(repo.getTag())) {
                    String s3Url = s3UtilClient.getS3Url(fileInfoV2.getAddress());
                    fileInfoV2.setDownloadUrl(s3Url);
                }
                info.setFileInfo(fileInfoV2);
            }
        }
        return results;
    }


    /**
     * Get paginated hit test history for a repository. Returns history of knowledge retrieval tests
     * performed by the current user.
     *
     * @param repoId repository ID to get history for
     * @param pageNo page number (starting from 1)
     * @param pageSize number of items per page
     * @return paginated hit test history data
     */
    public PageData<HitTestHistory> listHitTestHistoryByPage(Long repoId, Integer pageNo, Integer pageSize) {
        LambdaQueryWrapper<HitTestHistory> hitTestHistoryQueryWrapper = new LambdaQueryWrapper<>();
        hitTestHistoryQueryWrapper.eq(HitTestHistory::getRepoId, repoId);
        hitTestHistoryQueryWrapper.eq(HitTestHistory::getUserId, UserInfoManagerHandler.getUserId());
        hitTestHistoryQueryWrapper.orderByDesc(HitTestHistory::getCreateTime);
        long modelListCount = historyService.count(hitTestHistoryQueryWrapper);
        hitTestHistoryQueryWrapper.last("start " + (pageNo - 1) * 10);
        hitTestHistoryQueryWrapper.last("limit " + pageSize);
        List<HitTestHistory> historyList = historyService.list(hitTestHistoryQueryWrapper);

        PageData<HitTestHistory> pageData = new PageData<>();
        pageData.setPageData(historyList);
        pageData.setTotalCount(modelListCount);
        return pageData;
    }


    /**
     * Enable or disable a repository by changing its status. Validates repository existence, ownership,
     * and status transition validity.
     *
     * @param id repository ID to enable/disable
     * @param enabled 1 to enable, 0 to disable
     * @throws BusinessException if repository does not exist, user has no permission, or status
     *         transition is invalid
     */
    @Transactional
    public void enableRepo(Long id, Integer enabled) {
        Repo repo = this.getById(id);
        if (repo == null) {
            throw new BusinessException(ResponseEnum.REPO_NOT_EXIST);
        }
        dataPermissionCheckTool.checkRepoBelong(repo);
        RepoVO repoVO = new RepoVO();
        repoVO.setId(id);
        if ((Objects.equals(repo.getStatus(), ProjectContent.REPO_STATUS_CREATED)
                || Objects.equals(repo.getStatus(), ProjectContent.REPO_STATUS_PUBLISHED)) && enabled == 0) {
            repoVO.setOperType(ProjectContent.REPO_STATUS_UNPUBLISHED);
        } else if (Objects.equals(repo.getStatus(), ProjectContent.REPO_STATUS_UNPUBLISHED) && enabled == 1) {
            repoVO.setOperType(ProjectContent.REPO_STATUS_PUBLISHED);
        } else {
            throw new BusinessException(ResponseEnum.REPO_STATUS_ILLEGAL);
        }
        this.updateRepoStatus(repoVO);
    }


    public JSONObject deleteXinghuoDataset(HttpServletRequest request, String id) {
        Map<String, String> params = new HashMap<>();
        params.put("datasetId", id);

        Map<String, String> headers = new HashMap<>();
        String authorization = request.getHeader("Authorization");
        if (StringUtils.isNotBlank(authorization)) {
            headers.put("Authorization", authorization);
        }
        String response = OkHttpUtil.post(apiUrl.getDeleteXinghuoDatasetUrl(), params, headers, null);
        return JSON.parseObject(response);
    }


    /**
     * Delete a repository based on its tag type. Handles both local repositories and Spark platform
     * repositories.
     *
     * @param id repository ID to delete
     * @param tag repository tag to determine deletion method
     * @param request HTTP servlet request for cookie-based authentication (for Spark repositories)
     * @return deletion result
     * @throws BusinessException if repository does not exist, user has no permission, or repository is
     *         in use by bots
     */
    @Transactional
    public Object deleteRepo(Long id, String tag, HttpServletRequest request) {
        // Check if tag equals Spark tag
        if (ProjectContent.isSparkRagCompatible(tag)) {
            log.info("Using Spark deletion logic");
            return deleteXinghuoDataset(request, id.toString());
        }
        Repo repo = this.getById(id);
        if (repo == null) {
            throw new BusinessException(ResponseEnum.REPO_NOT_EXIST);
        }

        dataPermissionCheckTool.checkRepoBelong(repo);

        long modelListCount = botRepoRelService.count(Wrappers.lambdaQuery(BotRepoRel.class).eq(BotRepoRel::getRepoId, repo.getCoreRepoId()));
        if (modelListCount > 0) {
            throw new BusinessException(ResponseEnum.REPO_DELETE_FAILED_BOT_USED);
        }

        repo.setDeleted(true);
        this.updateById(repo);

        // Metering rollback
        List<FileInfoV2> fileInfos = fileInfoV2Mapper.getFileInfoV2ByRepoId(repo.getId());
        for (FileInfoV2 fileInfoV2 : fileInfos) {
            fileInfoV2Service.fileCostRollback(fileInfoV2.getUuid());
        }

        RepoVO repoVO = new RepoVO();
        repoVO.setId(id);
        repoVO.setOperType(ProjectContent.REPO_STATUS_DELETE);
        return this.updateRepoStatus(repoVO);
    }

    private JSONObject getRepoRequestObject() {
        String str = "{\"header\":{\"businessId\":\"\"},\"parameter\":{\"type\":\"\",\"repoId\":\"\"}}";
        return JSONObject.parseObject(str);
    }

    private JSONObject getAppIdSubscribeRepoObject() {
        String str = "{\"header\":{\"businessId\":\"\",\"appId\":\"\"},\"parameter\":{\"repoList\":\"\"}}";
        return JSONObject.parseObject(str);
    }

    // private JSONObject getKnowledgeQueryObject(String group, Integer topN, String query) {
    // JSONObject jsonObject = new JSONObject();
    // jsonObject.put("query", query);
    // jsonObject.put("topN", topN);
    // List<String> repoNameList = apiUrl.getRepoNameList();
    // JSONArray repoSources = new JSONArray();
    // jsonObject.put("repoSources", repoSources);
    // for (String repoName : repoNameList) {
    // JSONObject repoSource = new JSONObject();
    // repoSource.put("repoId", repoName);
    // repoSource.put("threshold", 0);
    // repoSources.add(repoSource);
    // }
    // JSONObject match = new JSONObject();
    // JSONArray groups = new JSONArray();
    // groups.add(group);
    // match.put("groups", groups);
    // jsonObject.put("match", match);
    // return jsonObject;
    // }

    private QueryRequest getKnowledgeQueryObject(Repo repo, Integer topN, String query) {
        QueryRequest request = new QueryRequest();
        request.setQuery(query);
        request.setTopN(topN);

        String coreRepoId = repo.getCoreRepoId();
        QueryMatchObj matchObj = new QueryMatchObj();
        matchObj.setRepoId(Collections.singletonList(coreRepoId));

        List<String> docIds = new ArrayList<>();
        List<FileInfoV2> fileInfos = fileInfoV2Mapper.getFileInfoV2ByRepoId(repo.getId());
        String source = "";
        if (!fileInfos.isEmpty()) {
            source = fileInfos.get(0).getSource();
            if (ProjectContent.isCbgRagCompatible(source)) {
                for (FileInfoV2 fileInfoV2 : fileInfos) {
                    if (5 == fileInfoV2.getStatus() && 1 == fileInfoV2.getEnabled()) {
                        docIds.add(fileInfoV2.getUuid());
                    }
                }
                matchObj.setDocIds(docIds);
            }
        }

        request.setMatch(matchObj);
        request.setRagType(source);

        return request;
    }

    /**
     * Get list of files in a repository. Validates user permission before returning file list.
     *
     * @param id repository ID to get files for
     * @return list of files in the repository
     * @throws BusinessException if user has no permission to access the repository
     */
    public Object listFiles(Long id) {
        Repo repo = repoMapper.selectById(id);
        dataPermissionCheckTool.checkRepoBelong(repo);
        return fileInfoV2Mapper.listFiles(id);
        // return
        // fileInfoV2Mapper.selectList(Wrappers.lambdaQuery(FileInfoV2.class).eq(FileInfoV2::getRepoId, id)
        // .eq(FileInfoV2::getStatus, 5));
    }

    /**
     * Get repository usage status by checking if it's being used by any bots or workflows. Returns true
     * if repository is in use, false otherwise.
     *
     * @param repoId repository ID to check usage for
     * @param request HTTP servlet request (currently unused)
     * @return true if repository is in use by bots or workflows, false otherwise
     */
    public Object getRepoUseStatus(Long repoId, HttpServletRequest request) {
        Repo repo = getById(repoId);
        // Get agent list
        List<SparkBotVO> sparkBotVOList = sparkBotMapper.listSparkBotByRepoId(repoId, UserInfoManagerHandler.getUserId());

        // Get workflow list that associates with knowledge base
        List<FlowRepoRel> flowBotRelVOList = flowRepoRelMapper.selectList(
                new LambdaQueryWrapper<FlowRepoRel>()
                        .eq(FlowRepoRel::getRepoId, repo.getCoreRepoId()));
        if (!CollectionUtils.isEmpty(flowBotRelVOList)) {
            for (FlowRepoRel flowRepoRel : flowBotRelVOList) {
                SparkBotVO sparkBotVO = new SparkBotVO();
                sparkBotVO.setUuid(flowRepoRel.getFlowId());
                sparkBotVOList.add(sparkBotVO);
            }
        }

        List<DatasetStats> sparkBots = datasetFileService.getMaasDataset(repoId);
        for (DatasetStats sparkBot : sparkBots) {
            SparkBotVO sparkBotVO = new SparkBotVO();
            sparkBotVO.setName(sparkBot.getName());
            sparkBotVO.setUuid(sparkBot.getBotId());
            sparkBotVOList.add(sparkBotVO);
        }
        return !sparkBotVOList.isEmpty();
    }
}
