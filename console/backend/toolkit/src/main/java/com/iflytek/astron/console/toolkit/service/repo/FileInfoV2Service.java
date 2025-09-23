package com.iflytek.astron.console.toolkit.service.repo;

import cn.hutool.core.map.MapUtil;
import cn.hutool.core.util.RandomUtil;
import com.alibaba.fastjson2.*;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.iflytek.astron.console.commons.util.ChatFileHttpClient;
import com.iflytek.astron.console.commons.util.S3ClientUtil;
import com.iflytek.astron.console.commons.util.space.SpaceInfoUtil;
import com.iflytek.astron.console.commons.constant.ResponseEnum;
import com.iflytek.astron.console.commons.exception.BusinessException;
import com.iflytek.astron.console.toolkit.common.Result;
import com.iflytek.astron.console.toolkit.common.constant.*;
import com.iflytek.astron.console.toolkit.config.properties.ApiUrl;
import com.iflytek.astron.console.toolkit.entity.common.PageData;
import com.iflytek.astron.console.toolkit.entity.dto.*;
import com.iflytek.astron.console.toolkit.entity.knowledge.ChunkInfo;
import com.iflytek.astron.console.toolkit.entity.mongo.PreviewKnowledge;
import com.iflytek.astron.console.toolkit.entity.table.knowledge.MysqlKnowledge;
import com.iflytek.astron.console.toolkit.entity.table.knowledge.MysqlPreviewKnowledge;
import com.iflytek.astron.console.toolkit.mapper.knowledge.KnowledgeMapper;
import com.iflytek.astron.console.toolkit.mapper.knowledge.PreviewKnowledgeMapper;
import com.iflytek.astron.console.toolkit.entity.pojo.*;
import com.iflytek.astron.console.toolkit.entity.table.ConfigInfo;
import com.iflytek.astron.console.toolkit.entity.table.repo.*;
import com.iflytek.astron.console.toolkit.entity.vo.HtmlFileVO;
import com.iflytek.astron.console.toolkit.entity.vo.knowledge.SparkUploadVo;
import com.iflytek.astron.console.toolkit.entity.vo.repo.*;
import com.iflytek.astron.console.toolkit.handler.UserInfoManagerHandler;
import com.iflytek.astron.console.toolkit.mapper.repo.FileDirectoryTreeMapper;
import com.iflytek.astron.console.toolkit.mapper.repo.FileInfoV2Mapper;
import com.iflytek.astron.console.toolkit.service.common.ConfigInfoService;
import com.iflytek.astron.console.toolkit.service.task.ExtractKnowledgeTaskService;
import com.iflytek.astron.console.toolkit.task.EmbeddingFileTask;
import com.iflytek.astron.console.toolkit.task.SliceFileTask;
import com.iflytek.astron.console.toolkit.tool.DataPermissionCheckTool;
import com.iflytek.astron.console.toolkit.tool.FileUploadTool;
import com.iflytek.astron.console.toolkit.util.*;
import jakarta.annotation.Resource;
import jakarta.servlet.ServletOutputStream;
import jakarta.servlet.http.*;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.apache.poi.hssf.usermodel.*;
import org.apache.poi.ss.usermodel.HorizontalAlignment;
import org.apache.poi.ss.usermodel.VerticalAlignment;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.*;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.CollectionUtils;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.*;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * FileInfoV2 Service Implementation Class This service handles file operations including upload,
 * slicing, embedding, and management for various file sources including Spark RAG, CBG RAG, and
 * AIUI RAG2.
 *
 * @author xxzhang23
 * @since 2023-12-07
 */
@Service
@Slf4j
public class FileInfoV2Service extends ServiceImpl<FileInfoV2Mapper, FileInfoV2> {
    /**
     * Get single record by query wrapper
     *
     * @param wrapper query wrapper
     * @return single FileInfoV2 record or null if not found
     */
    public FileInfoV2 getOnly(QueryWrapper<FileInfoV2> wrapper) {
        wrapper.last("limit 1");
        return this.getOne(wrapper);
    }

    @Resource
    private FileInfoV2Mapper fileInfoV2Mapper;
    @Resource
    private ConfigInfoService configInfoService;
    @Resource
    private S3Util s3UtilClient;
    @Resource
    @Lazy
    private RepoService repoService;

    @Resource
    FileDirectoryTreeMapper fileDirectoryTreeMapper;
    @Resource
    private FileDirectoryTreeService fileDirectoryTreeService;
    @Resource
    private KnowledgeService knowledgeService;
    @Resource
    private ExtractKnowledgeTaskService extractKnowledgeTaskService;
    @Resource
    private KnowledgeMapper knowledgeMapper;
    @Resource
    private PreviewKnowledgeMapper previewKnowledgeMapper;

    @Resource
    FileUploadTool fileUploadTool;

    @Resource
    DataPermissionCheckTool dataPermissionCheckTool;

    @Autowired
    ChatFileHttpClient chatFileHttpClient;
    @Autowired
    private S3ClientUtil s3ClientUtil;
    // @Autowired
    // private ResourceQuotaFacade facade;

    @Value("${api.url.sparkDocUrl}")
    private String sparkDocUrl;

    @Value("${biz.cbg-rag-max-char-count}")
    private long cbgRagMaxCharCount;
    private ApiUrl apiUrl;

    /**
     * Upload file to repository
     *
     * @param file uploaded file
     * @param parentId parent directory ID
     * @param repoId repository ID
     * @param tag file source tag
     * @param request HTTP request
     * @return FileInfoV2 object containing file information
     * @throws BusinessException if file type is invalid or validation fails
     */
    @Transactional
    public FileInfoV2 uploadFile(MultipartFile file, Long parentId, Long repoId, String tag, HttpServletRequest request) {
        String originalFilename = file.getOriginalFilename();
        String fileType = getFileFormat(originalFilename);

        // 1. File type validation
        validateFileType(fileType);

        // 2. Spark upload returns directly
        if (ProjectContent.isSparkRagCompatible(tag)) {
            return handleSparkUpload(file, request);
        }

        Repo repo = repoService.getById(repoId);
        dataPermissionCheckTool.checkRepoBelong(repo);

        // 4. Calculate character count
        int charCount = countChars(file);

        // 5. Source-specific validation
        if (ProjectContent.isCbgRagCompatible(tag)) {
            validateCbgFile(file, originalFilename, charCount);
        } else if (ProjectContent.isAiuiRagCompatible(tag)) {
            validateAiuiFile(file, fileType, originalFilename);
        }

        // 6. Upload & save
        JSONObject uploadRes = fileUploadTool.uploadFile(file, tag);
        return createFile(repoId,
                UUID.randomUUID().toString().replace("-", ""),
                originalFilename,
                parentId,
                uploadRes.getString("s3Key"),
                file.getSize(),
                (long) charCount,
                0,
                tag);
    }


    /**
     * Validate file type
     *
     * @param fileType file type to validate
     * @throws BusinessException if file type is not supported
     */
    private void validateFileType(String fileType) {
        if ("html".equalsIgnoreCase(fileType) || "svg".equalsIgnoreCase(fileType)) {
            throw new BusinessException(ResponseEnum.REPO_FILE_UPLOAD_TYPE_NOT_EXIST);
        }
    }

    /**
     * Handle Spark file upload
     *
     * @param file uploaded file
     * @param request HTTP request
     * @return FileInfoV2 object with Spark upload information
     */
    private FileInfoV2 handleSparkUpload(MultipartFile file, HttpServletRequest request) {
        SparkUploadVo sparkUploadVo = uploadSpark(file, request);
        FileInfoV2 fileInfoV2 = new FileInfoV2();
        fileInfoV2.setUuid(sparkUploadVo.getFileId());
        fileInfoV2.setName(sparkUploadVo.getFileName());
        fileInfoV2.setCharCount(Long.valueOf(sparkUploadVo.getLetterNum()));
        return fileInfoV2;
    }

    /**
     * Resolve user ID from current context
     *
     * @return resolved user ID
     */
    private String resolveUserId() {
        String userId = UserInfoManagerHandler.getUserId();
        Long spaceId = SpaceInfoUtil.getSpaceId();
        if (spaceId != null) {
            String spaceUserId = SpaceInfoUtil.getUidByCurrentSpaceId();
            if (spaceUserId != null) {
                userId = spaceUserId;
            }
        }
        return userId;
    }

    /**
     * Count characters in uploaded file
     *
     * @param file the uploaded file to count characters
     * @return total character count including newlines
     */
    private int countChars(MultipartFile file) {
        int charCount = 0;
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(file.getInputStream()))) {
            String line;
            while ((line = reader.readLine()) != null) {
                charCount += line.length() + 1; // including newlines
            }
        } catch (IOException e) {
            log.error("Failed to get file character count", e);
        }
        return charCount;
    }

    /**
     * Validate CBG file constraints
     *
     * @param file uploaded file
     * @param originalFilename original filename
     * @param charCount character count
     * @throws BusinessException if file size or character count exceeds limits
     */
    private void validateCbgFile(MultipartFile file, String originalFilename, int charCount) {
        long size = file.getSize();
        if (checkIsPic(originalFilename)) {
            if (size > 5 * 1024 * 1024) {
                throw new BusinessException(ResponseEnum.REPO_FILE_UPLOAD_FAILED_PIC_5MB);
            }
        } else {
            if (size > 20 * 1024 * 1024) {
                throw new BusinessException(ResponseEnum.REPO_FILE_UPLOAD_FAILED_FILE_20MB);
            }
        }
        if (cbgRagMaxCharCount < charCount
                && originalFilename != null
                && getFileFormat(originalFilename).equalsIgnoreCase(ProjectContent.TXT_FILE_TYPE)) {
            throw new BusinessException(ResponseEnum.REPO_FILE_UPLOAD_FAILED_WORDS_100W);
        }
    }

    /**
     * Validate AIUI file constraints
     *
     * @param file uploaded file
     * @param fileType file type
     * @param originalFilename original filename
     * @throws BusinessException if file type is empty or size exceeds limits
     */
    private void validateAiuiFile(MultipartFile file, String fileType, String originalFilename) {
        if (StringUtils.isEmpty(fileType)) {
            log.error("Xingchen file type is empty, filename: {}", originalFilename);
            throw new BusinessException(ResponseEnum.REPO_FILE_TYPE_EMPTY_XINGCHEN);
        }
        long size = file.getSize();
        if (fileType.equalsIgnoreCase(ProjectContent.TXT_FILE_TYPE)
                || fileType.equalsIgnoreCase(ProjectContent.MD_FILE_TYPE)) {
            if (size > 10 * 1024 * 1024) {
                throw new BusinessException(ResponseEnum.REPO_FILE_UPLOAD_FAILED_FILE_10MB_XINGCHEN);
            }
        } else {
            if (size > 100 * 1024 * 1024) {
                throw new BusinessException(ResponseEnum.REPO_FILE_UPLOAD_FAILED_FILE_100MB_XINGCHEN);
            }
        }
    }

    /**
     * Upload file to Spark platform
     *
     * @param file file to upload
     * @param request HTTP request
     * @return SparkUploadVo containing upload result
     * @throws BusinessException if upload fails
     */
    private SparkUploadVo uploadSpark(MultipartFile file, HttpServletRequest request) {
        try {
            String fileName = UserInfoManagerHandler.getUserId() + "_" + RandomUtil.randomString(6) + file.getOriginalFilename();
            String link;
            try (InputStream in = file.getInputStream()) {
                String contentType = Optional.ofNullable(file.getContentType())
                        .filter(ct -> !ct.isBlank())
                        .orElse("application/octet-stream");
                link = s3ClientUtil.uploadObject(fileName, contentType, in);
            }
            // Get doc signature
            HashMap<String, String> docHeader = chatFileHttpClient.getSignForXinghuoDs();
            // Call upload interface
            String uploadUrl = sparkDocUrl + "/openapi/v1/file/upload";
            Map<String, Object> uploadParams = new HashMap<>();
            uploadParams.put("url", link);
            uploadParams.put("fileName", file.getOriginalFilename());
            uploadParams.put("parseType", "AUTO");
            uploadParams.put("fileType", "wiki");
            uploadParams.put("stepByStep", false);
            log.info("Calling file upload interface, url:{}, header:{}, params:{}", uploadUrl, docHeader, uploadParams);
            String uploadString = OkHttpUtil.postMultipart(uploadUrl, docHeader, null, uploadParams, null);
            log.info("File upload interface response:{}", uploadString);
            JSONObject uploadStringObject = JSON.parseObject(uploadString);
            if (uploadStringObject.getIntValue("code") != 0) {
                throw new BusinessException(ResponseEnum.REPO_FILE_UPLOAD_FAILED);
            }
            SparkUploadVo data1 = uploadStringObject.getObject("data", SparkUploadVo.class);
            data1.setFileName(file.getOriginalFilename());
            return data1;
        } catch (Exception ex) {
            log.info("Spark file upload failed, error:{}", ex.getMessage());
            throw new BusinessException(ResponseEnum.REPO_FILE_UPLOAD_FAILED);
        }

    }


    /**
     * Create file record in database
     *
     * @param repoId repository ID
     * @param sourceId source ID
     * @param originalFilename original filename
     * @param parentId parent directory ID
     * @param s3Key S3 storage key
     * @param size file size
     * @param charCount character count
     * @param enable enabled status
     * @param tag file source tag
     * @return created FileInfoV2 object
     */
    public FileInfoV2 createFile(Long repoId, String sourceId, String originalFilename, Long parentId, String s3Key,
            Long size, Long charCount, Integer enable, String tag) {
        FileInfoV2 fileInfoV2 = new FileInfoV2();
        fileInfoV2.setUuid(sourceId);
        fileInfoV2.setUid(UserInfoManagerHandler.getUserId());
        fileInfoV2.setRepoId(repoId);
        fileInfoV2.setName(originalFilename);
        fileInfoV2.setAddress(s3Key);
        fileInfoV2.setSize(size);
        fileInfoV2.setCharCount(charCount);
        fileInfoV2.setType(getFileFormat(originalFilename));
        fileInfoV2.setStatus(ProjectContent.FILE_UPLOAD_STATUS);
        fileInfoV2.setEnabled(enable);
        fileInfoV2.setPid(parentId);
        fileInfoV2.setSource(tag);
        if (SpaceInfoUtil.getSpaceId() != null) {
            fileInfoV2.setSpaceId(SpaceInfoUtil.getSpaceId());
        }

        Timestamp timestamp = new Timestamp(System.currentTimeMillis());
        fileInfoV2.setCreateTime(timestamp);
        fileInfoV2.setUpdateTime(timestamp);

        this.save(fileInfoV2);
        return fileInfoV2;
    }

    /**
     * Truncate string to specified maximum length
     *
     * @param original the original string to truncate
     * @param maxLength the maximum allowed length
     * @return truncated string or original if shorter than maxLength
     */
    private String truncateString(String original, int maxLength) {
        if (original.length() > maxLength) {
            return original.substring(0, maxLength);
        } else {
            return original;
        }
    }


    /**
     * Create HTML file records in database
     *
     * @param htmlFileVO HTML file creation parameters
     * @return list of created FileInfoV2 objects
     * @throws BusinessException if repository access is denied
     */
    public List<FileInfoV2> createHtmlFile(HtmlFileVO htmlFileVO) {
        Repo repo = repoService.getById(htmlFileVO.getRepoId());
        dataPermissionCheckTool.checkRepoBelong(repo);
        List<String> htmlAddressList = htmlFileVO.getHtmlAddressList();
        List<FileInfoV2> fileInfoV2List = new ArrayList<>();
        for (String htmlAddress : htmlAddressList) {
            String htmlAddressTrim = htmlAddress.trim();
            FileInfoV2 fileInfoV2 = new FileInfoV2();
            fileInfoV2.setUuid(UUID.randomUUID().toString().replace("-", ""));
            fileInfoV2.setUid(UserInfoManagerHandler.getUserId());
            fileInfoV2.setRepoId(htmlFileVO.getRepoId());
            fileInfoV2.setName(truncateString(htmlAddressTrim, 30));
            fileInfoV2.setAddress(htmlAddressTrim);
            fileInfoV2.setSize(0L);
            fileInfoV2.setCharCount(0L);
            String fileFormat = getFileFormat(htmlAddressTrim);
            if (ProjectContent.isValidFileType(fileFormat)) {
                fileInfoV2.setType(fileFormat);
            } else {
                fileInfoV2.setType(ProjectContent.HTML_FILE_TYPE);
            }
            fileInfoV2.setStatus(ProjectContent.FILE_UPLOAD_STATUS);
            fileInfoV2.setEnabled(0);
            fileInfoV2.setPid(htmlFileVO.getParentId());
            Timestamp timestamp = new Timestamp(System.currentTimeMillis());
            fileInfoV2.setCreateTime(timestamp);
            fileInfoV2.setUpdateTime(timestamp);
            if (SpaceInfoUtil.getSpaceId() != null) {
                fileInfoV2.setSpaceId(SpaceInfoUtil.getSpaceId());
            }
            fileInfoV2List.add(fileInfoV2);
        }
        if (!CollectionUtils.isEmpty(fileInfoV2List)) {
            this.saveBatch(fileInfoV2List);
        }

        return fileInfoV2List;
    }


    /**
     * Slice files into knowledge chunks
     *
     * @param sliceFileVO file slicing parameters containing file IDs and slice configuration
     * @return Result indicating success or failure of slicing operation
     * @throws InterruptedException if thread execution is interrupted
     * @throws ExecutionException if execution fails
     * @throws BusinessException if files are currently being parsed or slice range is invalid
     */
    public Result<Boolean> sliceFiles(DealFileVO sliceFileVO) throws InterruptedException, ExecutionException {
        Long spaceId = SpaceInfoUtil.getSpaceId();
        if (ProjectContent.isSparkRagCompatible(sliceFileVO.getTag())) {
            if (sliceFileVO.getSliceConfig().getType().equals(1)) {
                HashMap<String, String> header = new HashMap<>();
                // Spark split interface
                String url = sparkDocUrl + "/openapi/v1/file/split";
                JSONObject params = new JSONObject();
                params.put("fileIds", sliceFileVO.getFileIds());
                params.put("isSplitDefault", false);
                params.put("splitType", "wiki");
                JSONObject wikiSplit = new JSONObject();
                List<String> separator = sliceFileVO.getSliceConfig().getSeperator();
                List<String> separatorBase64 = new ArrayList<>();
                if (!separator.isEmpty()) {
                    for (String string : separator) {
                        String base64 = Base64.getEncoder().encodeToString(string.getBytes());
                        separatorBase64.add(base64);
                    }
                }
                wikiSplit.put("chunkSeparators", separatorBase64);
                wikiSplit.put("chunkSize", sliceFileVO.getSliceConfig().getLengthRange().get(1));
                wikiSplit.put("minChunkSize", sliceFileVO.getSliceConfig().getLengthRange().get(0));
                params.put("wikiSplitExtends", wikiSplit);
                String post = OkHttpUtil.post(url, header, params.toJSONString());
                JSONObject jsonObject = JSONObject.parseObject(post);
                if (jsonObject.getIntValue("code") != 0) {
                    throw new BusinessException(ResponseEnum.REPO_FILE_SLICE_FAILED);
                }
            } else {
                return Result.success(true);
            }
        } else {
            List<Long> fileIds = sliceFileVO.getFileIds()
                    .stream()
                    .map(Long::valueOf)
                    .collect(Collectors.toList());
            if (!CollectionUtils.isEmpty(fileIds)) {
                ExecutorService executorService = Executors.newFixedThreadPool(fileIds.size());
                List<FileInfoV2> fileInfoV2List = fileInfoV2Mapper.listByIds(fileIds);
                List<Future<Boolean>> futures = new ArrayList<>();
                for (FileInfoV2 fileInfoV2 : fileInfoV2List) {
                    if (null == spaceId) {
                        dataPermissionCheckTool.checkFileBelong(fileInfoV2);
                    }

                    if (fileInfoV2.getStatus().equals(ProjectContent.FILE_PARSE_DOING)) {
                        throw new BusinessException(ResponseEnum.REPO_KNOWLEDGE_SPLITTING);
                    }
                    // Check slice default values and range
                    if (sliceFileVO.getSliceConfig().getLengthRange() != null) {
                        if (ProjectContent.isAiuiRagCompatible(fileInfoV2.getSource())) {
                            if (sliceFileVO.getSliceConfig().getLengthRange().get(0) < 16 || sliceFileVO.getSliceConfig().getLengthRange().get(1) > 1024) {
                                throw new BusinessException(ResponseEnum.REPO_FILE_SLICE_RANGE_16_1024);
                            }
                        }
                    }
                    Long fileId = fileInfoV2.getId();
                    // Insert data into file_directory_tree table
                    FileDirectoryTree fileDirectoryTree = fileDirectoryTreeService.getOnly(Wrappers.lambdaQuery(FileDirectoryTree.class)
                            .eq(FileDirectoryTree::getAppId, fileInfoV2.getRepoId())
                            .eq(FileDirectoryTree::getFileId, fileId));

                    if (fileDirectoryTree == null) {
                        fileDirectoryTree = new FileDirectoryTree();
                        fileDirectoryTree.setIsFile(1);
                        fileDirectoryTree.setName(fileInfoV2.getName());
                        fileDirectoryTree.setAppId(fileInfoV2.getRepoId().toString());
                        fileDirectoryTree.setParentId(fileInfoV2.getPid());
                        fileDirectoryTree.setFileId(fileId);
                        fileDirectoryTree.setCreateTime(LocalDateTime.now());
                        // Insert a record directly into database table
                        fileDirectoryTreeMapper.insert(fileDirectoryTree);
                    }
                    // Update slice configuration
                    SliceConfig sliceConfig = sliceFileVO.getSliceConfig();
                    fileInfoV2.setSliceConfig(JSON.toJSONString(sliceConfig));
                    fileInfoV2.setCurrentSliceConfig(JSON.toJSONString(sliceConfig));
                    fileInfoV2.setStatus(ProjectContent.FILE_PARSE_DOING);
                    fileInfoV2Mapper.updateById(fileInfoV2);
                    Future<Boolean> future = executorService.submit(new SliceFileTask(this, fileInfoV2.getId(), sliceConfig, 0));
                    futures.add(future);
                }
                executorService.shutdown();
                boolean ignoreVar = executorService.awaitTermination(Long.MAX_VALUE, TimeUnit.NANOSECONDS);
                boolean allFailed = true;
                for (Future<Boolean> future : futures) {
                    Boolean result = future.get();
                    if (result) {
                        allFailed = false;
                    }
                }
                if (allFailed) {
                    throw new BusinessException(ResponseEnum.REPO_FILE_ALL_CLEAN_FAILED);
                }
            }
        }
        return Result.success(true);
    }


    /**
     * Slice a single file into knowledge chunks
     *
     * @param fileId ID of the file to be sliced
     * @param sliceConfig configuration for slicing operation
     * @param backEmbedding flag indicating whether to trigger embedding after slicing (0=no, 1=yes)
     * @return DealFileResult containing processing result and task information
     * @throws BusinessException if file type is not supported
     */
    @Transactional
    public DealFileResult sliceFile(Long fileId, SliceConfig sliceConfig, Integer backEmbedding) {
        DealFileResult dealFileResult = new DealFileResult();
        boolean parseSuccess = false;
        FileInfoV2 fileInfoV2 = this.getById(fileId);
        if (fileInfoV2 != null) {
            // Type mapping
            LambdaQueryWrapper<ConfigInfo> wrapper = Wrappers.lambdaQuery(ConfigInfo.class).eq(ConfigInfo::getCategory, "FILE_TYPE_MAPPING").eq(ConfigInfo::getIsValid, 1);
            String type = fileInfoV2.getType();
            if (!StringUtils.isEmpty(type)) {
                wrapper.eq(ConfigInfo::getName, type);
            }

            ConfigInfo configInfo = configInfoService.getOnly(wrapper);
            if (configInfo != null) {
                type = configInfo.getValue();
            }
            // Asynchronous knowledge extraction
            String address = fileInfoV2.getAddress();
            if (!ProjectContent.HTML_FILE_TYPE.equals(type) && address.startsWith("sparkBot")) {
                address = s3UtilClient.getS3Url(address);
            }
            // CBG-RAG file type validation failed
            String source = fileInfoV2.getSource();
            if (ProjectContent.isCbgRagCompatible(source)) {
                if (!ProjectContent.SUPPORTED_FILE_TYPES.contains(type.toLowerCase())) {
                    return dealFileResult;
                }
            }


            try {
                dealFileResult.setTaskId(fileInfoV2.getUuid());
                ExtractKnowledgeTask extractKnowledgeTask = new ExtractKnowledgeTask();
                extractKnowledgeTask.setTaskId(fileInfoV2.getUuid());
                extractKnowledgeTask.setFileId(fileId);
                extractKnowledgeTask.setStatus(0);
                extractKnowledgeTask.setUserId(fileInfoV2.getUid());
                // extractKnowledgeTask.setSliceConfig(JSON.toJSONString(sliceConfig));
                extractKnowledgeTask.setTaskStatus(0);
                Timestamp timestamp = new Timestamp(System.currentTimeMillis());
                extractKnowledgeTask.setCreateTime(timestamp);
                extractKnowledgeTask.setUpdateTime(timestamp);
                extractKnowledgeTaskService.save(extractKnowledgeTask);
                if (backEmbedding == 0) {
                    knowledgeService.knowledgeExtractAsync(type, address, sliceConfig, fileInfoV2, extractKnowledgeTask);
                } else {
                    knowledgeService.knowledgeEmbeddingExtractAsync(type, address, sliceConfig, fileInfoV2, extractKnowledgeTask, this);
                }
                fileInfoV2.setStatus(ProjectContent.FILE_PARSE_DOING);
                parseSuccess = true;
            } catch (Exception e) {
                fileInfoV2.setStatus(ProjectContent.FILE_PARSE_FAILED);
                fileInfoV2.setReason("Knowledge extraction failed:" + e.getMessage());
                dealFileResult.setErrMsg("Knowledge extraction failed:" + e.getMessage());
                log.error("Knowledge extraction and save failed", e);
            }
            fileInfoV2.setSliceConfig(JSON.toJSONString(sliceConfig));
            fileInfoV2.setUpdateTime(new Timestamp(System.currentTimeMillis()));
            this.updateById(fileInfoV2);
        }
        dealFileResult.setParseSuccess(parseSuccess);
        return dealFileResult;
    }


    /**
     * List preview knowledge by page with pagination support
     *
     * @param knowledgeQueryVO query parameters containing file IDs, pagination info, and tag
     * @return PageData containing preview knowledge list and metadata
     * @throws BusinessException if failed to retrieve knowledge from Spark
     */
    public Object listPreviewKnowledgeByPage(KnowledgeQueryVO knowledgeQueryVO) {
        Long spaceId = SpaceInfoUtil.getSpaceId();
        Map<String, Object> extMap = new HashMap<>();
        Map<String, Long> fileIdCountMap = new HashMap<>();
        List<PreviewKnowledgeDto> knowledgeDtoList;

        if (ProjectContent.isSparkRagCompatible(knowledgeQueryVO.getTag())) {
            // Spark file processing
            SparkResult sparkResult = handleSparkPreviewKnowledge(knowledgeQueryVO);
            knowledgeDtoList = sparkResult.knowledgeDtoList;
            fileIdCountMap = sparkResult.fileIdCountMap;
        } else {
            // MongoDB file processing
            MongoResult mongoResult = handleMongoPreviewKnowledge(knowledgeQueryVO, spaceId);
            knowledgeDtoList = mongoResult.knowledgeDtoList;
            extMap = mongoResult.extMap;
        }

        PageData<PreviewKnowledgeDto> pageData = new PageData<>();
        pageData.setPageData(knowledgeDtoList);
        pageData.setExtMap(extMap);
        pageData.setTotalCount((long) knowledgeDtoList.size());
        pageData.setFileSliceCount(fileIdCountMap);
        return pageData;
    }

    private static class SparkResult {
        List<PreviewKnowledgeDto> knowledgeDtoList;
        Map<String, Long> fileIdCountMap;
    }

    private SparkResult handleSparkPreviewKnowledge(KnowledgeQueryVO vo) {
        SparkResult result = new SparkResult();
        result.knowledgeDtoList = new ArrayList<>();
        result.fileIdCountMap = new HashMap<>();

        for (String fileId : vo.getFileIds()) {
            String url = sparkDocUrl + "/openapi/v1/file/chunks?fileId=" + fileId;
            HashMap<String, String> header = new HashMap<>();
            String response = OkHttpUtil.get(url, header);
            JSONObject jsonObject = JSONObject.parseObject(response);
            if (jsonObject.getIntValue("code") != 0) {
                throw new BusinessException(ResponseEnum.REPO_FILE_GET_KNOWLEDGE_FAILED);
            }
            JSONArray data = JSONArray.parseArray(jsonObject.getString("data"));
            for (Object datum : data) {
                result.knowledgeDtoList.add(convertSparkChunk(fileId, (JSONObject) datum));
            }
            result.fileIdCountMap.put(fileId, (long) data.size());
        }

        // Pagination
        result.knowledgeDtoList = result.knowledgeDtoList.stream()
                .skip((long) (vo.getPageNo() - 1) * vo.getPageSize())
                .limit(vo.getPageSize())
                .collect(Collectors.toList());

        return result;
    }

    private PreviewKnowledgeDto convertSparkChunk(String fileId, JSONObject chunk) {
        PreviewKnowledgeDto dto = new PreviewKnowledgeDto();
        String content = chunk.getString("content");
        JSONObject contentJson = new JSONObject();
        contentJson.put("content", content);
        contentJson.put("context", content);
        dto.setCharCount((long) content.length());
        dto.setContent(contentJson);
        dto.setFileId(fileId);
        return dto;
    }

    private static class MongoResult {
        List<PreviewKnowledgeDto> knowledgeDtoList;
        Map<String, Object> extMap;
    }

    private MongoResult handleMongoPreviewKnowledge(KnowledgeQueryVO vo, Long spaceId) {
        MongoResult result = new MongoResult();
        result.knowledgeDtoList = new ArrayList<>();
        result.extMap = new HashMap<>();

        int pageNo = Optional.ofNullable(vo.getPageNo()).orElse(1);
        int pageSize = Optional.ofNullable(vo.getPageSize()).orElse(10);

        // 1. Query FileInfoV2
        List<Long> fileIds = vo.getFileIds().stream().map(Long::valueOf).collect(Collectors.toList());
        List<FileInfoV2> fileInfoList = fileInfoV2Mapper.listByIds(fileIds);
        dataPermissionCheckTool.checkFileInfoListVisible(fileInfoList);

        List<String> fileUuIds = fileInfoList.stream().map(FileInfoV2::getLastUuid).collect(Collectors.toList());
        if (spaceId == null) {
            fileInfoList.forEach(dataPermissionCheckTool::checkFileBelong);
        }

        // 2. Query MySQL (replace MongoDB query)
        // Criteria criteria = Criteria.where("fileId").in(fileUuIds);
        // Query query = new Query(criteria)
        // .with(Sort.by(Sort.Direction.ASC, "fileId"))
        // .with(Sort.by(Sort.Direction.ASC, "_id"))
        // .with(PageRequest.of(pageNo - 1, pageSize));

        // long auditBlockCount = mongoTemplate.count(new Query(Criteria.where("fileId")
        // .in(fileUuIds)
        // .and("content.auditSuggest")
        // .in("block", "review")), PreviewKnowledge.class);
        // result.extMap.put("auditBlockCount", auditBlockCount);

        // List<PreviewKnowledge> knowledges = mongoTemplate.find(query, PreviewKnowledge.class);

        // Use MySQL query to replace MongoDB query
        long auditBlockCount = previewKnowledgeMapper.findByFileIdInAndAuditType(fileUuIds, 1).size();
        result.extMap.put("auditBlockCount", auditBlockCount);

        List<MysqlPreviewKnowledge> knowledges = previewKnowledgeMapper.findByFileIdIn(fileUuIds);

        // Manual pagination
        int start = (pageNo - 1) * pageSize;
        int end = Math.min(start + pageSize, knowledges.size());
        if (start < knowledges.size()) {
            knowledges = knowledges.subList(start, end);
        } else {
            knowledges = new ArrayList<>();
        }

        // 3. Convert results
        if (!CollectionUtils.isEmpty(knowledges)) {
            for (MysqlPreviewKnowledge knowledge : knowledges) {
                result.knowledgeDtoList.add(convertMysqlPreviewKnowledge(knowledge));
            }
        }

        return result;
    }

    private PreviewKnowledgeDto convertMongoKnowledge(PreviewKnowledge knowledge) {
        FileInfoV2 fileInfoV2 = this.getOnly(new QueryWrapper<FileInfoV2>().eq("last_uuid", knowledge.getFileId()));
        String source = fileInfoV2.getSource();

        PreviewKnowledgeDto dto = new PreviewKnowledgeDto();
        if (ProjectContent.isCbgRagCompatible(source)) {
            PreviewKnowledge tmp = new PreviewKnowledge();
            BeanUtils.copyProperties(knowledge, tmp);
            ChunkInfo chunkInfo = tmp.getContent().toJavaObject(ChunkInfo.class);
            JSONObject references = chunkInfo.getReferences();
            if (!CollectionUtils.isEmpty(references)) {
                JSONObject newRef = new JSONObject();
                for (String key : references.keySet()) {
                    newRef.put(key, buildImageReference(references.getString(key)));
                }
                chunkInfo.setReferences(newRef);
                tmp.setContent((JSONObject) JSON.toJSON(chunkInfo));
            }
            BeanUtils.copyProperties(tmp, dto);
        } else {
            BeanUtils.copyProperties(knowledge, dto);
        }
        dto.setFileInfoV2(fileInfoV2);
        return dto;
    }

    private PreviewKnowledgeDto convertMysqlPreviewKnowledge(MysqlPreviewKnowledge knowledge) {
        FileInfoV2 fileInfoV2 = this.getOnly(new QueryWrapper<FileInfoV2>().eq("last_uuid", knowledge.getFileId()));
        String source = fileInfoV2.getSource();

        PreviewKnowledgeDto dto = new PreviewKnowledgeDto();
        if (ProjectContent.isCbgRagCompatible(source)) {
            MysqlPreviewKnowledge tmp = new MysqlPreviewKnowledge();
            BeanUtils.copyProperties(knowledge, tmp);
            ChunkInfo chunkInfo = tmp.getContent().toJavaObject(ChunkInfo.class);
            JSONObject references = chunkInfo.getReferences();
            if (!CollectionUtils.isEmpty(references)) {
                JSONObject newRef = new JSONObject();
                for (String key : references.keySet()) {
                    newRef.put(key, buildImageReference(references.getString(key)));
                }
                chunkInfo.setReferences(newRef);
                tmp.setContent((JSONObject) JSON.toJSON(chunkInfo));
            }
            BeanUtils.copyProperties(tmp, dto);
        } else {
            BeanUtils.copyProperties(knowledge, dto);
        }
        dto.setFileInfoV2(fileInfoV2);
        return dto;
    }

    private JSONObject buildImageReference(String link) {
        JSONObject ref = new JSONObject();
        ref.put("format", "image");
        ref.put("link", link);
        ref.put("suffix", "png");
        ref.put("content", "");
        return ref;
    }


    /**
     * List knowledge by page with pagination and filtering support
     *
     * @param knowledgeQueryVO query parameters containing file IDs, pagination info, content query, and
     *        audit type
     * @return PageData containing knowledge list with pagination metadata
     * @throws BusinessException if file access is denied
     */
    public PageData<KnowledgeDto> listKnowledgeByPage(KnowledgeQueryVO knowledgeQueryVO) {
        Integer pageNo = knowledgeQueryVO.getPageNo();
        Long spaceId = SpaceInfoUtil.getSpaceId();
        if (pageNo == null) {
            pageNo = 1;
        }
        Integer pageSize = knowledgeQueryVO.getPageSize();
        if (pageSize == null) {
            pageSize = 10;
        }
        List<Long> fileIds = knowledgeQueryVO.getFileIds()
                .stream()
                .map(Long::valueOf)
                .collect(Collectors.toList());
        List<String> fileUuIds = new ArrayList<>();
        List<FileInfoV2> fileInfoV2List = fileInfoV2Mapper.listByIds(fileIds);
        for (FileInfoV2 fileInfoV2 : fileInfoV2List) {
            if (null == spaceId) {
                dataPermissionCheckTool.checkFileBelong(fileInfoV2);
            }

            fileUuIds.add(fileInfoV2.getUuid());
        }
        // Use MySQL query to replace MongoDB query
        String queryContent = knowledgeQueryVO.getQuery();
        List<MysqlKnowledge> knowledges;
        if (!StringUtils.isEmpty(queryContent)) {
            knowledges = knowledgeMapper.findByFileIdInAndContentLike(fileUuIds, queryContent);
        } else {
            knowledges = knowledgeMapper.findByFileIdIn(fileUuIds);
        }

        Integer auditType = knowledgeQueryVO.getAuditType();
        if (auditType != null && auditType == 1) {
            knowledges = knowledgeMapper.findByFileIdInAndAuditType(fileUuIds, auditType);
        }

        long count = knowledgeMapper.countByFileIdIn(fileUuIds);
        long auditBlockCount = knowledgeMapper.findByFileIdInAndAuditType(fileUuIds, 1).size();
        Map<String, Object> extMap = new HashMap<>();
        extMap.put("auditBlockCount", auditBlockCount);
        List<KnowledgeDto> knowledgeDtoList = new ArrayList<>();

        // Manual pagination
        int start = (pageNo - 1) * pageSize;
        int end = Math.min(start + pageSize, knowledges.size());
        if (start < knowledges.size()) {
            knowledges = knowledges.subList(start, end);
        } else {
            knowledges = new ArrayList<>();
        }
        if (!CollectionUtils.isEmpty(knowledges)) {
            for (MysqlKnowledge knowledge : knowledges) {
                String fileId = knowledge.getFileId();
                FileInfoV2 fileInfoV2 = this.getOnly(new QueryWrapper<FileInfoV2>().eq("uuid", fileId));
                String source = fileInfoV2.getSource();
                MysqlKnowledge knowledgeTemp = new MysqlKnowledge();
                if (ProjectContent.isCbgRagCompatible(source)) {
                    BeanUtils.copyProperties(knowledge, knowledgeTemp);
                    ChunkInfo chunkInfo = knowledgeTemp.getContent().toJavaObject(ChunkInfo.class);
                    JSONObject references = chunkInfo.getReferences();
                    Set<String> referenceUnusedSet = new HashSet<>();
                    if (!CollectionUtils.isEmpty(references)) {
                        referenceUnusedSet = references.keySet();
                    }
                    if (!CollectionUtils.isEmpty(referenceUnusedSet)) {
                        JSONObject newReference = new JSONObject();
                        for (String referenceUnused : referenceUnusedSet) {
                            buildNewMode(referenceUnused, references, newReference);
                        }
                        chunkInfo.setReferences(newReference);
                        JSONObject updatedContent = (JSONObject) JSON.toJSON(chunkInfo);
                        knowledgeTemp.setContent(updatedContent);
                    }
                }
                KnowledgeDto knowledgeDto = new KnowledgeDto();
                knowledgeDtoList.add(knowledgeDto);
                if (ProjectContent.isCbgRagCompatible(source)) {
                    BeanUtils.copyProperties(knowledgeTemp, knowledgeDto);
                } else {
                    BeanUtils.copyProperties(knowledge, knowledgeDto);
                }
                JSONObject content = knowledgeDto.getContent();
                // Compatible with old data structure
                if (!StringUtils.isEmpty(content.getString("knowledge"))) {
                    content.put("content", content.get("knowledge"));
                }
                knowledgeDto.setFileInfoV2(fileInfoV2);
            }
        }
        PageData<KnowledgeDto> pageData = new PageData<>();
        pageData.setPageData(knowledgeDtoList);
        pageData.setExtMap(extMap);
        pageData.setTotalCount(count);
        return pageData;
    }

    private static void buildNewMode(String referenceUnused, JSONObject references, JSONObject newReference) {
        String link = references.getString(referenceUnused);
        JSONObject newReferenceV = new JSONObject();
        newReferenceV.put("format", "image");
        newReferenceV.put("link", link);
        newReferenceV.put("suffix", "png");
        newReferenceV.put("content", "");
        // Replace original value with new nested object
        newReference.put(referenceUnused, newReferenceV);
    }

    /**
     * Embed files to create vector representations for knowledge retrieval
     *
     * @param sliceFileVO file embedding parameters containing file IDs and configuration
     * @param request HTTP servlet request for authentication and context
     * @throws BusinessException if embedding process fails or file access is denied
     */
    public void embeddingFiles(DealFileVO sliceFileVO, HttpServletRequest request) {
        if (ProjectContent.isSparkRagCompatible(sliceFileVO.getTag())) {
            try {
                String embeddingUrl = sparkDocUrl + "/openapi/v1/file/embedding";
                HashMap<String, String> header = chatFileHttpClient.getSignForXinghuoDs();
                Map<String, Object> params = new HashMap<>();
                List<String> fileIds = sliceFileVO.getSparkFiles().stream().map(SparkFileVo::getFileId).collect(Collectors.toList());
                params.put("fileIds", String.join(",", fileIds));
                String embeddingRsp = OkHttpUtil.postMultipart(embeddingUrl, header, null, params, null);
                JSONObject jsonObject = JSONObject.parseObject(embeddingRsp);
                if (jsonObject.getIntValue("code") != 0) {
                    throw new BusinessException(ResponseEnum.REPO_FILE_EMBEDDING_FAILED);
                } else {
                    // 调用文档绑定

                    JSONObject bindParams = new JSONObject();
                    bindParams.put("datasetId", sliceFileVO.getRepoId());
                    bindParams.put("files", sliceFileVO.getSparkFiles());
                    HashMap<String, String> bindHeader = new HashMap<>();
                    String authorization = request.getHeader("Authorization");
                    if (StringUtils.isNotBlank(authorization)) {
                        bindHeader.put("Authorization", authorization);
                    }
                    String bindRsp = OkHttpUtil.post(apiUrl.getXinghuoDatasetFileUrl(), bindHeader, bindParams.toJSONString());
                    JSONObject bindObject = JSONObject.parseObject(bindRsp);
                    if (bindObject.getIntValue("code") != 0) {
                        throw new BusinessException(ResponseEnum.REPO_FILE_EMBEDDING_FAILED);
                    }
                }
            } catch (Exception ex) {
                throw new BusinessException(ResponseEnum.REPO_FILE_EMBEDDING_FAILED);
            }
        } else {
            List<Long> fileIds = sliceFileVO.getFileIds()
                    .stream()
                    .map(Long::valueOf) // Convert String to Long
                    .collect(Collectors.toList());
            if (!CollectionUtils.isEmpty(fileIds)) {
                ExecutorService executorService = Executors.newFixedThreadPool(fileIds.size());
                for (Long fileId : fileIds) {
                    FileInfoV2 fileInfo = this.getById(fileId);
                    if (fileInfo != null) {
                        if (sliceFileVO.getIsBackTask() == null) {
                            Long spaceId = SpaceInfoUtil.getSpaceId();
                            if (null == spaceId) {
                                dataPermissionCheckTool.checkFileBelong(fileInfo);
                            }
                        }
                    }
                    FileDirectoryTree fileDirectoryTree = fileDirectoryTreeService.getOnly(Wrappers.lambdaQuery(FileDirectoryTree.class)
                            .eq(FileDirectoryTree::getAppId, fileInfo.getRepoId())
                            .eq(FileDirectoryTree::getFileId, fileId));
                    fileDirectoryTree.setStatus(1);
                    fileDirectoryTreeMapper.updateById(fileDirectoryTree);
                    executorService.execute(() -> {
                        int count = 0;
                        while (true) {
                            FileInfoV2 fileInfoV2 = fileInfoV2Mapper.selectById(fileId);
                            // if(Objects.equals(fileInfoV2.getStatus(), ProjectContent.FILE_EMBEDDING_SUCCESSED)){
                            // if (count > 10) {
                            // break;
                            // }
                            // count++;
                            // // Wait 3 seconds before checking again
                            // try {
                            // Thread.sleep(3000);
                            // } catch (InterruptedException e) {
                            // throw new RuntimeException(e);
                            // }
                            // continue;
                            // }
                            if (Objects.equals(fileInfoV2.getStatus(), ProjectContent.FILE_PARSE_FAILED)) {
                                break;
                            }
                            if (Objects.equals(fileInfoV2.getStatus(), ProjectContent.FILE_PARSE_SUCCESSED)
                                    || Objects.equals(fileInfoV2.getStatus(), ProjectContent.FILE_EMBEDDING_DOING)
                                    || Objects.equals(fileInfoV2.getStatus(), ProjectContent.FILE_EMBEDDING_FAILED)
                                    || Objects.equals(fileInfoV2.getStatus(), ProjectContent.FILE_EMBEDDING_SUCCESSED)) {
                                saveTaskAndUpdateFileStatus(fileId);
                                new EmbeddingFileTask(this, fileId, fileInfoV2.getSpaceId()).run();
                                break;
                            }
                        }
                    });
                }
            }
        }
    }

    /**
     * Extract cookies from HTTP request and format them as cookie string
     *
     * @param request HTTP servlet request containing cookies
     * @return formatted cookie string for HTTP headers, empty string if no cookies
     */
    public static String getRequestCookies(HttpServletRequest request) {
        Cookie[] cookies = request.getCookies();
        if (cookies != null) {
            return Arrays.stream(cookies)
                    .map(cookie -> cookie.getName() + "=" + cookie.getValue())
                    .collect(Collectors.joining("; "));
        }
        return "";
    }

    /**
     * Embed a single file to create vector representations
     *
     * @param fileId ID of the file to be embedded
     * @param spaceId space ID for resource management and billing
     * @return DealFileResult containing embedding result and failure count
     * @throws BusinessException if resource quota is exceeded
     */
    @Transactional
    public DealFileResult embeddingFile(Long fileId, Long spaceId) {
        DealFileResult dealFileResult = new DealFileResult();
        boolean embeddingSuccess = false;
        FileInfoV2 fileInfoV2 = this.getById(fileId);
        ExtractKnowledgeTask extractKnowledgeTask = extractKnowledgeTaskService.getOnly(Wrappers.lambdaQuery(ExtractKnowledgeTask.class)
                .eq(ExtractKnowledgeTask::getFileId, fileInfoV2.getId())
                .eq(ExtractKnowledgeTask::getTaskStatus, 2));
        try {
            Integer failedKnowledgeCount = knowledgeService.embeddingKnowledgeAndStorage(fileId);
            dealFileResult.setFailedCount(failedKnowledgeCount);
            embeddingSuccess = true;
            fileInfoV2.setStatus(ProjectContent.FILE_EMBEDDING_SUCCESSED);
            fileInfoV2.setCurrentSliceConfig(fileInfoV2.getSliceConfig());
            fileInfoV2.setEnabled(1);
            extractKnowledgeTask.setStatus(1);
        } catch (Exception e) {
            log.error("File embedding failed, message:", e);
            fileInfoV2.setStatus(ProjectContent.FILE_EMBEDDING_FAILED);
            fileInfoV2.setReason("File embedding failed:" + e.getMessage());
            extractKnowledgeTask.setStatus(2);
            extractKnowledgeTask.setReason("File embedding failed:" + e.getMessage());
            dealFileResult.setErrMsg("File embedding failed:" + e.getMessage());
        }
        fileInfoV2.setUpdateTime(new Timestamp(System.currentTimeMillis()));
        extractKnowledgeTask.setUpdateTime(new Timestamp(System.currentTimeMillis()));
        extractKnowledgeTask.setTaskStatus(3);
        if (ProjectContent.isCbgRagCompatible(fileInfoV2.getSource())) {
            fileInfoV2.setUuid(fileInfoV2.getLastUuid());
        }
        this.updateById(fileInfoV2);
        extractKnowledgeTaskService.updateById(extractKnowledgeTask);
        dealFileResult.setParseSuccess(embeddingSuccess);
        // Add billing metrics
        if (!addFileCost(fileInfoV2.getUid(), fileInfoV2.getSize(), spaceId)) {
            throw new BusinessException(ResponseEnum.REPO_FILE_SIZE_LIMITED);
        }
        return dealFileResult;
    }

    /**
     * Execute background embedding tasks for files
     *
     * @param sliceFileVO file embedding parameters containing file IDs and configuration
     * @param request HTTP servlet request for authentication and context
     * @throws BusinessException if embedding process fails or file access is denied
     */
    public void embeddingBack(DealFileVO sliceFileVO, HttpServletRequest request) {
        if (ProjectContent.isSparkRagCompatible(sliceFileVO.getTag())) {
            try {
                String embeddingUrl = sparkDocUrl + "/openapi/v1/file/embedding";
                HashMap<String, String> header = chatFileHttpClient.getSignForXinghuoDs();
                Map<String, Object> params = new HashMap<>();
                List<String> fileIds = sliceFileVO.getSparkFiles().stream().map(SparkFileVo::getFileId).collect(Collectors.toList());
                params.put("fileIds", String.join(",", fileIds));
                String embeddingRsp = OkHttpUtil.postMultipart(embeddingUrl, header, null, params, null);
                JSONObject jsonObject = JSONObject.parseObject(embeddingRsp);
                if (jsonObject.getIntValue("code") != 0) {
                    throw new BusinessException(ResponseEnum.REPO_FILE_EMBEDDING_FAILED);
                } else {
                    // 调用文档绑定
                    JSONObject bindParams = new JSONObject();
                    bindParams.put("datasetId", sliceFileVO.getRepoId());
                    bindParams.put("files", sliceFileVO.getSparkFiles());
                    HashMap<String, String> bindHeader = new HashMap<>();
                    String authorization = request.getHeader("Authorization");
                    if (StringUtils.isNotBlank(authorization)) {
                        bindHeader.put("Authorization", authorization);
                    }
                    String bindRsp = OkHttpUtil.post(apiUrl.getXinghuoDatasetFileUrl(), bindHeader, bindParams.toJSONString());
                    JSONObject bindObject = JSONObject.parseObject(bindRsp);
                    if (bindObject.getIntValue("code") != 0) {
                        throw new BusinessException(ResponseEnum.REPO_FILE_EMBEDDING_FAILED);
                    }
                }
            } catch (Exception ex) {
                throw new BusinessException(ResponseEnum.REPO_FILE_EMBEDDING_FAILED);
            }
        } else {
            List<Long> fileIds = sliceFileVO.getFileIds()
                    .stream()
                    .map(Long::valueOf) // Convert String to Long
                    .collect(Collectors.toList());
            if (!CollectionUtils.isEmpty(fileIds)) {
                ExecutorService executorService = Executors.newFixedThreadPool(fileIds.size());
                for (Long fileId : fileIds) {
                    FileInfoV2 fileInfo = this.getById(fileId);
                    if (fileInfo != null) {
                        if (sliceFileVO.getIsBackTask() == null) {
                            dataPermissionCheckTool.checkFileBelong(fileInfo);
                        }
                    }
                    // Set file visibility
                    FileDirectoryTree fileDirectoryTree = fileDirectoryTreeService.getOnly(Wrappers.lambdaQuery(FileDirectoryTree.class).eq(FileDirectoryTree::getAppId, fileInfo.getRepoId()).eq(FileDirectoryTree::getFileId, fileId));
                    fileDirectoryTree.setStatus(1);
                    fileDirectoryTreeMapper.updateById(fileDirectoryTree);
                    executorService.execute(() -> {
                        int count = 0;
                        while (true) {
                            FileInfoV2 fileInfoV2 = fileInfoV2Mapper.selectById(fileId);
                            // if(Objects.equals(fileInfoV2.getStatus(), ProjectContent.FILE_EMBEDDING_SUCCESSED)){
                            // if (count > 10) {
                            // break;
                            // }
                            // count++;
                            // // Wait 3 seconds before checking again
                            // try {
                            // Thread.sleep(3000);
                            // } catch (InterruptedException e) {
                            // throw new RuntimeException(e);
                            // }
                            // continue;
                            // }
                            if (Objects.equals(fileInfoV2.getStatus(), ProjectContent.FILE_PARSE_FAILED)) {
                                break;
                            }
                            if (Objects.equals(fileInfoV2.getStatus(), ProjectContent.FILE_PARSE_SUCCESSED)
                                    || Objects.equals(fileInfoV2.getStatus(), ProjectContent.FILE_EMBEDDING_DOING)
                                    || Objects.equals(fileInfoV2.getStatus(), ProjectContent.FILE_EMBEDDING_FAILED)
                                    || Objects.equals(fileInfoV2.getStatus(), ProjectContent.FILE_EMBEDDING_SUCCESSED)) {
                                saveTaskAndUpdateFileStatus(fileId);
                                new EmbeddingFileTask(this, fileId, fileInfoV2.getSpaceId()).run();
                                break;
                            }
                        }
                    });
                }
            }
        }
    }

    /**
     * Retry failed file processing operations (parsing or embedding)
     *
     * @param sliceFileVO retry parameters containing file IDs and slice configuration
     * @param request HTTP servlet request for authentication and context
     * @throws InterruptedException if thread execution is interrupted
     * @throws ExecutionException if execution fails
     * @throws BusinessException if files are currently being processed or configuration is invalid
     */
    public void retry(DealFileVO sliceFileVO, HttpServletRequest request) throws InterruptedException, ExecutionException {
        Long spaceId = SpaceInfoUtil.getSpaceId();

        // 1) Spark: Retry with "custom splitting"
        if (ProjectContent.isSparkRagCompatible(sliceFileVO.getTag())) {
            retrySparkSplitIfNeeded(sliceFileVO);
            return;
        }

        // 2) Non-Spark: Handle "parse failure retry (including auto-embedding) / embedding failure retry"
        // separately
        List<Long> fileIds = sliceFileVO.getFileIds().stream().map(Long::valueOf).collect(Collectors.toList());
        if (CollectionUtils.isEmpty(fileIds))
            return;

        ExecutorService pool = Executors.newFixedThreadPool(fileIds.size());
        List<FileInfoV2> files = fileInfoV2Mapper.listByIds(fileIds);
        for (FileInfoV2 f : files) {
            if (Objects.equals(f.getStatus(), ProjectContent.FILE_PARSE_FAILED)) {
                handleParseFailedRetry(f, sliceFileVO, spaceId, pool);
            } else if (Objects.equals(f.getStatus(), ProjectContent.FILE_EMBEDDING_FAILED)) {
                handleEmbeddingFailedRetry(f, sliceFileVO, spaceId, pool);
            }
            // Other statuses: No processing (consistent with original logic)
        }
        pool.shutdown();
    }
    /* ======================== Private Methods ======================== */

    /**
     * Spark split retry (triggered only when sliceConfig.type == 1), throws REPO_FILE_SLICE_FAILED on
     * failure
     *
     * @param vo deal file parameters containing slice configuration
     * @throws BusinessException if split operation fails
     */
    private void retrySparkSplitIfNeeded(DealFileVO vo) {
        if (!Integer.valueOf(1).equals(vo.getSliceConfig().getType()))
            return;

        HashMap<String, String> header = new HashMap<>();
        String url = sparkDocUrl + "/openapi/v1/file/split";
        JSONObject params = new JSONObject();
        params.put("fileIds", vo.getFileIds());
        params.put("isSplitDefault", false);
        params.put("splitType", "wiki");

        // Custom separators (base64)
        JSONObject wiki = new JSONObject();
        List<String> sep = Optional.ofNullable(vo.getSliceConfig().getSeperator()).orElseGet(ArrayList::new);
        List<String> sep64 = new ArrayList<>();
        for (String s : sep)
            sep64.add(Base64.getEncoder().encodeToString(s.getBytes()));
        wiki.put("chunkSeparators", sep64);
        wiki.put("chunkSize", vo.getSliceConfig().getLengthRange().get(1));
        wiki.put("minChunkSize", vo.getSliceConfig().getLengthRange().get(0));
        params.put("wikiSplitExtends", wiki);

        String resp = OkHttpUtil.post(url, header, params.toJSONString());
        JSONObject obj = JSONObject.parseObject(resp);
        if (obj.getIntValue("code") != 0) {
            throw new BusinessException(ResponseEnum.REPO_FILE_SLICE_FAILED);
        }
    }

    /**
     * Parse failure retry: Reset/write directory tree → Validate range/separators → Set status to
     * parsing → Execute slicing asynchronously (auto-trigger subsequent embedding)
     *
     * @param file file information object
     * @param vo deal file parameters
     * @param spaceId space ID for permission checking
     * @param pool thread pool for async execution
     * @throws BusinessException if file is currently being parsed or range is invalid
     */
    private void handleParseFailedRetry(FileInfoV2 file, DealFileVO vo, Long spaceId, ExecutorService pool) {
        // Auto separator fallback
        ensureSeparatorDefault(vo.getSliceConfig());

        // Permission validation (consistent with original logic: validate only when spaceId is null)
        if (spaceId == null)
            dataPermissionCheckTool.checkFileBelong(file);

        // Prohibit retry if currently parsing
        if (Objects.equals(file.getStatus(), ProjectContent.FILE_PARSE_DOING)) {
            throw new BusinessException(ResponseEnum.REPO_KNOWLEDGE_SPLITTING);
        }

        // AIUI slice range validation
        validateSliceRangeForAiui(vo.getSliceConfig(), file.getSource());

        // Ensure directory tree existence
        ensureFileDirectoryTree(file);

        // Update slice configuration & set status to parsing
        SliceConfig sc = vo.getSliceConfig();
        file.setSliceConfig(JSON.toJSONString(sc));
        file.setCurrentSliceConfig(JSON.toJSONString(sc));
        file.setStatus(ProjectContent.FILE_PARSE_DOING);
        fileInfoV2Mapper.updateById(file);

        // Execute slicing task asynchronously (with backEmbedding flag set to 1)
        pool.execute(() -> new SliceFileTask(this, file.getId(), sc, 1).call());
    }

    /**
     * Embedding failure retry: Set to parse success → Wait for embeddable → Save task/make directory
     * visible → Trigger embedding task
     *
     * @param file file information object
     * @param vo deal file parameters
     * @param spaceId space ID for permission checking
     * @param pool thread pool for async execution
     */
    private void handleEmbeddingFailedRetry(FileInfoV2 file, DealFileVO vo, Long spaceId, ExecutorService pool) {
        // Only validate file ownership during foreground retry (consistent with original logic)
        if (vo.getIsBackTask() == null && spaceId == null) {
            dataPermissionCheckTool.checkFileBelong(file);
        }

        // Set status to "parse success" to enter embedding process
        file.setStatus(ProjectContent.FILE_PARSE_SUCCESSED);
        fileInfoV2Mapper.updateById(file);

        pool.execute(() -> {
            while (true) {
                FileInfoV2 latest = fileInfoV2Mapper.selectById(file.getId());
                if (Objects.equals(latest.getStatus(), ProjectContent.FILE_PARSE_FAILED))
                    break;
                if (Objects.equals(latest.getStatus(), ProjectContent.FILE_PARSE_SUCCESSED)) {
                    // Save task and update file status to embedding_doing
                    saveTaskAndUpdateFileStatus(file.getId());
                    // Make directory visible
                    FileDirectoryTree tree = fileDirectoryTreeService.getOnly(
                            Wrappers.lambdaQuery(FileDirectoryTree.class)
                                    .eq(FileDirectoryTree::getAppId, file.getRepoId())
                                    .eq(FileDirectoryTree::getFileId, file.getId()));
                    if (tree != null) {
                        tree.setStatus(1);
                        fileDirectoryTreeMapper.updateById(tree);
                    }
                    // Trigger embedding
                    new EmbeddingFileTask(this, file.getId(), file.getSpaceId()).run();
                    break;
                }
            }
        });
    }

    /**
     * Universal for non-Spark/AIUI: Use \n as fallback when empty
     *
     * @param sc slice configuration to check and update
     */
    private void ensureSeparatorDefault(SliceConfig sc) {
        if (sc == null)
            return;
        List<String> sep = sc.getSeperator();
        if (sep == null || sep.isEmpty() || StringUtils.isEmpty(sep.get(0))) {
            sc.setSeperator(Collections.singletonList("\n"));
        }
    }

    /**
     * AIUI slice range restriction ([16, 1024]), skip for other sources
     *
     * @param sc slice configuration to validate
     * @param source file source type
     * @throws BusinessException if range is invalid for AIUI source
     */
    private void validateSliceRangeForAiui(SliceConfig sc, String source) {
        if (sc == null || !ProjectContent.isAiuiRagCompatible(source))
            return;
        if (sc.getLengthRange() != null) {
            Integer min = sc.getLengthRange().get(0);
            Integer max = sc.getLengthRange().get(1);
            if (min < 16 || max > 1024) {
                throw new BusinessException(ResponseEnum.REPO_FILE_SLICE_RANGE_16_1024);
            }
        }
    }

    /**
     * Ensure directory tree record exists (insert one if not exists)
     *
     * @param file file information object containing repo and file details
     */
    private void ensureFileDirectoryTree(FileInfoV2 file) {
        FileDirectoryTree tree = fileDirectoryTreeService.getOnly(
                Wrappers.lambdaQuery(FileDirectoryTree.class)
                        .eq(FileDirectoryTree::getAppId, file.getRepoId())
                        .eq(FileDirectoryTree::getFileId, file.getId()));
        if (tree == null) {
            tree = new FileDirectoryTree();
            tree.setIsFile(1);
            tree.setName(file.getName());
            tree.setAppId(file.getRepoId().toString());
            tree.setParentId(file.getPid());
            tree.setFileId(file.getId());
            tree.setCreateTime(LocalDateTime.now());
            fileDirectoryTreeMapper.insert(tree);
        }
    }

    /**
     * Save extraction task and update file status to embedding in progress
     *
     * @param fileId ID of the file to process
     */
    @Transactional
    public void saveTaskAndUpdateFileStatus(Long fileId) {
        FileInfoV2 fileInfoV2 = this.getById(fileId);
        if (fileInfoV2 != null) {
            // If file has not been successfully parsed, embedding is not allowed
            if (fileInfoV2.getStatus() < ProjectContent.FILE_PARSE_SUCCESSED) {
                return;
            }
            fileInfoV2.setStatus(ProjectContent.FILE_EMBEDDING_DOING);
            fileInfoV2.setUpdateTime(new Timestamp(System.currentTimeMillis()));
            this.updateById(fileInfoV2);
            // Update task status
            ExtractKnowledgeTask localExtractKnowledgeTask = extractKnowledgeTaskService.getOnly(Wrappers.lambdaQuery(ExtractKnowledgeTask.class)
                    .eq(ExtractKnowledgeTask::getFileId, fileInfoV2.getId())
                    .eq(ExtractKnowledgeTask::getTaskStatus, 2));
            if (localExtractKnowledgeTask == null) {
                ExtractKnowledgeTask extractKnowledgeTask = new ExtractKnowledgeTask();
                extractKnowledgeTask.setTaskId(fileInfoV2.getUuid());
                extractKnowledgeTask.setFileId(fileId);
                extractKnowledgeTask.setStatus(0);
                extractKnowledgeTask.setUserId(UserInfoManagerHandler.getUserId());
                extractKnowledgeTask.setTaskStatus(2);
                Timestamp timestamp = new Timestamp(System.currentTimeMillis());
                extractKnowledgeTask.setCreateTime(timestamp);
                extractKnowledgeTask.setUpdateTime(timestamp);
                extractKnowledgeTaskService.save(extractKnowledgeTask);
            }
        }
    }


    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void updateFileInfoV2Status(FileInfoV2 fileInfoV2) {
        fileInfoV2.setStatus(ProjectContent.FILE_EMBEDDING_DOING);
        fileInfoV2.setUpdateTime(new Timestamp(System.currentTimeMillis()));
        this.updateById(fileInfoV2);
    }


    @Transactional
    public void continueSliceOrEmbeddingFile() {
        log.info("Starting to rerun knowledge base embedding tasks");
        List<ExtractKnowledgeTask> tasks = extractKnowledgeTaskService.list(
                Wrappers.lambdaQuery(ExtractKnowledgeTask.class).eq(ExtractKnowledgeTask::getStatus, 0).isNotNull(ExtractKnowledgeTask::getTaskStatus));
        if (CollectionUtils.isEmpty(tasks)) {
            return;
        }
        // Get tasks that need to be rerun
        List<ExtractKnowledgeTask> distinctTasks = tasks.stream()
                .collect(Collectors.toMap(
                        ExtractKnowledgeTask::getFileId,
                        Function.identity(),
                        (existing, replacement) -> existing.getCreateTime().after(replacement.getCreateTime()) ? existing : replacement))
                .values()
                .stream()
                .collect(Collectors.toList());
        // Rerun parsing and embedding tasks
        // List<ExtractKnowledgeTask> spliceTask = distinctTasks.stream().filter(item -> item.getTag() ==
        // 1).collect(Collectors.toList());
        // if(!CollectionUtils.isEmpty(spliceTask)){
        // ObjectMapper objectMapper = new ObjectMapper();
        // for (ExtractKnowledgeTask extractKnowledgeTask : spliceTask) {
        // String sliceConfigJson = extractKnowledgeTask.getSliceConfig();
        // SliceConfig sliceConfig = objectMapper.readValue(sliceConfigJson, SliceConfig.class);
        // sliceFile(extractKnowledgeTask.getFileId(),sliceConfig);
        // }
        // }
        // Rerun embedding tasks
        List<ExtractKnowledgeTask> embeddingTask = distinctTasks.stream().filter(item -> item.getTaskStatus() == 2).collect(Collectors.toList());
        log.info("Number of knowledge base tasks that need to be rerun: {}", embeddingTask.size());
        if (!CollectionUtils.isEmpty(embeddingTask)) {
            for (ExtractKnowledgeTask extractKnowledgeTask : embeddingTask) {
                FileInfoV2 fileInfoV2 = this.getById(extractKnowledgeTask.getFileId());
                DealFileVO dealFileVO = new DealFileVO();
                dealFileVO.setFileIds(Arrays.asList(extractKnowledgeTask.getFileId().toString()));
                dealFileVO.setTag(fileInfoV2.getSource());
                dealFileVO.setRepoId(fileInfoV2.getRepoId());
                dealFileVO.setIsBackTask(1);
                embeddingFiles(dealFileVO, null);
            }
        }
        log.info("Knowledge base embedding task rerun completed");

    }

    /**
     * Get indexing status of files
     *
     * @param sliceFileVO parameters containing file IDs and tag information
     * @return list of FileInfoV2Dto with indexing status and paragraph counts
     * @throws BusinessException if file access is denied
     */
    public List<FileInfoV2Dto> getIndexingStatus(DealFileVO sliceFileVO) {
        List<FileInfoV2Dto> fileInfoV2List = new ArrayList<>();
        Long spaceId = SpaceInfoUtil.getSpaceId();

        if (ProjectContent.isSparkRagCompatible(sliceFileVO.getTag())) {
            for (String fileId : sliceFileVO.getFileIds()) {
                FileInfoV2Dto fileInfoV2Dto = new FileInfoV2Dto();
                if (sliceFileVO.getIndexType() == null || sliceFileVO.getIndexType().equals(0)) {
                    fileInfoV2Dto.setStatus(2);
                } else {
                    fileInfoV2Dto.setStatus(5);
                }
                fileInfoV2Dto.setUuid(fileId);
                fileInfoV2List.add(fileInfoV2Dto);
            }
            return fileInfoV2List;
        } else {
            List<Long> fileIds = sliceFileVO.getFileIds()
                    .stream()
                    .map(Long::valueOf) // Convert String to Long
                    .collect(Collectors.toList());
            for (Long fileId : fileIds) {
                FileInfoV2 fileInfoV2 = this.getById(fileId);
                if (null == spaceId) {
                    dataPermissionCheckTool.checkFileBelong(fileInfoV2);
                }


                FileInfoV2Dto fileInfoV2Dto = new FileInfoV2Dto();
                BeanUtils.copyProperties(fileInfoV2, fileInfoV2Dto);
                // Criteria criteria = Criteria.where("fileId").is(fileInfoV2.getUuid());
                // long count = mongoTemplate.count(new Query(criteria), Knowledge.class);
                long count = knowledgeMapper.countByFileId(fileInfoV2.getUuid());
                fileInfoV2Dto.setParagraphCount(count);
                fileInfoV2List.add(fileInfoV2Dto);
            }
        }
        return fileInfoV2List;
    }

    /**
     * Get file summary including knowledge count and character count
     *
     * @param dealFileVO parameters containing file IDs, repository ID, and tag information
     * @param request HTTP servlet request for authentication
     * @return FileSummary containing file information and knowledge statistics
     * @throws BusinessException if file access is denied
     */
    public FileSummary getFileSummary(DealFileVO dealFileVO, HttpServletRequest request) {
        FileSummary fileSummary = new FileSummary();
        Long spaceId = SpaceInfoUtil.getSpaceId();
        if (ProjectContent.isSparkRagCompatible(dealFileVO.getTag())) {
            List<RelatedDocDto> sparkCbgResponse = new ArrayList<RelatedDocDto>();
            RelatedDocDto fileInfo = new RelatedDocDto();
            String url = apiUrl.getDatasetFileUrl().concat("?datasetId=").concat(dealFileVO.getRepoId().toString());
            log.info("getFileSummary request url:{}", url);

            Map<String, String> header = new HashMap<>();
            String authorization = request.getHeader("Authorization");
            if (StringUtils.isNotBlank(authorization)) {
                header.put("Authorization", authorization);
            }
            String resp = OkHttpUtil.get(url, header);
            JSONObject respObject = JSON.parseObject(resp);
            log.info("getFileSummary response data: {}", resp);

            if (respObject.getBooleanValue("flag") && respObject.getInteger("code") == 0) {
                sparkCbgResponse = JSON.parseArray(respObject.getString("data"), RelatedDocDto.class);
            }
            long paraCount = 0L;
            if (!CollectionUtils.isEmpty(sparkCbgResponse)) {
                for (RelatedDocDto relatedDocDto : sparkCbgResponse) {
                    if (relatedDocDto.getDatasetIndex().equals(dealFileVO.getFileIds().get(0))) {
                        fileInfo = relatedDocDto;
                        paraCount += fileInfo.getParaCount();
                        FileInfoV2 fileInfoV2 = new FileInfoV2();
                        fileInfoV2.setCharCount((long) fileInfo.getCharCount());
                        fileInfoV2.setName(fileInfo.getName());
                        fileSummary.setFileInfoV2(fileInfoV2);
                    }
                }
                fileSummary.setKnowledgeCount(paraCount);
            }
        } else {
            List<Long> fileIds = dealFileVO.getFileIds()
                    .stream()
                    .map(Long::valueOf) // Convert String to Long
                    .collect(Collectors.toList());
            List<FileInfoV2> fileInfoV2List = fileInfoV2Mapper.listByIds(fileIds);
            List<String> fileUuids = new ArrayList<>();
            for (FileInfoV2 fileInfoV2 : fileInfoV2List) {
                if (null == spaceId) {
                    dataPermissionCheckTool.checkFileBelong(fileInfoV2);
                }

                fileUuids.add(fileInfoV2.getUuid());
            }

            // Double v = myMongoService.sumField(Knowledge.class, "charCount",
            // Criteria.where("fileId").in(fileUuids));
            // fileSummary.setKnowledgeTotalLength(v.longValue());
            // Double dialogHitCount = myMongoService.sumField(Knowledge.class, "dialogHitCount",
            // Criteria.where("fileId").in(fileUuids));
            // fileSummary.setHitCount(dialogHitCount.longValue());
            // long count = mongoTemplate.count(new Query(Criteria.where("fileId").in(fileUuids)),
            // Knowledge.class);
            long count = knowledgeMapper.countByFileIdIn(fileUuids);
            if (count == 0) {
                fileSummary.setKnowledgeCount(0L);
                fileSummary.setKnowledgeAvgLength(0L);
            } else {
                fileSummary.setKnowledgeCount(count);
                // fileSummary.setKnowledgeAvgLength(v.longValue() / count);
            }
            FileInfoV2 fileInfoV2 = this.getById(fileIds.get(0));
            FileDirectoryTree fileDirectoryTree = fileDirectoryTreeService.getOnly(Wrappers.lambdaQuery(FileDirectoryTree.class).eq(FileDirectoryTree::getFileId, fileInfoV2.getId()));
            fileSummary.setFileDirectoryTreeId(fileDirectoryTree.getId());
            fileSummary.setFileInfoV2(fileInfoV2);
            String currentSliceConfig = fileInfoV2.getCurrentSliceConfig();
            if (!StringUtils.isEmpty(currentSliceConfig)) {
                SliceConfig sliceConfig = JSONObject.parseObject(currentSliceConfig, SliceConfig.class);
                fileSummary.setSliceType(sliceConfig.getType());
                fileSummary.setSeperator(sliceConfig.getSeperator());
                fileSummary.setLengthRange(sliceConfig.getLengthRange());
            }
        }

        return fileSummary;
    }

    /**
     * Query file list with pagination support
     *
     * @param repoId repository ID
     * @param parentId parent directory ID
     * @param pageNo page number for pagination
     * @param pageSize number of items per page
     * @param tag file source tag (Spark RAG or others)
     * @param request HTTP servlet request for authentication
     * @param isRepoPage flag indicating if this is a repository page query
     * @return PageData containing file directory tree list
     * @throws BusinessException if required parameters are missing or repository access is denied
     */
    public Object queryFileList(Long repoId, Long parentId, Integer pageNo, Integer pageSize, String tag, HttpServletRequest request, Integer isRepoPage) {
        if ((repoId == null || parentId == null) && tag.isEmpty()) {
            throw new BusinessException(ResponseEnum.REPO_SOME_IDS_MUST_INPUT);
        }

        PageData<FileDirectoryTreeDto> pageData = new PageData<>();
        List<FileDirectoryTreeDto> fileDirectoryTreeDtoList = new ArrayList<>();
        List<RelatedDocDto> sparkCbgResponse = new ArrayList<RelatedDocDto>();
        Long modelListCount = (long) 0;
        if (ProjectContent.isSparkRagCompatible(tag)) {
            String url = apiUrl.getDatasetFileUrl() + "?datasetId=".concat(repoId.toString());
            log.info("sparkDeskRepoFileGet request url:{}", url);
            Map<String, String> header = new HashMap<>();
            String authorization = request.getHeader("Authorization");
            if (StringUtils.isNotBlank(authorization)) {
                header.put("Authorization", authorization);
            }
            String resp = OkHttpUtil.get(url, header);
            JSONObject respObject = JSON.parseObject(resp);
            log.info("sparkDeskRepoFileGet response data: {}", resp);

            if (respObject.getBooleanValue("flag") && respObject.getInteger("code") == 0) {
                sparkCbgResponse = JSON.parseArray(respObject.getString("data"), RelatedDocDto.class);
            }

            if (!CollectionUtils.isEmpty(sparkCbgResponse)) {
                modelListCount = (long) sparkCbgResponse.size();

                for (RelatedDocDto relatedDocDto : sparkCbgResponse) {
                    FileDirectoryTreeDto fileDirectoryTreeDto = new FileDirectoryTreeDto();
                    fileDirectoryTreeDtoList.add(fileDirectoryTreeDto);
                    fileDirectoryTreeDto.setId(relatedDocDto.getId());
                    fileDirectoryTreeDto.setName(relatedDocDto.getName());
                    fileDirectoryTreeDto.setParentId((long) -1);
                    fileDirectoryTreeDto.setIsFile(1);
                    fileDirectoryTreeDto.setAppId(repoId.toString());
                    fileDirectoryTreeDto.setFileId(relatedDocDto.getId());
                    fileDirectoryTreeDto.setCreateTime(relatedDocDto.getCreateTime());

                    FileInfoV2 fileInfoV2 = new FileInfoV2();
                    fileInfoV2.setId(relatedDocDto.getId());
                    fileInfoV2.setName(relatedDocDto.getName());
                    fileInfoV2.setCharCount(relatedDocDto.getCharCount().longValue());
                    fileInfoV2.setStatus(relatedDocDto.getStatus());
                    fileInfoV2.setEnabled(1);
                    fileInfoV2.setUuid(relatedDocDto.getDatasetIndex());
                    fileDirectoryTreeDto.setFileInfoV2(fileInfoV2);
                }
            }
        } else {
            Repo repo = repoService.getById(repoId);
            if (repo == null) {
                throw new BusinessException(ResponseEnum.REPO_NOT_FOUND);
            }
            dataPermissionCheckTool.checkRepoBelong(repo);
            dataPermissionCheckTool.checkRepoVisible(repo);
            List<FileDirectoryTree> modelListLinkFileInfoV2 = fileDirectoryTreeMapper.getModelListLinkFileInfoV2(MapUtil.builder()
                    .put("appId", repoId.toString())
                    .put("parentId", parentId)
                    .put("isRepoPage", isRepoPage)
                    .put("start", (pageNo - 1) * 10)
                    .put("limit", pageSize)
                    .put("safeOrderBy", "create_time desc")
                    .build());
            if (!CollectionUtils.isEmpty(modelListLinkFileInfoV2)) {
                for (FileDirectoryTree fileDirectoryTree : modelListLinkFileInfoV2) {
                    FileDirectoryTreeDto fileDirectoryTreeDto = new FileDirectoryTreeDto();
                    fileDirectoryTreeDtoList.add(fileDirectoryTreeDto);
                    BeanUtils.copyProperties(fileDirectoryTree, fileDirectoryTreeDto);
                    // if (fileDirectoryTree.getIsFile()==1) {
                    // FileInfoV2 fileInfoV2 = this.getById(fileDirectoryTree.getFileId());
                    // Double v = myMongoService.sumField(Knowledge.class, "dialogHitCount",
                    // Criteria.where("fileId").is(fileInfoV2.getUuid()));
                    // fileDirectoryTreeDto.setHitCount(v.longValue());
                    // }
                }
            }
            modelListCount = fileDirectoryTreeMapper.selectCount(Wrappers.lambdaQuery(FileDirectoryTree.class)
                    .eq(FileDirectoryTree::getAppId, repoId.toString())
                    .eq(FileDirectoryTree::getParentId, parentId)
                    .eq(FileDirectoryTree::getStatus, 1));
        }

        pageData.setTotalCount(modelListCount);
        pageData.setPageData(fileDirectoryTreeDtoList);
        return pageData;
    }

    /**
     * Create a new folder in the repository
     *
     * @param folderVO folder creation parameters containing name, repository ID, and parent ID
     * @throws BusinessException if folder name is empty, contains illegal characters, or repository
     *         access is denied
     */
    public void createFolder(CreateFolderVO folderVO) {
        String name = folderVO.getName();
        String regex = ".*[\\\\/:*?\"<>|].*";
        if (ObjectIsNull.check(name)) {
            throw new BusinessException(ResponseEnum.REPO_FILE_NAME_CANNOT_EMPTY);
        } else {
            boolean flag = name.matches(regex);
            if (flag) {
                throw new BusinessException(ResponseEnum.REPO_FOLDER_NAME_ILLEGAL);
            }
        }
        Long parentId = folderVO.getParentId();

        Repo repo = repoService.getById(folderVO.getRepoId());
        if (repo != null) {
            dataPermissionCheckTool.checkRepoBelong(repo);
        }

        FileDirectoryTree fileDirectoryTree;
        // Non-empty means a folder with the same name exists in the same directory, user creation is not
        // allowed
        /*
         * if (!ObjectIsNull.check(fileDirectoryTree)) { throw new
         * CustomException("Folders with the same name are not allowed in the current directory"); }
         */
        fileDirectoryTree = new FileDirectoryTree();
        fileDirectoryTree.setIsFile(0);
        fileDirectoryTree.setName(name);
        fileDirectoryTree.setAppId(folderVO.getRepoId().toString());
        fileDirectoryTree.setParentId(parentId);
        fileDirectoryTree.setCreateTime(LocalDateTime.now());
        fileDirectoryTree.setStatus(1);
        // Insert a record directly into database table
        fileDirectoryTreeMapper.insert(fileDirectoryTree);
    }


    /**
     * Update existing folder name
     *
     * @param folderVO folder update parameters containing ID, new name, and repository ID
     * @throws BusinessException if folder name is empty, contains illegal characters, or repository
     *         access is denied
     */
    public void updateFolder(CreateFolderVO folderVO) {
        String name = folderVO.getName();
        String regex = ".*[\\\\/:*?\"<>|].*";
        if (ObjectIsNull.check(name)) {
            throw new BusinessException(ResponseEnum.REPO_FILE_NAME_CANNOT_EMPTY);
        } else {
            boolean flag = name.matches(regex);
            if (flag) {
                throw new BusinessException(ResponseEnum.REPO_FOLDER_NAME_ILLEGAL);
            }
        }
        Repo repo = repoService.getById(folderVO.getRepoId());
        if (repo != null) {
            dataPermissionCheckTool.checkRepoBelong(repo);
        }
        FileDirectoryTree fileDirectoryTree = fileDirectoryTreeService.getById(folderVO.getId());
        fileDirectoryTree.setName(folderVO.getName());
        fileDirectoryTree.setUpdateTime(LocalDateTime.now());
        fileDirectoryTreeService.updateById(fileDirectoryTree);
    }


    /**
     * Update file name in directory tree and file info table
     *
     * @param folderVO file update parameters containing ID and new name
     * @throws RuntimeException if file is not found
     * @throws BusinessException if file access is denied
     */
    public void updateFile(CreateFolderVO folderVO) {
        FileDirectoryTree fileDirectoryTree = fileDirectoryTreeService.getById(folderVO.getId());
        Long spaceId = SpaceInfoUtil.getSpaceId();
        if (fileDirectoryTree == null) {
            throw new RuntimeException("File not found");
        }
        FileInfoV2 fileInfoV2 = this.getById(fileDirectoryTree.getFileId());
        if (fileInfoV2 == null) {
            throw new RuntimeException("File not found");
        }
        if (null == spaceId) {
            dataPermissionCheckTool.checkFileBelong(fileInfoV2);
        }

        fileDirectoryTree.setName(folderVO.getName());
        fileDirectoryTree.setUpdateTime(LocalDateTime.now());
        fileDirectoryTreeService.updateById(fileDirectoryTree);

        fileInfoV2.setName(folderVO.getName());
        fileInfoV2.setUpdateTime(new Timestamp(System.currentTimeMillis()));
        this.updateById(fileInfoV2);
    }


    /**
     * Search files in repository with real-time streaming response
     *
     * @param repoId repository ID to search in
     * @param fileName file name pattern to search for
     * @param isFile flag indicating whether to search for files (1) or directories (0)
     * @param pid parent directory ID filter (optional)
     * @param tag file source tag (Spark RAG or others)
     * @param isRepoPage flag indicating if this is a repository page search
     * @param request HTTP servlet request for authentication
     * @return SseEmitter for streaming search results
     * @throws BusinessException if repository access is denied
     */
    public SseEmitter searchFile(Long repoId, String fileName, Integer isFile, Long pid, String tag, Integer isRepoPage, HttpServletRequest request) {
        SseEmitter emitter = new SseEmitter();

        // First perform local database query by file name (maintain original SQL call)
        List<FileDirectoryTree> matched = fileDirectoryTreeMapper.getModelListSearchByFileName(MapUtil.builder()
                .put("appId", repoId)
                .put("isFile", isFile)
                .put("fileName", fileName)
                .put("isRepoPage", isRepoPage)
                .build());

        try {
            if (ProjectContent.isSparkRagCompatible(tag)) {
                streamSparkSearch(emitter, repoId, fileName, request);
            } else {
                Repo repo = repoService.getById(repoId);
                if (repo != null) {
                    dataPermissionCheckTool.checkRepoBelong(repo);
                }
                streamLocalSearch(emitter, matched, repoId, pid);
                emitter.complete(); // Consistent with original implementation: complete when local branch ends
            }
        } catch (IOException e) {
            log.error("Error sending data", e);
            emitter.completeWithError(e);
        }
        return emitter;
    }

    /* ======================== Spark Branch ======================== */
    private void streamSparkSearch(SseEmitter emitter, Long repoId, String fileName, HttpServletRequest request) throws IOException {
        String url = apiUrl.getDatasetFileUrl() + "?datasetId="
                .concat(repoId.toString())
                .concat("&searchValue=")
                .concat(fileName);
        log.info("searchFile request url: {}", url);

        Map<String, String> header = new HashMap<>();
        String authorization = request.getHeader("Authorization");
        if (StringUtils.isNotBlank(authorization)) {
            header.put("Authorization", authorization);
        }
        String resp = OkHttpUtil.get(url, header);
        JSONObject obj = JSON.parseObject(resp);
        log.info("searchFile response data: {}", resp);

        List<RelatedDocDto> data = new ArrayList<>();
        if (obj.getBooleanValue("flag") && obj.getInteger("code") == 0) {
            data = JSON.parseArray(obj.getString("data"), RelatedDocDto.class);
        }

        long total = data.size();
        for (int i = 0; i <= total; i++) {
            if (i == total) {
                sendBye(emitter);
            } else {
                FileDirectoryTreeDto dto = buildDtoFromRelatedDocDto(data.get(i));
                sendData(emitter, dto);
            }
        }
        // Note: Keep consistent with original code, Spark branch only sends bye, does not call complete()
    }

    /* ======================== Local Branch ======================== */
    private void streamLocalSearch(SseEmitter emitter, List<FileDirectoryTree> list, Long repoId, Long pid) throws IOException {
        int size = list.size();
        for (int i = 0; i <= size; i++) {
            if (i == size) {
                sendBye(emitter);
            } else {
                FileDirectoryTree row = list.get(i);
                FileDirectoryTreeDto dto = buildDtoFromDirectoryRow(repoId, row, pid);
                if (dto == null) {
                    // Does not satisfy pid filter, skip current item
                    continue;
                }
                sendData(emitter, dto);
            }
        }
    }

    /* ======================== DTO Construction ======================== */
    private FileDirectoryTreeDto buildDtoFromRelatedDocDto(RelatedDocDto src) {
        FileDirectoryTreeDto dto = new FileDirectoryTreeDto();
        dto.setId(src.getId());
        dto.setName(src.getName());
        dto.setParentId(-1L);
        dto.setIsFile(1);
        dto.setCreateTime(src.getCreateTime());

        FileInfoV2 fi = new FileInfoV2();
        fi.setCharCount(src.getCharCount().longValue());
        fi.setName(src.getName());
        fi.setStatus(src.getStatus());
        fi.setEnabled(1);
        fi.setId(src.getId());
        fi.setUuid(src.getDatasetIndex());
        dto.setFileInfoV2(fi);
        return dto;
    }

    private FileDirectoryTreeDto buildDtoFromDirectoryRow(Long repoId, FileDirectoryTree row, Long pid) {
        FileDirectoryTreeDto dto = new FileDirectoryTreeDto();
        BeanUtils.copyProperties(row, dto);

        Long parentId = row.getParentId();
        if (parentId != null && !parentId.equals(-1L)) {
            // Trace back parent path while filtering by pid
            List<FileDirectoryTree> path = new ArrayList<>();
            recursiveFindFatherPath(String.valueOf(repoId), parentId, path);
            if (pid != null && pid != -1L && !containsId(path, pid)) {
                return null; // Does not exist under specified pid, filter out
            }
            if (!CollectionUtils.isEmpty(path)) {
                Collections.reverse(path);
                dto.setPath(buildPathString(path));
            }
        }
        return dto;
    }

    /* ======================== Utility Methods ======================== */
    private boolean containsId(List<FileDirectoryTree> list, Long id) {
        for (FileDirectoryTree t : list) {
            if (Objects.equals(t.getId(), id))
                return true;
        }
        return false;
    }

    private String buildPathString(List<FileDirectoryTree> path) {
        StringBuilder sb = new StringBuilder();
        for (FileDirectoryTree p : path) {
            sb.append("/").append(p.getName());
        }
        return sb.toString();
    }

    private void sendData(SseEmitter emitter, Object dto) throws IOException {
        emitter.send(SseEmitter.event().name("data").data(JSONObject.toJSONString(dto)));
    }

    private void sendBye(SseEmitter emitter) throws IOException {
        emitter.send(SseEmitter.event().name("bye").data("bye"));
    }


    /**
     * List file directory tree path from root to specified file
     *
     * @param fileId ID of the file to trace path for
     * @return list of FileDirectoryTree objects representing the path from root to file
     */
    public List<FileDirectoryTree> listFileDirectoryTree(Long fileId) {
        List<FileDirectoryTree> fileDirectoryTreePathList = new ArrayList<>();
        FileDirectoryTree fileDirectoryTree = fileDirectoryTreeService.getById(fileId);
        if (fileDirectoryTree == null) {
            return fileDirectoryTreePathList;
        }
        recursiveFindFatherPath(fileDirectoryTree.getAppId(), fileId, fileDirectoryTreePathList);
        Collections.reverse(fileDirectoryTreePathList);
        return fileDirectoryTreePathList;
    }


    /**
     * Enable or disable a file in the repository
     *
     * @param id file directory tree ID
     * @param enabled enable status (1=enabled, 0=disabled)
     * @throws BusinessException if file does not exist or access is denied
     */
    @Transactional
    public void enableFile(Long id, Integer enabled) {
        FileDirectoryTree fileDirectoryTree = fileDirectoryTreeService.getById(id);
        Long spaceId = SpaceInfoUtil.getSpaceId();
        if (fileDirectoryTree == null || fileDirectoryTree.getIsFile() != 1) {
            throw new BusinessException(ResponseEnum.REPO_FILE_NOT_EXIST);
        }
        FileInfoV2 fileInfoV2 = this.getById(fileDirectoryTree.getFileId());
        if (fileInfoV2 == null) {
            throw new BusinessException(ResponseEnum.REPO_FILE_NOT_EXIST);
        }
        if (null == spaceId) {
            dataPermissionCheckTool.checkFileBelong(fileInfoV2);
        }

        fileInfoV2.setEnabled(enabled);
        fileInfoV2.setUpdateTime(new Timestamp(System.currentTimeMillis()));
        this.updateById(fileInfoV2);

        knowledgeService.enableDoc(fileInfoV2.getId(), enabled);
    }


    /**
     * Delete file from directory tree and associated data
     *
     * @param id file ID to delete
     * @param tag file source tag (Spark RAG or others)
     * @param repoId repository ID
     * @param request HTTP servlet request for authentication
     * @throws BusinessException if file deletion fails or repository access is denied
     */
    @Transactional
    public void deleteFileDirectoryTree(String id, String tag, Long repoId, HttpServletRequest request) {
        if (ProjectContent.isSparkRagCompatible(tag)) {
            // 星火删除
            String url = apiUrl.getDeleteXinghuoDatasetFileUrl() + "?datasetId=" + repoId + "&fileId=" + id;
            HashMap<String, String> header = new HashMap<>();
            String authorization = request.getHeader("Authorization");
            if (StringUtils.isNotBlank(authorization)) {
                header.put("Authorization", authorization);
            }
            String resp = OkHttpUtil.get(url, header);
            JSONObject jsonObject = JSONObject.parseObject(resp);
            if (jsonObject.get("code") == null || jsonObject.getIntValue("code") != 0) {
                throw new BusinessException(ResponseEnum.REPO_FILE_DELETE_FAILED);
            }
        } else {
            Repo repo = repoService.getById(repoId);
            if (repo != null) {
                dataPermissionCheckTool.checkRepoBelong(repo);
            }

            Long fileId = Long.valueOf(id);
            FileDirectoryTree fileDirectoryTree = fileDirectoryTreeService.getById(fileId);
            if (fileDirectoryTree == null || fileDirectoryTree.getIsFile() != 1) {
                throw new BusinessException(ResponseEnum.REPO_FILE_NOT_EXIST);
            }

            fileDirectoryTreeService.removeById(fileId);
            List<Long> ids = new ArrayList<>();
            ids.add(fileDirectoryTree.getFileId());

            // Add back billing metrics after deletion
            FileInfoV2 fileInfoV2 = this.getById(fileDirectoryTree.getFileId());
            removeById(fileInfoV2.getId());
            fileCostRollback(fileInfoV2.getUuid());
            knowledgeService.deleteDoc(ids);
        }
    }


    /**
     * Delete file by ID and associated knowledge documents
     *
     * @param id file ID to delete
     */
    @Transactional
    public void deleteFile(Long id) {
        fileDirectoryTreeService.remove(Wrappers.lambdaQuery(FileDirectoryTree.class).eq(FileDirectoryTree::getFileId, id));
        List<Long> ids = new ArrayList<>();
        ids.add(id);
        knowledgeService.deleteDoc(ids);
    }


    /**
     * Delete folder and all its contents recursively
     *
     * @param id folder ID to delete
     * @throws BusinessException if folder does not exist or repository access is denied
     */
    @Transactional
    public void deleteFolder(Long id) {
        FileDirectoryTree fileDirectoryTree = fileDirectoryTreeService.getById(id);
        if (fileDirectoryTree == null || fileDirectoryTree.getIsFile() != 0) {
            throw new BusinessException(ResponseEnum.REPO_FOLDER_NOT_EXIST);
        }
        Repo repo = repoService.getById(fileDirectoryTree.getAppId());
        if (repo != null) {
            dataPermissionCheckTool.checkRepoBelong(repo);
        }
        // Recursively find all files and directory objects under the current folder
        List<FileDirectoryTree> dirList = new ArrayList<>();
        List<FileDirectoryTree> fileList = new ArrayList<>();
        this.recursiveFindChildPath(fileDirectoryTree.getAppId(), id, dirList, fileList);
        Set<Long> delIdSet = new HashSet<>();
        delIdSet.add(id);
        for (FileDirectoryTree directoryTree : dirList) {
            delIdSet.add(directoryTree.getId());
        }
        List<Long> delDocIdList = new ArrayList<>();
        for (FileDirectoryTree directoryTree : fileList) {
            delIdSet.add(directoryTree.getId());
            delDocIdList.add(directoryTree.getFileId());
        }
        fileDirectoryTreeMapper.deleteBatchIds(delIdSet);
        removeBatchByIds(delIdSet);

        // Add back billing metrics after deletion
        for (Long dicId : delDocIdList) {
            FileInfoV2 fileInfoV2 = this.getById(dicId);
            fileCostRollback(fileInfoV2.getUuid());
        }

        // Delete documents
        knowledgeService.deleteDoc(delDocIdList);
    }


    /**
     * Get file information by source ID
     *
     * @param sourceId source ID (UUID) of the file
     * @return FileInfoV2 object containing file information
     * @throws BusinessException if file access is denied
     */
    public FileInfoV2 getFileInfoV2BySourceId(String sourceId) {
        Long spaceId = SpaceInfoUtil.getSpaceId();
        FileInfoV2 fileInfoV2 = this.getOnly(new QueryWrapper<FileInfoV2>().eq("uuid", sourceId));
        if (null == spaceId) {
            dataPermissionCheckTool.checkFileBelong(fileInfoV2);
        }

        return fileInfoV2;
    }


    /**
     * Download knowledge data that violates content policies as Excel file
     *
     * @param response HTTP response for file download
     * @param knowledgeQueryVO query parameters containing file IDs and source type
     * @throws BusinessException if file access is denied or export fails
     */
    public void downloadKnowledgeByViolation(HttpServletResponse response, KnowledgeQueryVO knowledgeQueryVO) {
        // 1) Collect files and perform permission validation
        RepoContext ctx = resolveRepoContext(knowledgeQueryVO);

        // 2) Build workbook/styles/headers
        HSSFWorkbook wb = new HSSFWorkbook();
        HSSFSheet sheet = wb.createSheet("Violation Details");
        ExcelStyles styles = buildStyles(wb);
        writeHeader(sheet, styles.header);

        // 3) Query violation data (preview or formal)
        // Query q = buildViolationQuery(ctx.fileUuids);
        Integer source = knowledgeQueryVO.getSource();
        if (source == null || source == 0) {
            // List<PreviewKnowledge> list = mongoTemplate.find(q, PreviewKnowledge.class);
            List<MysqlPreviewKnowledge> list = previewKnowledgeMapper.findByFileIdInAndAuditType(ctx.fileUuids, 1);
            fillPreviewRows(sheet, list, ctx.fileMap, styles.body);
        } else {
            // List<Knowledge> list = mongoTemplate.find(q, Knowledge.class);
            List<MysqlKnowledge> list = knowledgeMapper.findByFileIdInAndAuditType(ctx.fileUuids, 1);
            fillKnowledgeRows(sheet, list, ctx.fileMap, styles.body);
        }

        // 4) Output
        writeWorkbook(response, wb, "(" + ctx.repo.getName() + ") Violation Details");
    }

    private static final class RepoContext {
        List<String> fileUuids;
        Map<String, FileInfoV2> fileMap;
        Repo repo;
    }

    private RepoContext resolveRepoContext(KnowledgeQueryVO vo) {
        RepoContext c = new RepoContext();
        List<Long> fileIds = vo.getFileIds().stream().map(Long::valueOf).collect(Collectors.toList());
        List<FileInfoV2> files = fileInfoV2Mapper.listByIds(fileIds);
        if (CollectionUtils.isEmpty(files)) {
            throw new BusinessException(ResponseEnum.REPO_FILE_NOT_EXIST);
        }
        c.fileUuids = new ArrayList<>(files.size());
        c.fileMap = new HashMap<>(files.size() * 2);
        for (FileInfoV2 f : files) {
            c.fileUuids.add(f.getUuid());
            c.fileMap.put(f.getUuid(), f);
        }
        Long repoId = files.get(0).getRepoId();
        c.repo = repoService.getById(repoId);
        dataPermissionCheckTool.checkRepoBelong(c.repo);
        return c;
    }

    // private Query buildViolationQuery(List<String> fileUuids) {
    // Criteria c = Criteria.where("fileId")
    // .in(fileUuids)
    // .and("content.auditSuggest")
    // .in("block", "review");
    // return new Query(c)
    // .with(Sort.by(Sort.Direction.ASC, "fileId"))
    // .with(Sort.by(Sort.Direction.ASC, "_id"));
    // }

    /* ---------- Excel Construction ---------- */

    private static final class ExcelStyles {
        HSSFCellStyle header;
        HSSFCellStyle body;
    }

    private ExcelStyles buildStyles(HSSFWorkbook wb) {
        ExcelStyles s = new ExcelStyles();
        // header
        HSSFCellStyle h = wb.createCellStyle();
        h.setAlignment(HorizontalAlignment.CENTER);
        h.setVerticalAlignment(VerticalAlignment.CENTER);
        HSSFFont hf = wb.createFont();
        hf.setFontHeightInPoints((short) 10);
        hf.setBold(true);
        hf.setFontName("宋体");
        h.setFont(hf);
        // body
        HSSFCellStyle b = wb.createCellStyle();
        b.setAlignment(HorizontalAlignment.CENTER);
        b.setVerticalAlignment(VerticalAlignment.CENTER);
        s.header = h;
        s.body = b;
        return s;
    }

    private void writeHeader(HSSFSheet sheet, HSSFCellStyle headerStyle) {
        List<String> heads = Arrays.asList("序号", "文件名", "文件内容", "违规原因");
        HSSFRow row0 = sheet.createRow(0);
        row0.setHeight((short) 1000);
        for (int i = 0; i < heads.size(); i++) {
            HSSFCell c = row0.createCell(i);
            c.setCellValue(heads.get(i));
            c.setCellStyle(headerStyle);
            sheet.setColumnWidth(i, 5000);
        }
        // Compatible with originally set additional column width
        sheet.setColumnWidth(5, 8000);
        sheet.setColumnWidth(6, 12000);
    }

    /* ---------- Data Filling ---------- */

    private void fillPreviewRows(HSSFSheet sheet, List<MysqlPreviewKnowledge> list,
            Map<String, FileInfoV2> fileMap, HSSFCellStyle body) {
        if (CollectionUtils.isEmpty(list))
            return;
        for (int i = 0; i < list.size(); i++) {
            MysqlPreviewKnowledge k = list.get(i);
            HSSFRow r = sheet.createRow(i + 1);
            r.setHeight((short) 1000);
            setCommonCells(r, i, fileMap.get(k.getFileId()), k.getContent().getString("knowledge"),
                    extractAuditDetail(k.getContent().getJSONArray("auditDetail")), body);
        }
    }

    private void fillKnowledgeRows(HSSFSheet sheet, List<MysqlKnowledge> list,
            Map<String, FileInfoV2> fileMap, HSSFCellStyle body) {
        if (CollectionUtils.isEmpty(list))
            return;
        for (int i = 0; i < list.size(); i++) {
            MysqlKnowledge k = list.get(i);
            HSSFRow r = sheet.createRow(i + 1);
            r.setHeight((short) 1000);
            setCommonCells(r, i, fileMap.get(k.getFileId()), k.getContent().getString("knowledge"),
                    extractAuditDetail(k.getContent().getJSONArray("auditDetail")), body);
        }
    }

    private void setCommonCells(HSSFRow row, int idx, FileInfoV2 fileInfo,
            String content, String audit, HSSFCellStyle style) {
        HSSFCell c0 = row.createCell(0);
        c0.setCellValue(idx + 1);
        c0.setCellStyle(style);

        HSSFCell c1 = row.createCell(1);
        c1.setCellValue(fileInfo == null ? "" : fileInfo.getName());
        c1.setCellStyle(style);

        HSSFCell c2 = row.createCell(2);
        c2.setCellValue(content);
        c2.setCellStyle(style);

        HSSFCell c3 = row.createCell(3);
        c3.setCellValue(audit);
        c3.setCellStyle(style);
    }

    private String extractAuditDetail(JSONArray arr) {
        if (CollectionUtils.isEmpty(arr))
            return "";
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < arr.size(); i++) {
            JSONObject o = arr.getJSONObject(i);
            String desc = o.getString("categoryDescription");
            Integer conf = o.getInteger("confidence");
            if (desc != null) {
                sb.append(desc);
                if (conf != null)
                    sb.append(":").append(conf);
                sb.append("\n");
            }
        }
        return sb.toString();
    }

    /* ---------- Output ---------- */

    private void writeWorkbook(HttpServletResponse resp, HSSFWorkbook wb, String filename) {
        try (ServletOutputStream out = resp.getOutputStream()) {
            resp.reset();
            resp.setHeader("Content-disposition",
                    "attachment; filename=" + URLEncoder.encode(filename, StandardCharsets.UTF_8.name()) + ".xls");
            resp.setContentType("application/msexcel");
            wb.write(out);
            out.flush();
        } catch (IOException ex) {
            log.error("File download failed", ex);
            throw new BusinessException(ResponseEnum.REPO_FILE_DOWNLOAD_FAILED);
        }
    }


    /**
     * Get file information list by repository core ID and existing source IDs
     *
     * @param repoCoreId repository core identifier
     * @param existSourceIds list of existing source IDs to filter
     * @return list of FileInfoV2 objects matching the criteria
     * @throws BusinessException if file access is denied
     */
    public List<FileInfoV2> getFileInfoV2UUIDS(String repoCoreId, List<String> existSourceIds) {
        List<FileInfoV2> fileInfoV2List = fileInfoV2Mapper.getFileInfoV2UUIDS(repoCoreId, existSourceIds);
        Long spaceId = SpaceInfoUtil.getSpaceId();

        for (FileInfoV2 fileInfoV2 : fileInfoV2List) {
            if (null == spaceId) {
                dataPermissionCheckTool.checkFileBelong(fileInfoV2);
            }

        }
        return fileInfoV2List;
    }


    /**
     * Get model count by repository ID and file UUID
     *
     * @param repoId repository identifier
     * @param sourceId file source identifier (UUID)
     * @return count of models associated with the file
     */
    public Integer getModelCountByRepoIdAndFileUUIDS(String repoId, String sourceId) {
        return fileDirectoryTreeMapper.getModelCountByRepoIdAndFileUUIDS(repoId, sourceId);
    }


    /**
     * Get file information list by repository core ID and file names
     *
     * @param repoCoreId repository core identifier
     * @param fileNames list of file names to search for
     * @return list of FileInfoV2 objects matching the file names
     * @throws BusinessException if file access is denied
     */
    public List<FileInfoV2> getFileInfoV2ByNames(String repoCoreId, List<String> fileNames) {
        List<FileInfoV2> fileInfoV2List = fileInfoV2Mapper.getFileInfoV2ByNames(repoCoreId, fileNames);
        Long spaceId = SpaceInfoUtil.getSpaceId();

        for (FileInfoV2 fileInfoV2 : fileInfoV2List) {
            if (null == spaceId) {
                dataPermissionCheckTool.checkFileBelong(fileInfoV2);
            }
        }
        return fileInfoV2List;
    }

    /**
     * Get all file information by repository ID
     *
     * @param repoId repository identifier
     * @return list of all FileInfoV2 objects in the repository
     * @throws BusinessException if file access is denied
     */
    public List<FileInfoV2> getFileInfoV2ByRepoId(Long repoId) {
        List<FileInfoV2> fileInfoV2List = fileInfoV2Mapper.getFileInfoV2ByRepoId(repoId);
        Long spaceId = SpaceInfoUtil.getSpaceId();

        for (FileInfoV2 fileInfoV2 : fileInfoV2List) {
            if (null == spaceId) {
                dataPermissionCheckTool.checkFileBelong(fileInfoV2);
            }
        }
        return fileInfoV2List;
    }

    private void recursiveFindChildPath(String appId, Long parentId, List<FileDirectoryTree> dirList, List<FileDirectoryTree> fileList) {
        List<FileDirectoryTree> fileDirectoryTreeList = fileDirectoryTreeMapper.selectList(Wrappers.lambdaQuery(FileDirectoryTree.class).eq(FileDirectoryTree::getAppId, appId).eq(FileDirectoryTree::getParentId, parentId));
        if (CollectionUtils.isEmpty(fileDirectoryTreeList)) {
            return;
        }
        for (FileDirectoryTree fileDirectoryTree : fileDirectoryTreeList) {
            if (fileDirectoryTree.getIsFile() == 1) {
                fileList.add(fileDirectoryTree);
            } else {
                dirList.add(fileDirectoryTree);
                this.recursiveFindChildPath(appId, fileDirectoryTree.getId(), dirList, fileList);
            }
        }
    }

    /**
     * Get file size mapping by user ID
     *
     * @param uid user identifier
     * @return map of file UUID to file size in KB
     */
    public Map<String, Long> getFileSizeMapByUid(String uid) {
        List<FileInfoV2> fileInfoV2List = fileInfoV2Mapper.getFileInfoV2byUserId(uid);
        Map<String, Long> fileSizeMap = new HashMap<>();
        for (FileInfoV2 fileInfoV2 : fileInfoV2List) {
            fileSizeMap.put(fileInfoV2.getUuid(), fileInfoV2.getSize() / 1024);
        }
        return fileSizeMap;
    }

    private void recursiveFindFatherPath(String appId, Long parentId, List<FileDirectoryTree> fileDirectoryTreePathList) {
        if (parentId.equals(-1L)) {
            return;
        }
        FileDirectoryTree parentDirectory = fileDirectoryTreeService.getOnly(Wrappers.lambdaQuery(FileDirectoryTree.class).eq(FileDirectoryTree::getAppId, appId).eq(FileDirectoryTree::getId, parentId));
        if (parentDirectory != null) {
            fileDirectoryTreePathList.add(parentDirectory);
            recursiveFindFatherPath(appId, parentDirectory.getParentId(), fileDirectoryTreePathList);
        }
    }

    /**
     * Extract file format/extension from filename
     *
     * @param fileName the filename to extract format from
     * @return file extension without dot, empty string if no extension found
     */
    public static String getFileFormat(String fileName) {
        int lastDotIndex = fileName.lastIndexOf('.');
        if (lastDotIndex != -1 && lastDotIndex < fileName.length() - 1) {
            return fileName.substring(lastDotIndex + 1);
        }
        return ""; // Return empty string indicating no extension found
    }

    /**
     * Check if file is an image based on its extension
     *
     * @param fileName filename to check
     * @return true if file is an image (jpg, jpeg, png, bmp), false otherwise
     */
    public static boolean checkIsPic(String fileName) {
        String fileType = getFileFormat(fileName);
        if (!StringUtils.isEmpty(fileType)) {
            return fileType.equalsIgnoreCase(ProjectContent.JPG_FILE_TYPE) || fileType.equalsIgnoreCase(ProjectContent.JPEG_FILE_TYPE) || fileType.equalsIgnoreCase(ProjectContent.PNG_FILE_TYPE)
                    || fileType.equalsIgnoreCase(ProjectContent.BMP_FILE_TYPE);
        }
        return false;
    }

    private boolean checkLeftSize(String uid, long fileSize) {
        return true;
    }

    private boolean addFileCost(String uid, long fileSize, Long spaceId) {
        return true;
    }

    public void fileCostRollback(String docId) {
        ResourceParameter rollback = new ResourceParameter();
        rollback.setKey(CommonConst.ALL_FILE_LIMIT_COUNT);
        // rollback.setBusinessId(docId);
        Long spaceId = SpaceInfoUtil.getSpaceId();
        // facade.rollBackResources(rollback, spaceId);
    }
}
