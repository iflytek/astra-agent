package com.iflytek.astra.console.toolkit.service.repo;

import cn.hutool.core.thread.ThreadFactoryBuilder;
import com.alibaba.fastjson2.*;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.iflytek.astra.console.commons.constant.ResponseEnum;
import com.iflytek.astra.console.commons.exception.BusinessException;
import com.iflytek.astra.console.toolkit.common.constant.ProjectContent;
import com.iflytek.astra.console.toolkit.config.properties.ApiUrl;
import com.iflytek.astra.console.toolkit.entity.core.knowledge.*;
import com.iflytek.astra.console.toolkit.entity.mongo.Knowledge;
import com.iflytek.astra.console.toolkit.entity.mongo.PreviewKnowledge;
import com.iflytek.astra.console.toolkit.entity.table.knowledge.MysqlKnowledge;
import com.iflytek.astra.console.toolkit.entity.table.knowledge.MysqlPreviewKnowledge;
import java.util.stream.Collectors;
import com.iflytek.astra.console.toolkit.mapper.knowledge.KnowledgeMapper;
import com.iflytek.astra.console.toolkit.mapper.knowledge.PreviewKnowledgeMapper;
import com.iflytek.astra.console.toolkit.entity.pojo.SliceConfig;
import com.iflytek.astra.console.toolkit.entity.table.repo.*;
import com.iflytek.astra.console.toolkit.entity.vo.repo.KnowledgeVO;
import com.iflytek.astra.console.toolkit.handler.KnowledgeV2ServiceCallHandler;
import com.iflytek.astra.console.toolkit.mapper.repo.FileInfoV2Mapper;
import com.iflytek.astra.console.toolkit.service.task.ExtractKnowledgeTaskService;
import com.iflytek.astra.console.toolkit.tool.DataPermissionCheckTool;
import com.iflytek.astra.console.toolkit.util.S3Util;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Retryable;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.CollectionUtils;
import org.springframework.web.client.RestTemplate;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Service
@Slf4j
public class KnowledgeService {
    @Resource
    private KnowledgeV2ServiceCallHandler knowledgeV2ServiceCallHandler;
    @Resource
    @Lazy
    private FileInfoV2Service fileInfoV2Service;
    @Resource
    private FileInfoV2Mapper fileInfoV2Mapper;
    @Resource
    private RepoService repoService;
    @Resource
    @Lazy
    private ExtractKnowledgeTaskService extractKnowledgeTaskService;
    @Resource
    private ApiUrl apiUrl;
    @Resource
    private S3Util s3Util;
    // @Resource
    // private AuditService auditService;
    @Autowired
    private DataPermissionCheckTool dataPermissionCheckTool;
    @Resource
    private KnowledgeMapper knowledgeMapper;
    @Resource
    private PreviewKnowledgeMapper previewKnowledgeMapper;


    /**
     * Create knowledge entry
     *
     * @param knowledgeVO knowledge value object
     * @return created Knowledge object
     * @throws BusinessException if validation fails or knowledge creation fails
     */
    @Transactional
    public Knowledge createKnowledge(KnowledgeVO knowledgeVO) {
        List<String> uuids = preCheck(knowledgeVO.getFileId());
        Repo repo = repoService.getOnly(Wrappers.lambdaQuery(Repo.class).eq(Repo::getCoreRepoId, uuids.get(1)));
        dataPermissionCheckTool.checkRepoBelong(repo);
        // Create knowledge
        Knowledge knowledge = this.getKnowledgePojo(knowledgeVO, uuids.getFirst());

        String auditSuggest = null;
        // Query current document enabled status
        FileInfoV2 fileInfoById = fileInfoV2Service.getById(knowledgeVO.getFileId());
        knowledge.setEnabled(fileInfoById.getEnabled());
        if (repo.getEnableAudit()) {
            JSONObject content = knowledge.getContent();
            String knowledgeStr = content.getString("content");
            // Future<SyncAuditResult<TextDetail>> syncAuditResultFuture =
            // auditService.syncAuditText(knowledgeStr, content);
            // syncAuditResultFuture.get();
            auditSuggest = content.getString("auditSuggest");
            if (!(StringUtils.isEmpty(auditSuggest) || "pass".equals(auditSuggest))) {
                knowledge.setEnabled(0);
            }
        }

        try {
            // 1. Add tags
            FileInfoV2 fileInfoV2 = fileInfoV2Mapper.selectOne(Wrappers.lambdaQuery(FileInfoV2.class)
                    .eq(FileInfoV2::getUuid, knowledge.getFileId())
                    .eq(FileInfoV2::getRepoId, repo.getId()));
            // 2. Submit to knowledge base
            JSONArray jsonArray = new JSONArray();
            if (!repo.getEnableAudit() || StringUtils.isEmpty(auditSuggest) || "pass".equals(auditSuggest)) {
                jsonArray.add(this.convertKnowledge2Object(knowledge, knowledge.getFileId()));
            }

            // 3. Determine type, override chunk_id
            String source = fileInfoV2.getSource();
            if (ProjectContent.isAiuiRagCompatible(source)) {
                this.addKnowledge4AIUI(uuids.get(0), uuids.get(1), jsonArray, source);
            } else if (ProjectContent.isCbgRagCompatible(source)) {
                Map<String, String> cbgKnowledgeMap = this.addKnowledge4CBG(uuids.get(0), uuids.get(1), jsonArray, source);
                if (!cbgKnowledgeMap.isEmpty()) {
                    for (String key : cbgKnowledgeMap.keySet()) {
                        knowledge.setId(cbgKnowledgeMap.get(key));
                        break;
                    }
                }
            }

            // 4. Add knowledge point - using MySQL
            MysqlKnowledge mysqlKnowledge = new MysqlKnowledge();
            BeanUtils.copyProperties(knowledge, mysqlKnowledge);
            knowledgeMapper.insert(mysqlKnowledge);
            knowledge.setId(mysqlKnowledge.getId());

        } catch (Exception e) {
            log.error("Failed to save knowledge point", e);
            throw e;
        }
        return knowledge;
    }


    /**
     * Update knowledge entry
     *
     * @param knowledgeVO knowledge value object
     * @return updated Knowledge object
     * @throws BusinessException if knowledge not found or update fails
     */
    @Transactional
    public Knowledge updateKnowledge(KnowledgeVO knowledgeVO) {
        MysqlKnowledge mysqlKnowledge = knowledgeMapper.selectById(knowledgeVO.getId());
        if (mysqlKnowledge == null) {
            throw new BusinessException(ResponseEnum.REPO_KNOWLEDGE_NOT_EXIST);
        }
        Knowledge knowledge = new Knowledge();
        BeanUtils.copyProperties(mysqlKnowledge, knowledge);
        List<String> uuids = preCheck(knowledgeVO.getFileId());

        String originKnowledge = knowledge.getContent().getString("content");
        boolean notNeedUpdate = originKnowledge.equals(knowledgeVO.getContent());
        if (notNeedUpdate) {
            return knowledge;
        }

        knowledge.getContent().put("content", knowledgeVO.getContent());
        knowledge.setUpdatedAt(LocalDateTime.now());
        Repo repo = repoService.getOnly(Wrappers.lambdaQuery(Repo.class).eq(Repo::getCoreRepoId, uuids.get(1)));
        dataPermissionCheckTool.checkRepoBelong(repo);

        String auditSuggest = null;
        if (repo.getEnableAudit()) {
            JSONObject content = knowledge.getContent();
            // String knowledgeStr = content.getString("content");
            // Future<SyncAuditResult<TextDetail>> syncAuditResultFuture =
            // auditService.syncAuditText(knowledgeStr, content);
            // syncAuditResultFuture.get();
            auditSuggest = content.getString("auditSuggest");
            if (!(StringUtils.isEmpty(auditSuggest) || "pass".equals(auditSuggest))) {
                knowledge.setEnabled(0);
            }
        }

        try {
            // 3. Modify knowledge point
            JSONArray updateKnowledgeArray = new JSONArray();
            if (!repo.getEnableAudit() || StringUtils.isEmpty(auditSuggest) || "pass".equals(auditSuggest)) {
                // Query current document enabled status
                FileInfoV2 fileInfoById = fileInfoV2Service.getById(knowledgeVO.getFileId());
                knowledge.setEnabled(fileInfoById.getEnabled());
                updateKnowledgeArray.add(this.convertKnowledge2Object(knowledge, knowledge.getFileId()));
            }
            // 1. Modify knowledge point - using MySQL
            BeanUtils.copyProperties(knowledge, mysqlKnowledge);
            knowledgeMapper.updateById(mysqlKnowledge);
            this.updateKnowledge(uuids.get(0), uuids.get(1), updateKnowledgeArray);
        } catch (Exception e) {
            log.error("Failed to modify knowledge point", e);
            throw e;
        }
        return knowledge;
    }


    /**
     * Enable or disable knowledge entry
     *
     * @param id knowledge ID
     * @param enabled enabled status (1=enabled, 0=disabled)
     * @return knowledge ID
     * @throws BusinessException if knowledge not found or operation fails
     */
    public String enableKnowledge(String id, Integer enabled) {
        MysqlKnowledge mysqlKnowledge = knowledgeMapper.selectById(id);
        if (mysqlKnowledge == null) {
            throw new BusinessException(ResponseEnum.REPO_KNOWLEDGE_NOT_EXIST);
        }
        Knowledge knowledge = new Knowledge();
        BeanUtils.copyProperties(mysqlKnowledge, knowledge);

        Integer originEnabled = knowledge.getEnabled();
        if (Objects.equals(originEnabled, enabled)) {
            return knowledge.getId();
        }

        FileInfoV2 fileInfoV2 = fileInfoV2Service.getOnly(new QueryWrapper<FileInfoV2>().eq("uuid", knowledge.getFileId()));
        if (fileInfoV2 == null) {
            throw new BusinessException(ResponseEnum.REPO_FILE_NOT_EXIST);
        }

        List<String> uuids = this.preCheck(fileInfoV2.getId());

        JSONObject content = knowledge.getContent();
        String auditSuggest = content.getString("auditSuggest");
        if (!(StringUtils.isEmpty(auditSuggest) || "pass".equals(auditSuggest)) && enabled == 1) {
            return knowledge.getId();
        }

        knowledge.setEnabled(enabled);
        knowledge.setUpdatedAt(LocalDateTime.now());

        try {
            String source = fileInfoV2.getSource();
            if (enabled == 1) {// Enable - re-add
                JSONArray jsonArray = new JSONArray();

                jsonArray.add(this.convertKnowledge2Object(knowledge, knowledge.getFileId()));
                if (ProjectContent.isAiuiRagCompatible(source)) {
                    this.addKnowledge4AIUI(uuids.get(0), uuids.get(1), jsonArray, source);
                } else if (ProjectContent.isCbgRagCompatible(source)) {
                    // Delete first then add
                    knowledgeMapper.deleteById(id);
                    Map<String, String> cbgKnowledgeMap = this.addKnowledge4CBG(uuids.get(0), uuids.get(1), jsonArray, source);
                    if (!cbgKnowledgeMap.isEmpty()) {
                        for (String key : cbgKnowledgeMap.keySet()) {
                            knowledge.setId(cbgKnowledgeMap.get(key));
                            break;
                        }
                    }
                }
            } else {// Disable - corresponding logic is to delete knowledge
                JSONArray delKbList = new JSONArray();
                delKbList.add(knowledge.getId());
                this.deleteKnowledgeChunks(uuids.getFirst(), delKbList);
            }
            // Save using MySQL
            BeanUtils.copyProperties(knowledge, mysqlKnowledge);
            knowledgeMapper.updateById(mysqlKnowledge);
            return knowledge.getId();
        } catch (Exception e) {
            log.error("Failed to enable/disable knowledge point", e);
            throw e;
        }

    }


    /**
     * Enable or disable document
     *
     * @param id file ID
     * @param enabled enabled status (1=enabled, 0=disabled)
     * @throws BusinessException if file not found or operation fails
     */
    public void enableDoc(Long id, Integer enabled) {
        List<String> uuids = this.preCheck(id);
        // ClientSession session = mongoClient.startSession();
        // try {
        // session.startTransaction();
        FileInfoV2 fileInfoV2 = fileInfoV2Service.getOnly(new QueryWrapper<FileInfoV2>().eq("uuid", uuids.get(0)));
        if (fileInfoV2 == null) {
            throw new BusinessException(ResponseEnum.REPO_FILE_NOT_EXIST);
        }
        if (enabled == 1) {// Enable - re-add
            // 1. Query knowledge points that were previously enabled for the associated document
            // Criteria newCriteria = Criteria.where("fileId").is(uuids.getFirst()).and("enabled").is(0);
            // List<Knowledge> knowledges = mongoTemplate.find(new Query(newCriteria), Knowledge.class);

            // Use MySQL query to replace MongoDB query
            List<MysqlKnowledge> mysqlKnowledges = knowledgeMapper.findByFileIdAndEnabled(uuids.getFirst(), 0);
            List<Knowledge> knowledges = new ArrayList<>();
            for (MysqlKnowledge mysql : mysqlKnowledges) {
                Knowledge knowledge = new Knowledge();
                BeanUtils.copyProperties(mysql, knowledge);
                knowledges.add(knowledge);
            }
            // 2. Convert knowledge points to the structure required by the knowledge base
            JSONArray waitAddKnowledge = this.getWaitAddKnowledge(knowledges, uuids.get(0));
            // 3. Add new (CBG knowledge base does not delete knowledge base slice information when
            // enabling/disabling doc)
            String source = fileInfoV2.getSource();
            if (ProjectContent.isAiuiRagCompatible(source)) {
                List<String> failedKnowledge = this.addKnowledge4AIUI(uuids.get(0), uuids.get(1), waitAddKnowledge, source);
                // 4. Check if there are failed knowledge points
                if (!CollectionUtils.isEmpty(failedKnowledge)) {
                    List<Knowledge> updateKnowledgeList = new ArrayList<>();
                    for (Knowledge knowledge : knowledges) {
                        if (failedKnowledge.contains(knowledge.getId())) {
                            knowledge.setEnabled(0);
                            knowledge.setUpdatedAt(LocalDateTime.now());
                            updateKnowledgeList.add(knowledge);
                        }
                    }
                    if (!CollectionUtils.isEmpty(updateKnowledgeList)) {
                        // knowledgeRepository.saveAll(updateKnowledgeList);
                        // Use MySQL update
                        for (Knowledge knowledge : updateKnowledgeList) {
                            MysqlKnowledge mysqlKnowledge = new MysqlKnowledge();
                            BeanUtils.copyProperties(knowledge, mysqlKnowledge);
                            knowledgeMapper.updateById(mysqlKnowledge);
                        }
                    }
                }
            }

            // Update knowledge
            // Query query = new Query();
            // query.addCriteria(newCriteria);
            // Update update = new Update();
            // update.set("enabled", 1);
            // mongoTemplate.updateMulti(query, update, Knowledge.class);

            // Use MySQL update
            knowledgeMapper.updateEnabledByFileIdAndOldEnabled(uuids.getFirst(), 0, 1);
        } else {// Disable - corresponding logic is to delete document
            JSONArray delDocList = new JSONArray();
            delDocList.add(uuids.getFirst());
            if (ProjectContent.isAiuiRagCompatible(fileInfoV2.getSource())) {
                this.deleteKnowledgeDoc(delDocList, null);
            }
            // Update knowledge
            // Criteria newCriteria = Criteria.where("fileId").is(uuids.getFirst()).and("enabled").is(1);
            // Query query = new Query();
            // query.addCriteria(newCriteria);
            // Update update = new Update();
            // update.set("enabled", 0);
            // mongoTemplate.updateMulti(query, update, Knowledge.class);

            // Use MySQL update
            knowledgeMapper.updateEnabledByFileIdAndOldEnabled(uuids.getFirst(), 1, 0);
        }
        // session.commitTransaction();
        // } catch (Exception e) {
        // // Rollback transaction
        // session.abortTransaction();
        // log.error("Document enable/disable failed", e);
        // throw e;

        // } finally {
        // session.close();
        // }
    }


    /**
     * Delete knowledge entry
     *
     * @param id knowledge ID
     * @throws BusinessException if knowledge not found or file not found
     */
    public void deleteKnowledge(String id) {
        // Knowledge knowledge = knowledgeRepository.findById(id).orElse(null);
        MysqlKnowledge mysqlKnowledge = knowledgeMapper.selectById(id);
        if (mysqlKnowledge == null) {
            throw new BusinessException(ResponseEnum.REPO_KNOWLEDGE_NOT_EXIST);
        }

        // Convert to Knowledge object to maintain compatibility
        Knowledge knowledge = new Knowledge();
        BeanUtils.copyProperties(mysqlKnowledge, knowledge);

        FileInfoV2 fileInfoV2 = fileInfoV2Service.getOnly(new QueryWrapper<FileInfoV2>().eq("uuid", knowledge.getFileId()));
        if (fileInfoV2 == null) {
            throw new BusinessException(ResponseEnum.REPO_FILE_NOT_EXIST);
        }
        List<String> uuids = this.preCheck(fileInfoV2.getId());
        // ClientSession session = mongoClient.startSession();
        // try {
        // session.startTransaction();
        // knowledgeRepository.deleteById(id);
        knowledgeMapper.deleteById(id);
        JSONArray delKbList = new JSONArray();
        delKbList.add(knowledge.getId());
        this.deleteKnowledgeChunks(uuids.getFirst(), delKbList);
        // session.commitTransaction();
        // } catch (Exception e) {
        // // Rollback transaction
        // session.abortTransaction();
        // log.error("Failed to delete knowledge point", e);
        // throw e;

        // } finally {
        // session.close();
        // }
    }


    /**
     * Asynchronously extract knowledge from document content
     *
     * @param contentType the MIME type of the document content
     * @param url the URL of the document to be processed
     * @param sliceConfig configuration for document slicing/chunking
     * @param fileInfoV2 file information object containing metadata
     * @param extractKnowledgeTask task object to track extraction progress
     * @throws BusinessException if document processing fails or file doesn't meet requirements
     */
    @Async
    public void knowledgeExtractAsync(String contentType, String url, SliceConfig sliceConfig, FileInfoV2 fileInfoV2, ExtractKnowledgeTask extractKnowledgeTask) {
        // 1/2: Parse the user-provided text and perform chunking (completed in one interface)
        SplitRequest request = new SplitRequest();
        request.setFile(url.replaceAll("\\+", "%20"));
        request.setLengthRange(sliceConfig.getLengthRange());
        request.setCutOff(sliceConfig.getSeperator());
        if (ProjectContent.HTML_FILE_TYPE.equals(fileInfoV2.getType())) {
            request.setResourceType(1);
        }
        // Compatibility for old and new knowledge bases, handled by new CBG knowledge base
        String source = fileInfoV2.getSource();
        request.setRagType(source);
        if (ProjectContent.isCbgRagCompatible(source)) {
            // sliceFileVO.getSliceConfig().setSeperator(Collections.singletonList("\n"));
            List<String> sliceConf = sliceConfig.getSeperator();
            // request.setSeparator(Collections.singletonList("\n"));
            request.setSeparator(Collections.singletonList(sliceConf.get(0)));
        }
        KnowledgeResponse response = knowledgeV2ServiceCallHandler.documentSplit(request);
        if (response.getCode() != 0) {
            String errMsg = response.getMessage();
            log.error("Document chunking failed : {}", errMsg);
            // Temporary solution
            if (response.getCode() == 11111) {
                String regex = "[（(](.*?)[)）]";
                Pattern pattern = Pattern.compile(regex);
                Matcher matcher = pattern.matcher(errMsg);
                if (matcher.find()) {
                    errMsg = matcher.group(1);
                }
            }
            this.updateTaskAndFileStatus(fileInfoV2, extractKnowledgeTask, "Document chunking failed, " + errMsg, false);
            return;
            // throw new CustomException("Document chunking failed : { " + response.getMessage() + " }");
        }
        List<ChunkInfo> chunkInfos;
        try {
            chunkInfos = ((JSONArray) response.getData()).toJavaList(ChunkInfo.class);
        } catch (Exception e) {
            log.error("Failed to get document chunking result : {}", e.getMessage(), e);
            this.updateTaskAndFileStatus(fileInfoV2, extractKnowledgeTask, "Failed to get document chunking result:" + e.getMessage(), false);
            return;
        }

        if (chunkInfos.isEmpty()) {
            if (contentType.equals(ProjectContent.JPEG_FILE_TYPE) || contentType.equals(ProjectContent.JPG_FILE_TYPE) || contentType.equals(ProjectContent.PNG_FILE_TYPE) || contentType.equals(ProjectContent.BMP_FILE_TYPE)) {
                this.updateTaskAndFileStatus(fileInfoV2, extractKnowledgeTask, "Document cannot be chunked, please check if the image contains text", false);
            } else {
                this.updateTaskAndFileStatus(fileInfoV2, extractKnowledgeTask, "Document cannot be chunked, please check if the file meets upload requirements", false);
            }
            return;
        }

        // 3. Store data in database
        Repo repo = repoService.getById(fileInfoV2.getRepoId());
        if (repo.getEnableAudit()) {
            this.auditPreviewKnowledge(chunkInfos);
        }
        this.storagePreviewKnowledge(fileInfoV2.getUuid(), fileInfoV2.getId(), chunkInfos);

        int charCount = 0;
        for (ChunkInfo previewKnowledgeObject : chunkInfos) {
            String knowledgeStr = previewKnowledgeObject.getContent();
            if (!StringUtils.isEmpty(knowledgeStr)) {
                charCount += knowledgeStr.length();
            }
        }

        if (charCount > 0) {
            fileInfoV2.setCharCount((long) charCount);
        }

        // CBG needs to use chunk's fileId as fileId
        if (ProjectContent.isCbgRagCompatible(fileInfoV2.getSource())) {
            if (fileInfoV2.getLastUuid() == null) {
                fileInfoV2.setUuid(chunkInfos.get(0).getDocId());
            }
            fileInfoV2.setLastUuid(chunkInfos.get(0).getDocId());
        } else {
            fileInfoV2.setLastUuid(fileInfoV2.getUuid());
        }


        this.updateTaskAndFileStatus(fileInfoV2, extractKnowledgeTask, null, true);
    }

    /**
     * Asynchronously extract and embed knowledge from document content
     *
     * @param contentType the MIME type of the document content
     * @param url the URL of the document to be processed
     * @param sliceConfig configuration for document slicing/chunking
     * @param fileInfoV2 file information object containing metadata
     * @param extractKnowledgeTask task object to track extraction progress
     * @param fileInfoV2Service service for file information operations
     * @throws BusinessException if document processing, embedding, or file operations fail
     */
    @Async
    public void knowledgeEmbeddingExtractAsync(String contentType, String url, SliceConfig sliceConfig, FileInfoV2 fileInfoV2,
            ExtractKnowledgeTask extractKnowledgeTask, FileInfoV2Service fileInfoV2Service) {
        // 1/2: Parse the user-provided text and perform chunking (completed in one interface)
        SplitRequest request = new SplitRequest();
        request.setFile(url.replaceAll("\\+", "%20"));
        request.setLengthRange(sliceConfig.getLengthRange());
        request.setCutOff(sliceConfig.getSeperator());
        if (ProjectContent.HTML_FILE_TYPE.equals(fileInfoV2.getType())) {
            request.setResourceType(1);
        }
        // Compatibility for old and new knowledge bases, handled by new CBG knowledge base
        String source = fileInfoV2.getSource();
        request.setRagType(source);
        if (ProjectContent.isCbgRagCompatible(source)) {
            // sliceFileVO.getSliceConfig().setSeperator(Collections.singletonList("\n"));
            List<String> sliceConf = sliceConfig.getSeperator();
            // request.setSeparator(Collections.singletonList("\n"));
            request.setSeparator(Collections.singletonList(sliceConf.get(0)));
        }
        KnowledgeResponse response = knowledgeV2ServiceCallHandler.documentSplit(request);
        if (response.getCode() != 0) {
            String errMsg = response.getMessage();
            log.error("Document chunking failed : {}", errMsg);
            // Temporary solution
            if (response.getCode() == 11111) {
                String regex = "[（(](.*?)[)）]";
                Pattern pattern = Pattern.compile(regex);
                Matcher matcher = pattern.matcher(errMsg);
                if (matcher.find()) {
                    errMsg = matcher.group(1);
                }
            }
            this.updateTaskAndFileStatus(fileInfoV2, extractKnowledgeTask, "Document chunking failed, " + errMsg, false);
            return;
            // throw new CustomException("Document chunking failed : { " + response.getMessage() + " }");
        }
        List<ChunkInfo> chunkInfos;
        try {
            chunkInfos = ((JSONArray) response.getData()).toJavaList(ChunkInfo.class);
        } catch (Exception e) {
            log.error("Failed to get document chunking result : {}", e.getMessage(), e);
            this.updateTaskAndFileStatus(fileInfoV2, extractKnowledgeTask, "Failed to get document chunking result:" + e.getMessage(), false);
            return;
        }

        if (chunkInfos.isEmpty()) {
            if (contentType.equals(ProjectContent.JPEG_FILE_TYPE) || contentType.equals(ProjectContent.JPG_FILE_TYPE) || contentType.equals(ProjectContent.PNG_FILE_TYPE) || contentType.equals(ProjectContent.BMP_FILE_TYPE)) {
                this.updateTaskAndFileStatus(fileInfoV2, extractKnowledgeTask, "Document cannot be chunked, please check if the image contains text", false);
            } else {
                this.updateTaskAndFileStatus(fileInfoV2, extractKnowledgeTask, "Document cannot be chunked, please check if the file meets upload requirements", false);
            }
            return;
        }

        // 3. Store data in database
        Repo repo = repoService.getById(fileInfoV2.getRepoId());
        if (repo.getEnableAudit()) {
            this.auditPreviewKnowledge(chunkInfos);
        }
        this.storagePreviewKnowledge(fileInfoV2.getUuid(), fileInfoV2.getId(), chunkInfos);

        int charCount = 0;
        for (ChunkInfo previewKnowledgeObject : chunkInfos) {
            String knowledgeStr = previewKnowledgeObject.getContent();
            if (!StringUtils.isEmpty(knowledgeStr)) {
                charCount += knowledgeStr.length();
            }
        }

        if (charCount > 0) {
            fileInfoV2.setCharCount((long) charCount);
        }

        // CBG needs to use chunk's fileId as fileId
        // CBG needs to use chunk's fileId as fileId
        if (ProjectContent.isCbgRagCompatible(fileInfoV2.getSource())) {
            fileInfoV2.setLastUuid(chunkInfos.get(0).getDocId());
        } else {
            fileInfoV2.setLastUuid(fileInfoV2.getUuid());
        }

        this.updateTaskAndFileStatus(fileInfoV2, extractKnowledgeTask, null, true);
        fileInfoV2Service.saveTaskAndUpdateFileStatus(fileInfoV2.getId());
        fileInfoV2Service.embeddingFile(fileInfoV2.getId(), fileInfoV2.getSpaceId());
    }

    /**
     * Update the status of extraction task and file information
     *
     * @param fileInfoV2 file information object to be updated
     * @param extractKnowledgeTask extraction task object to be updated
     * @param errMsg error message if operation failed, null if successful
     * @param isSucess boolean flag indicating if the operation was successful
     */
    public void updateTaskAndFileStatus(FileInfoV2 fileInfoV2, ExtractKnowledgeTask extractKnowledgeTask, String errMsg, Boolean isSucess) {
        if (isSucess) {
            fileInfoV2.setStatus(ProjectContent.FILE_PARSE_SUCCESSED);
            fileInfoV2.setReason(null);
            extractKnowledgeTask.setStatus(1);
        } else {
            fileInfoV2.setStatus(ProjectContent.FILE_PARSE_FAILED);
            fileInfoV2.setReason(errMsg);
            extractKnowledgeTask.setStatus(2);
            extractKnowledgeTask.setReason(errMsg);
        }
        // Update parsing status
        extractKnowledgeTask.setTaskStatus(1);
        fileInfoV2.setUpdateTime(new Timestamp(System.currentTimeMillis()));
        fileInfoV2Service.updateById(fileInfoV2);

        extractKnowledgeTask.setUpdateTime(new Timestamp(System.currentTimeMillis()));
        extractKnowledgeTaskService.updateById(extractKnowledgeTask);
    }


    /**
     * Store preview knowledge chunks in the database
     *
     * @param fileId the unique identifier of the file
     * @param id the database ID of the file
     * @param chunkInfos list of chunk information objects containing knowledge data
     * @throws BusinessException if file not found or storage operation fails
     */
    public void storagePreviewKnowledge(String fileId, Long id, List<ChunkInfo> chunkInfos) {
        if (CollectionUtils.isEmpty(chunkInfos)) {
            return;
        }
        FileInfoV2 fileInfoV2 = fileInfoV2Service.getById(id);
        if (fileInfoV2 == null) {
            throw new BusinessException(ResponseEnum.REPO_FILE_NOT_EXIST);
        }
        // CBG's docId must be generated by CBG, AIUI's is a random value, compatibility handling
        String source = fileInfoV2.getSource();
        if (ProjectContent.isCbgRagCompatible(source)) {
            String newFileId = chunkInfos.get(0).getDocId();
            if (!newFileId.isEmpty() && !newFileId.equals(fileId)) {
                fileId = newFileId;
            }
        }

        List<PreviewKnowledge> previewKnowledgeList = new ArrayList<>();
        for (ChunkInfo previewKnowledgeObject : chunkInfos) {
            String knowledgeStr = previewKnowledgeObject.getContent();
            int charCount = 0;
            if (!StringUtils.isEmpty(knowledgeStr)) {
                charCount = knowledgeStr.length();
            }
            Set<String> referenceUnusedSet = new HashSet<>();
            JSONObject reference = previewKnowledgeObject.getReferences();
            if (reference != null) {
                referenceUnusedSet = reference.keySet();
            }

            if (!CollectionUtils.isEmpty(referenceUnusedSet)) {
                for (String referenceUnused : referenceUnusedSet) { // Replace if it's a file
                    try {
                        String content = "";
                        String s3Key = "";
                        boolean isImage = false;
                        if (ProjectContent.isAiuiRagCompatible(source)) {
                            content = reference.getJSONObject(referenceUnused).getString("content");
                            String format = reference.getJSONObject(referenceUnused).getString("format");
                            s3Key = "repoRef/" + fileId + "/" + referenceUnused + ".jpg";
                            if ("image".equals(format)) {
                                isImage = true;
                            }
                        }

                        if (isImage) {
                            String base64 = content;
                            int comma = content.indexOf(',');
                            if (comma > 0) {
                                base64 = content.substring(comma + 1);
                            }
                            s3Util.putObjectBase64(s3Key, base64, "image/jpeg");
                            reference.getJSONObject(referenceUnused).put("content", "");
                            reference.getJSONObject(referenceUnused).put("link", s3Util.getS3Url(s3Key));
                        }
                    } catch (Exception e) {
                        log.error("File upload failed", e);
                    }
                }
                previewKnowledgeObject.setReferences(reference);
            }

            PreviewKnowledge previewKnowledge = PreviewKnowledge.builder()
                    .fileId(fileId)
                    .content(JSON.parseObject(JSON.toJSONString(previewKnowledgeObject)))
                    .charCount((long) charCount)
                    .createdAt(LocalDateTime.now())
                    .updatedAt(LocalDateTime.now())
                    .build();
            previewKnowledgeList.add(previewKnowledge);
        }

        // Check if already chunked, if so delete the previous ones
        long count = previewKnowledgeMapper.countByFileId(fileId);
        if (count > 0) {
            previewKnowledgeMapper.deleteByFileId(fileId);
        }
        // Save using MySQL
        List<MysqlPreviewKnowledge> mysqlPreviewList = new ArrayList<>();
        for (PreviewKnowledge preview : previewKnowledgeList) {
            MysqlPreviewKnowledge mysql = new MysqlPreviewKnowledge();
            BeanUtils.copyProperties(preview, mysql);
            mysqlPreviewList.add(mysql);
        }
        previewKnowledgeMapper.insertBatch(mysqlPreviewList);
    }

    /**
     * Embed knowledge chunks and store them in the knowledge base
     *
     * @param fileId the database ID of the file to be processed
     * @return the number of failed knowledge points during embedding
     * @throws BusinessException if file validation fails, knowledge processing fails, or embedding
     *         operations fail
     */
    public Integer embeddingKnowledgeAndStorage(Long fileId) {
        List<String> uuid = this.preCheck(fileId);

        // 1. Read preview chunks
        List<PreviewKnowledge> previewKnowledgeList = loadPreviewKnowledge(uuid.get(2));
        // 2. Delete old "auto-embedded" knowledge chunks
        List<Knowledge> oldAuto = findOldAutoKnowledge(uuid.get(0));
        JSONArray delKbList = collectEnabledKbIds(oldAuto);
        this.deleteKnowledgeChunks(uuid.get(0), delKbList);

        // 3. Assemble "to be written" knowledge and JSON to be pushed
        BuildResult build = buildNewKnowledges(previewKnowledgeList, uuid.get(0));

        // 4. Push in batches to external knowledge base (AIUI/CBG), and collect failure/ID mapping
        PushResult push = pushChunksBySource(fileId, uuid, build.addArray);

        // 5. Adjust knowledge point enabled/ID based on push results
        applyPushResult(build.knowledgeList, oldAuto, push, fileId);

        // 6. Local persistence (delete old auto-embedded, write new ones, restore "manually added"
        // knowledge)
        try {
            // Delete old auto-embedded
            if (!oldAuto.isEmpty()) {
                List<String> oldAutoIds = oldAuto.stream().map(Knowledge::getId).collect(Collectors.toList());
                knowledgeMapper.deleteBatchIds(oldAutoIds);
            }
            // Write new knowledge points
            if (!build.knowledgeList.isEmpty()) {
                List<MysqlKnowledge> mysqlKnowledgeList = build.knowledgeList.stream().map(knowledge -> {
                    MysqlKnowledge mysqlKnowledge = new MysqlKnowledge();
                    BeanUtils.copyProperties(knowledge, mysqlKnowledge);
                    return mysqlKnowledge;
                }).collect(Collectors.toList());
                for (MysqlKnowledge mysqlKnowledge : mysqlKnowledgeList) {
                    knowledgeMapper.insert(mysqlKnowledge);
                }
            }
            restoreManualKnowledge(uuid.get(0), uuid.get(2));
        } catch (Exception e) {
            log.error("Embedding failed", e);
            throw e;
        }

        return push.failedKnowledge.size();
    }


    private List<PreviewKnowledge> loadPreviewKnowledge(String lastUuid) {
        // Criteria criteria = Criteria.where("fileId").is(lastUuid);
        // List<PreviewKnowledge> list = mongoTemplate.find(new Query(criteria), PreviewKnowledge.class);

        // Use MySQL query to replace MongoDB query
        List<MysqlPreviewKnowledge> mysqlList = previewKnowledgeMapper.findByFileId(lastUuid);
        if (CollectionUtils.isEmpty(mysqlList)) {
            throw new BusinessException(ResponseEnum.REPO_KNOWLEDGE_GET_FAILED);
        }

        List<PreviewKnowledge> list = new ArrayList<>();
        for (MysqlPreviewKnowledge mysql : mysqlList) {
            PreviewKnowledge preview = new PreviewKnowledge();
            BeanUtils.copyProperties(mysql, preview);
            list.add(preview);
        }
        return list;
    }

    private List<Knowledge> findOldAutoKnowledge(String docUuid) {
        // Criteria c = Criteria.where("fileId").is(docUuid).and("source").is(0);
        // return mongoTemplate.find(new Query(c), Knowledge.class);

        // Use MySQL query to replace MongoDB query
        List<MysqlKnowledge> mysqlList = knowledgeMapper.findByFileIdAndSource(docUuid, 0);
        List<Knowledge> result = new ArrayList<>();
        for (MysqlKnowledge mysql : mysqlList) {
            Knowledge knowledge = new Knowledge();
            BeanUtils.copyProperties(mysql, knowledge);
            result.add(knowledge);
        }
        return result;
    }

    private JSONArray collectEnabledKbIds(List<Knowledge> oldKnowledgeList) {
        JSONArray delKbList = new JSONArray();
        if (!CollectionUtils.isEmpty(oldKnowledgeList)) {
            for (Knowledge k : oldKnowledgeList) {
                if (k.getEnabled() == 1) {
                    delKbList.add(k.getId());
                }
            }
        }
        return delKbList;
    }

    private static final class BuildResult {
        final List<Knowledge> knowledgeList = new ArrayList<>();
        final JSONArray addArray = new JSONArray();
    }

    private BuildResult buildNewKnowledges(List<PreviewKnowledge> previewKnowledgeList, String docUuid) {
        BuildResult r = new BuildResult();
        for (PreviewKnowledge p : previewKnowledgeList) {
            Knowledge k = new Knowledge();
            BeanUtils.copyProperties(p, k);
            JSONObject content = k.getContent();
            String auditSuggest = content.getString("auditSuggest");
            if (StringUtils.isEmpty(auditSuggest) || "pass".equals(auditSuggest)) {
                k.setEnabled(1);
                r.addArray.add(this.convertKnowledge2Object(k, docUuid));
            } else {
                k.setEnabled(0);
            }
            k.setSource(0);
            k.setTestHitCount(0L);
            k.setDialogHitCount(0L);
            k.setCoreRepoName(apiUrl.getDefaultAddRepo());
            k.setCreatedAt(LocalDateTime.now());
            k.setUpdatedAt(LocalDateTime.now());
            r.knowledgeList.add(k);
        }
        return r;
    }

    private static final class PushResult {
        final List<String> failedKnowledge = new ArrayList<>();
        final Map<String, String> cbgKnowledgeMap = new HashMap<>();
        String source;
    }

    private PushResult pushChunksBySource(Long fileId, List<String> uuid, JSONArray jsonArray) {
        PushResult r = new PushResult();
        FileInfoV2 fileInfoV2 = fileInfoV2Service.getById(fileId);
        r.source = fileInfoV2.getSource();

        final int maxSaveCount = 200;
        final int maxThreadCount = 3;

        if (ProjectContent.isAiuiRagCompatible(r.source)) {
            // Synchronous batch push
            for (int i = 0; i < jsonArray.size(); i += maxSaveCount) {
                int end = Math.min(i + maxSaveCount, jsonArray.size());
                JSONArray batch = new JSONArray();
                List<String> presetFail = new ArrayList<>();
                for (Object o : jsonArray.subList(i, end)) {
                    JSONObject obj = (JSONObject) o;
                    batch.add(obj);
                    presetFail.add(obj.getString("chunkId"));
                }
                try {
                    List<String> childFailed = this.addKnowledge4AIUI(uuid.get(0), uuid.get(1), batch, r.source);
                    if (!CollectionUtils.isEmpty(childFailed)) {
                        r.failedKnowledge.addAll(childFailed);
                    }
                } catch (Exception e) {
                    log.error("Batch insert knowledge points failed (AIUI)", e);
                    r.failedKnowledge.addAll(presetFail);
                }
            }
            return r;
        }

        if (ProjectContent.isCbgRagCompatible(r.source)) {
            // Concurrent batch push
            ExecutorService pool = new ThreadPoolExecutor(
                    maxThreadCount, maxThreadCount, 0L, TimeUnit.MILLISECONDS,
                    new LinkedBlockingQueue<>(),
                    ThreadFactoryBuilder.create().setNamePrefix("addKnowledge4CBG-").build());
            List<Future<Map<String, String>>> futures = new ArrayList<>();
            try {
                for (int i = 0; i < jsonArray.size(); i += maxSaveCount) {
                    int end = Math.min(i + maxSaveCount, jsonArray.size());
                    JSONArray batch = new JSONArray();
                    for (Object o : jsonArray.subList(i, end)) {
                        batch.add((JSONObject) o);
                    }
                    futures.add(pool.submit(() -> this.addKnowledge4CBG(uuid.get(0), uuid.get(1), batch, r.source)));
                }
                for (Future<Map<String, String>> f : futures) {
                    try {
                        Map<String, String> m = f.get();
                        if (!m.isEmpty())
                            r.cbgKnowledgeMap.putAll(m);
                    } catch (Exception e) {
                        log.error("Failed to get CBG task result", e);
                    }
                }
            } finally {
                pool.shutdown();
            }
            return r;
        }

        // Unknown source: no external push
        return r;
    }

    private void applyPushResult(List<Knowledge> knowledgeList, List<Knowledge> oldAuto, PushResult push, Long fileId) {
        if (ProjectContent.isAiuiRagCompatible(push.source)) {
            // Throw error if all failed
            if (!push.failedKnowledge.isEmpty() && push.failedKnowledge.size() >= knowledgeList.size()) {
                log.error("All knowledge points embedding failed, fileId:{}, failed:{}, total:{}", fileId, push.failedKnowledge.size(), knowledgeList.size());
                throw new BusinessException(ResponseEnum.REPO_KNOWLEDGE_ALL_EMBEDDING_FAILED);
            }
            if (!CollectionUtils.isEmpty(push.failedKnowledge)) {
                for (Knowledge k : knowledgeList) {
                    if (push.failedKnowledge.contains(k.getId())) {
                        k.setEnabled(0);
                    }
                }
            }
            return;
        }

        if (ProjectContent.isCbgRagCompatible(push.source)) {
            // Map dataIndex -> id back to Knowledge.id
            for (Knowledge k : knowledgeList) {
                String dataIndex = k.getContent().getString("dataIndex");
                k.setId(push.cbgKnowledgeMap.get(dataIndex));
            }
            // CBG scenario: will delete oldAuto and save new data later, logic consistent with original
            // implementation
        }
    }

    private void restoreManualKnowledge(String docUuid, String lastUuid) {
        // Criteria handleCriteria = Criteria.where("fileId").is(docUuid).and("source").is(1);
        // List<Knowledge> manualList = mongoTemplate.find(new Query(handleCriteria), Knowledge.class);

        // Use MySQL query to replace MongoDB query
        List<MysqlKnowledge> mysqlList = knowledgeMapper.findByFileIdAndSource(docUuid, 1);
        List<Knowledge> manualList = new ArrayList<>();
        for (MysqlKnowledge mysql : mysqlList) {
            Knowledge knowledge = new Knowledge();
            BeanUtils.copyProperties(mysql, knowledge);
            manualList.add(knowledge);
        }

        for (Knowledge k : manualList) {
            k.setFileId(lastUuid);
            k.setEnabled(1);
            // knowledgeRepository.save(k);

            // Save using MySQL
            MysqlKnowledge mysqlKnowledge = new MysqlKnowledge();
            BeanUtils.copyProperties(k, mysqlKnowledge);
            knowledgeMapper.insert(mysqlKnowledge);
            // Original logic commented out updateChunk, keep not updating external library
        }
    }


    /**
     * Delete documents and their associated knowledge chunks
     *
     * @param ids list of file IDs to be deleted
     * @throws BusinessException if files not found or deletion operations fail
     */
    public void deleteDoc(List<Long> ids) {
        if (CollectionUtils.isEmpty(ids)) {
            return;
        }
        List<FileInfoV2> fileInfoV2List = fileInfoV2Mapper.listByIds(ids);
        List<String> fileUuids = new ArrayList<>();
        for (FileInfoV2 fileInfoV2 : fileInfoV2List) {
            dataPermissionCheckTool.checkFileBelong(fileInfoV2);
            fileUuids.add(fileInfoV2.getUuid());
        }
        // Criteria newCriteria = Criteria.where("fileId").in(fileUuids);
        // List<Knowledge> knowledges = mongoTemplate.find(new Query(newCriteria), Knowledge.class);

        // Use MySQL query to replace MongoDB query
        List<MysqlKnowledge> mysqlKnowledges = knowledgeMapper.findByFileIdIn(fileUuids);
        List<Knowledge> knowledges = new ArrayList<>();
        for (MysqlKnowledge mysql : mysqlKnowledges) {
            Knowledge knowledge = new Knowledge();
            BeanUtils.copyProperties(mysql, knowledge);
            knowledges.add(knowledge);
        }

        // ClientSession session = mongoClient.startSession();
        // try {
        // session.startTransaction();
        if (!CollectionUtils.isEmpty(knowledges)) {
            // knowledgeRepository.deleteAll(knowledges);
            // Delete using MySQL
            List<String> knowledgeIds = knowledges.stream().map(Knowledge::getId).collect(Collectors.toList());
            knowledgeMapper.deleteBatchIds(knowledgeIds);
        }

        // When using CBG, need to build chunkIds map with docId as key
        Map<String, List<String>> chunkIdsMap = new HashMap<>();
        // Iterate through fileDocIds
        for (FileInfoV2 fileInfoV2 : fileInfoV2List) {
            if (ProjectContent.isCbgRagCompatible(fileInfoV2.getSource())) {
                String docId = fileInfoV2.getUuid();
                // For each docId, find all matching Knowledge entries
                List<String> knowledgeIds = knowledges.stream()
                        .filter(knowledge -> knowledge.getFileId().equals(docId))
                        .map(Knowledge::getId)
                        .collect(Collectors.toList());

                // Store results in map
                if (!CollectionUtils.isEmpty(knowledgeIds)) {
                    chunkIdsMap.put(docId, knowledgeIds);
                }
            }
        }

        JSONArray delDocList = new JSONArray();
        delDocList.addAll(fileUuids);
        this.deleteKnowledgeDoc(delDocList, chunkIdsMap);
        // session.commitTransaction();
        // } catch (Exception e) {
        // // Rollback transaction
        // session.abortTransaction();
        // log.error("Failed to delete document", e);
        // throw e;
        // } finally {
        // session.close();
        // }
    }


    /**
     * Handle callback result for knowledge extraction task with retry mechanism
     *
     * @param retResult JSON object containing the callback result with task status and data
     * @throws BusinessException if task not found or processing fails
     */
    @Retryable(value = Exception.class, backoff = @Backoff(delay = 5000, multiplier = 1, maxDelay = 10000))
    public void dealTaskForKnowledgeExtract(JSONObject retResult) {
        log.info("dealTaskForKnowledgeExtract callback result:{}", JSONObject.toJSONString(retResult));
        // 1. Query task
        String taskId = retResult.getString("taskId");
        ExtractKnowledgeTask extractKnowledgeTask = extractKnowledgeTaskService.getOnly(Wrappers.lambdaQuery(ExtractKnowledgeTask.class).eq(ExtractKnowledgeTask::getTaskId, taskId));
        if (extractKnowledgeTask == null || extractKnowledgeTask.getStatus() != 0) {
            log.error("No corresponding task found: " + taskId);
            throw new BusinessException(ResponseEnum.REPO_KNOWLEDGE_NO_TASK);
        }

        boolean success = retResult.getBooleanValue("success");
        // If successful, parse knowledge points and store in database
        String resultTextUrl = retResult.getString("knowledgeUrl");
        this.downloadKnowLedgeData(resultTextUrl, extractKnowledgeTask, success, retResult.getString("err"));
    }


    /**
     * Download and process knowledge data from a given URL
     *
     * @param url the URL to download knowledge data from
     * @param extractKnowledgeTask the extraction task object to be updated
     * @param isSuccess boolean flag indicating if the extraction was successful
     * @param errMsg error message if extraction failed, null if successful
     * @throws BusinessException if file not found, download fails, or data processing fails
     */
    @Transactional
    public void downloadKnowLedgeData(String url, ExtractKnowledgeTask extractKnowledgeTask, boolean isSuccess, String errMsg) {
        Timestamp timestamp = new Timestamp(System.currentTimeMillis());
        FileInfoV2 fileInfoV2 = fileInfoV2Service.getById(extractKnowledgeTask.getFileId());
        if (fileInfoV2 == null) {
            extractKnowledgeTask.setStatus(2);
            extractKnowledgeTask.setReason("No corresponding file found");
            extractKnowledgeTask.setUpdateTime(timestamp);
            extractKnowledgeTaskService.updateById(extractKnowledgeTask);
            return;
        }

        if (!isSuccess) {
            extractKnowledgeTask.setStatus(2);
            extractKnowledgeTask.setReason(errMsg);
            extractKnowledgeTask.setUpdateTime(timestamp);
            extractKnowledgeTaskService.updateById(extractKnowledgeTask);

            fileInfoV2.setStatus(ProjectContent.FILE_PARSE_FAILED);
            fileInfoV2.setReason(errMsg);
            fileInfoV2.setUpdateTime(timestamp);
            fileInfoV2Service.updateById(fileInfoV2);
            return;
        }

        Repo repo = repoService.getById(fileInfoV2.getRepoId());


        String entityBody = null;
        try {
            RestTemplate restTemplate = new RestTemplate();
            ResponseEntity<String> forEntity = restTemplate.getForEntity(url, String.class);
            if (forEntity.getStatusCode() != HttpStatus.OK) {
                throw new BusinessException(ResponseEnum.REPO_KNOWLEDGE_DOWNLOAD_FAILED);
            }
            entityBody = forEntity.getBody();
            JSONArray jsonArray = JSON.parseArray(entityBody);
            List<ChunkInfo> chunkInfos = null;
            if (repo.getEnableAudit()) {
                if (jsonArray != null) {
                    chunkInfos = jsonArray.toJavaList(ChunkInfo.class);
                    this.auditPreviewKnowledge(chunkInfos);
                }
            }
            this.storagePreviewKnowledge(fileInfoV2.getUuid(), fileInfoV2.getId(), chunkInfos);

            extractKnowledgeTask.setStatus(1);
            extractKnowledgeTask.setUpdateTime(timestamp);
            extractKnowledgeTaskService.updateById(extractKnowledgeTask);

            fileInfoV2.setStatus(ProjectContent.FILE_PARSE_SUCCESSED);
            fileInfoV2.setReason(null);
            fileInfoV2.setUpdateTime(timestamp);
            fileInfoV2Service.updateById(fileInfoV2);
        } catch (Exception e) {
            log.error("Error downloading & parsing file", e);
            log.error("Result length: {}", entityBody == null ? 0 : entityBody.length());
            extractKnowledgeTask.setStatus(2);
            extractKnowledgeTask.setReason("Error downloading & parsing file, " + e.getMessage());
            extractKnowledgeTask.setUpdateTime(timestamp);
            extractKnowledgeTaskService.updateById(extractKnowledgeTask);

            fileInfoV2.setStatus(ProjectContent.FILE_PARSE_FAILED);
            fileInfoV2.setReason("Error downloading & parsing file, " + e.getMessage());
            fileInfoV2.setUpdateTime(timestamp);
            fileInfoV2Service.updateById(fileInfoV2);
        }
    }

    /**
     * Audit preview knowledge chunks for content compliance
     *
     * @param chunkInfos list of chunk information objects to be audited
     */
    private void auditPreviewKnowledge(List<ChunkInfo> chunkInfos) {
        if (CollectionUtils.isEmpty(chunkInfos)) {
            return;
        }
        // List<Future<SyncAuditResult<TextDetail>>> futureList = new ArrayList<>();
        // for (ChunkInfo chunkInfo : chunkInfos) {
        // String knowledgeStr = chunkInfo.getContent();
        // JSONObject previewKnowledgeObject = JSON.parseObject(JSON.toJSONString(chunkInfo));
        // Future<SyncAuditResult<TextDetail>> syncAuditResultFuture =
        // auditService.syncAuditText(knowledgeStr, previewKnowledgeObject);
        // futureList.add(syncAuditResultFuture);
        // }
        // for (Future<SyncAuditResult<TextDetail>> syncAuditResultFuture : futureList) {
        // try {
        // syncAuditResultFuture.get();
        // } catch (Exception e) {
        // log.error("Failed to batch get audit results in thread, skip this record", e);
        // }
        // }
    }

    /**
     * Perform pre-check validation and return file and repository information
     *
     * @param fileId the database ID of the file to be checked
     * @return list containing: uuid[0] = file uuid, uuid[1] = core system side repoId, uuid[2] = last
     *         uuid
     * @throws BusinessException if file not found or repository not found
     */
    private List<String> preCheck(Long fileId) {
        FileInfoV2 fileInfoV2 = fileInfoV2Service.getById(fileId);
        if (fileInfoV2 == null) {
            throw new BusinessException(ResponseEnum.REPO_FILE_NOT_EXIST);
        }
        Repo repo = repoService.getById(fileInfoV2.getRepoId());
        if (repo == null) {
            throw new BusinessException(ResponseEnum.REPO_NOT_EXIST);
        }

        List<String> uuids = new ArrayList<>();
        uuids.add(fileInfoV2.getUuid());
        uuids.add(repo.getCoreRepoId());
        uuids.add(fileInfoV2.getLastUuid());
        return uuids;
    }

    /**
     * Add knowledge chunks to the external knowledge base
     *
     * @param docId the document ID
     * @param group the group/repository ID
     * @param addChunkArray JSON array containing chunks to be added
     * @param source the source type of the knowledge base (AIUI/CBG)
     * @return KnowledgeResponse containing the operation result
     * @throws BusinessException if knowledge addition fails
     */
    private KnowledgeResponse addKnowledge(String docId, String group, JSONArray addChunkArray, String source) {
        KnowledgeResponse response = new KnowledgeResponse();
        if (!addChunkArray.isEmpty()) { // Embedding
            KnowledgeRequest request = new KnowledgeRequest();
            request.setDocId(docId);
            request.setGroup(group);
            request.setChunks(addChunkArray.toArray());
            request.setRagType(source);
            response = knowledgeV2ServiceCallHandler.saveChunk(request);
            if (response.getCode() != 0) {
                log.error("Failed to add knowledge point, message:{}", response.getMessage());
                throw new BusinessException(ResponseEnum.REPO_KNOWLEDGE_ADD_FAILED);
            }
        }
        return response;
    }

    /**
     * Add knowledge chunks specifically for CBG knowledge base
     *
     * @param docId the document ID
     * @param group the group/repository ID
     * @param addChunkArray JSON array containing chunks to be added
     * @param source the source type (should be CBG)
     * @return Map containing dataIndex to knowledge ID mapping
     * @throws BusinessException if CBG knowledge base operations fail
     */
    public Map<String, String> addKnowledge4CBG(String docId, String group, JSONArray addChunkArray, String source) {
        KnowledgeResponse knowledge = this.addKnowledge(docId, group, addChunkArray, source);
        List<CbgKnowledgeData> cbgKnowledgeDataList;
        Map<String, String> resultMap = new HashMap<>();
        try {
            cbgKnowledgeDataList = ((JSONArray) knowledge.getData()).toJavaList(CbgKnowledgeData.class);
        } catch (Exception e) {
            log.error("CBG knowledge base retrieval failed : {}", e.getMessage(), e);
            return resultMap;
        }
        if (!cbgKnowledgeDataList.isEmpty()) {
            for (CbgKnowledgeData cbgKnowledgeData : cbgKnowledgeDataList) {
                resultMap.put(String.format("%.0f", cbgKnowledgeData.getDataIndex()), cbgKnowledgeData.getId());
            }
        }

        return resultMap;
    }

    /**
     * Add knowledge chunks specifically for AIUI knowledge base
     *
     * @param docId the document ID
     * @param group the group/repository ID
     * @param addChunkArray JSON array containing chunks to be added
     * @param source the source type (should be AIUI)
     * @return List of failed chunk IDs if any failures occurred
     * @throws BusinessException if AIUI knowledge base operations fail
     */
    public List<String> addKnowledge4AIUI(String docId, String group, JSONArray addChunkArray, String source) {
        KnowledgeResponse knowledge = this.addKnowledge(docId, group, addChunkArray, source);
        List<String> resultList = new ArrayList<>();
        JSONObject data = (JSONObject) knowledge.getData();
        if (data != null) {
            JSONObject failedChunk = data.getJSONObject("failedChunk");
            if (failedChunk != null) {
                String errListStr = failedChunk.getString("chunkId");
                if (!StringUtils.isEmpty(errListStr)) {
                    String[] errIds = errListStr.split(",");
                    log.error("failed repoId:{},  errIds:{}", group, errIds);
                    resultList = Arrays.asList(errIds);
                }
            }
        }
        return resultList;
    }


    /**
     * Update knowledge chunks in the external knowledge base
     *
     * @param docId the document ID
     * @param group the group/repository ID
     * @param updateChunkArray JSON array containing chunks to be updated
     * @return List of failed chunk IDs if any failures occurred
     * @throws BusinessException if file not found or update operations fail
     */
    public List<String> updateKnowledge(String docId, String group, JSONArray updateChunkArray) {
        List<String> resultList = new ArrayList<>();
        if (!updateChunkArray.isEmpty()) { // Delete document
            KnowledgeRequest request = new KnowledgeRequest();
            request.setDocId(docId);
            request.setGroup(group);
            request.setChunks(updateChunkArray.toArray());
            FileInfoV2 fileInfoV2 = fileInfoV2Service.getOnly(new QueryWrapper<FileInfoV2>().eq("uuid", docId));
            if (fileInfoV2 == null) {
                throw new BusinessException(ResponseEnum.REPO_FILE_NOT_EXIST);
            }

            request.setRagType(fileInfoV2.getSource());

            KnowledgeResponse response = knowledgeV2ServiceCallHandler.updateChunk(request);
            if (response.getCode() != 0) {
                log.error("Failed to modify knowledge point, message:{}", response.getMessage());
                throw new BusinessException(ResponseEnum.REPO_KNOWLEDGE_MODIFY_FAILED);
            }
            JSONObject data = (JSONObject) response.getData();
            if (data != null) {
                JSONObject failedChunk = data.getJSONObject("failedChunk");
                if (failedChunk != null) {
                    String errListStr = failedChunk.getString("chunkId");
                    if (!StringUtils.isEmpty(errListStr)) {
                        String[] errIds = errListStr.split(",");
                        log.error("failed repoId:{},  errIds:{}", group, errIds);
                        resultList = Arrays.asList(errIds);
                    }
                }
            }
        }
        return resultList;
    }

    /**
     * Delete knowledge documents from the external knowledge base
     *
     * @param deleteDocIds JSON array containing document IDs to be deleted
     * @param chunkIdsMap map containing document ID to chunk IDs mapping for CBG knowledge base
     * @throws BusinessException if file not found or deletion operations fail
     */
    public void deleteKnowledgeDoc(JSONArray deleteDocIds, Map<String, List<String>> chunkIdsMap) {
        boolean needDelete = true;
        if (!deleteDocIds.isEmpty()) { // Delete documents
            for (int i = 0; i < deleteDocIds.size(); i++) {
                KnowledgeRequest request = new KnowledgeRequest();
                String docId = deleteDocIds.getString(i);
                request.setDocId(docId);
                if (!CollectionUtils.isEmpty(chunkIdsMap) && chunkIdsMap.containsKey(docId)) {
                    request.setChunkIds(chunkIdsMap.get(docId));
                }
                FileInfoV2 fileInfoV2 = fileInfoV2Service.getOnly(new QueryWrapper<FileInfoV2>().eq("uuid", docId));
                if (fileInfoV2 == null) {
                    throw new BusinessException(ResponseEnum.REPO_FILE_NOT_EXIST);
                }
                if (ProjectContent.isCbgRagCompatible(fileInfoV2.getSource())) {
                    request.setRagType(fileInfoV2.getSource());
                    if (CollectionUtils.isEmpty(request.getChunkIds())) {
                        needDelete = false;
                    }
                }
                if (needDelete) {
                    KnowledgeResponse response = knowledgeV2ServiceCallHandler.deleteDocOrChunk(request);
                    if (response.getCode() != 0) {
                        log.error("Failed to delete file, message:{}", response.getMessage());
                        throw new BusinessException(ResponseEnum.REPO_FILE_DELETE_FAILED);
                    }
                }
            }
        }
    }

    /**
     * Delete specific knowledge chunks from the external knowledge base
     *
     * @param docId the document ID containing the chunks
     * @param deleteChunkIds JSON array containing chunk IDs to be deleted
     * @throws BusinessException if file not found or deletion operations fail
     */
    public void deleteKnowledgeChunks(String docId, JSONArray deleteChunkIds) {
        if (!deleteChunkIds.isEmpty()) { // Delete documents
            KnowledgeRequest request = new KnowledgeRequest();
            request.setDocId(docId);
            request.setChunkIds(deleteChunkIds.toJavaList(String.class));
            FileInfoV2 fileInfoV2 = fileInfoV2Service.getOnly(new QueryWrapper<FileInfoV2>().eq("uuid", docId));
            if (fileInfoV2 == null) {
                throw new BusinessException(ResponseEnum.REPO_FILE_NOT_EXIST);
            }
            request.setRagType(fileInfoV2.getSource());
            KnowledgeResponse response = knowledgeV2ServiceCallHandler.deleteDocOrChunk(request);
            if (response.getCode() != 0) {
                log.error("Failed to delete knowledge chunk, message:{}", response.getMessage());
                throw new BusinessException(ResponseEnum.REPO_KNOWLEDGE_DELETE_FAILED);
            }
        }
    }

    /**
     * Create a Knowledge POJO from KnowledgeVO with default settings
     *
     * @param knowledgeVO the knowledge value object containing user input
     * @param fileId the file UUID associated with this knowledge
     * @return Knowledge object with populated default values
     */
    private Knowledge getKnowledgePojo(KnowledgeVO knowledgeVO, String fileId) {
        Knowledge knowledge = new Knowledge();
        knowledge.setFileId(fileId);
        knowledge.setContent(this.getKnowledgeDefaultConfig(knowledgeVO.getContent()));
        knowledge.setCharCount((long) knowledgeVO.getContent().length());
        knowledge.setEnabled(1);
        // Source is manual creation
        knowledge.setSource(1);
        knowledge.setTestHitCount(0L);
        knowledge.setDialogHitCount(0L);
        knowledge.setCreatedAt(LocalDateTime.now());
        knowledge.setUpdatedAt(LocalDateTime.now());
        return knowledge;
    }

    /**
     * Create default configuration JSON object for knowledge content
     *
     * @param knowledgeContent the text content of the knowledge
     * @return JSONObject containing default knowledge configuration
     */
    private JSONObject getKnowledgeDefaultConfig(String knowledgeContent) {
        JSONObject jsonObject = new JSONObject();
        jsonObject.put("title", "");
        jsonObject.put("content", knowledgeContent);
        jsonObject.put("context", knowledgeContent);
        JSONArray jsonArray = new JSONArray();
        jsonArray.add("text");
        jsonObject.put("type", jsonArray);
        jsonObject.put("docInfo", new JSONObject());
        jsonObject.put("references", new JSONObject());
        return jsonObject;
    }

    /**
     * Convert Knowledge object to JSON format for external knowledge base
     *
     * @param knowledge the Knowledge object to be converted
     * @param fileId the file ID (currently unused in implementation)
     * @return JSONObject formatted for external knowledge base consumption
     */
    private JSONObject convertKnowledge2Object(Knowledge knowledge, String fileId) {
        // Embed knowledge point, set chunkId to MongoDB ID
        JSONObject content = knowledge.getContent();

        content.put("chunkId", knowledge.getId());
        return content;
    }

    /**
     * Convert list of Knowledge objects to JSON array for batch addition
     *
     * @param knowledges list of Knowledge objects to be converted
     * @param fileId the file ID associated with the knowledge
     * @return JSONArray containing knowledge objects formatted for external knowledge base
     */
    private JSONArray getWaitAddKnowledge(List<Knowledge> knowledges, String fileId) {
        JSONArray jsonArray = new JSONArray();
        if (!CollectionUtils.isEmpty(knowledges)) {
            for (Knowledge knowledge : knowledges) {
                jsonArray.add(this.convertKnowledge2Object(knowledge, fileId));
            }
        }
        return jsonArray;
    }
}
