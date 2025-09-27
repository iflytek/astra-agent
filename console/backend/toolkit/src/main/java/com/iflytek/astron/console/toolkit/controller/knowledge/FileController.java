package com.iflytek.astron.console.toolkit.controller.knowledge;


import com.iflytek.astron.console.commons.annotation.space.SpacePreAuth;
import com.iflytek.astron.console.commons.constant.ResponseEnum;
import com.iflytek.astron.console.commons.exception.BusinessException;
import com.iflytek.astron.console.commons.response.ApiResult;
import com.iflytek.astron.console.toolkit.common.Result;
import com.iflytek.astron.console.toolkit.common.anno.ResponseResultBody;
import com.iflytek.astron.console.toolkit.entity.common.PageData;
import com.iflytek.astron.console.toolkit.entity.dto.FileInfoV2Dto;
import com.iflytek.astron.console.toolkit.entity.dto.KnowledgeDto;
import com.iflytek.astron.console.toolkit.entity.pojo.FileSummary;
import com.iflytek.astron.console.toolkit.entity.table.repo.FileDirectoryTree;
import com.iflytek.astron.console.toolkit.entity.table.repo.FileInfoV2;
import com.iflytek.astron.console.toolkit.entity.vo.HtmlFileVO;
import com.iflytek.astron.console.toolkit.entity.vo.repo.*;
import com.iflytek.astron.console.toolkit.service.repo.FileInfoV2Service;
import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.Collections;
import java.util.List;
import java.util.concurrent.ExecutionException;

/**
 * File management controller Provides REST APIs for file operations including upload, download,
 * slicing, embedding, and management
 *
 * @author Astron Team
 * @since 1.0.0
 */
@RestController
@RequestMapping("/file")
@Slf4j
@ResponseResultBody
public class FileController {
    @Resource
    private FileInfoV2Service fileInfoV2Service;

    /**
     * Upload file to the specified repository and parent directory
     *
     * @param file the multipart file to be uploaded, must not be null
     * @param parentId the ID of parent directory where file will be stored
     * @param repoId the ID of target repository
     * @param tag the source tag to categorize the file
     * @param request HTTP servlet request containing user context
     * @return ApiResult containing the uploaded file information with metadata
     * @throws BusinessException when file upload fails or validation errors occur
     */
    @PostMapping("/upload")
    @SpacePreAuth(key = "FileController_upload_POST",
            module = "File", point = "File Upload", description = "File Upload")
    public ApiResult<FileInfoV2> uploadFile(@RequestParam("file") MultipartFile file,
            @RequestParam("parentId") Long parentId,
            @RequestParam("repoId") Long repoId,
            @RequestParam("tag") String tag,
            HttpServletRequest request) {
        return ApiResult.success(fileInfoV2Service.uploadFile(file, parentId, repoId, tag, request));
    }

    /**
     * Create HTML file from provided content and metadata
     *
     * @param htmlFileVO the HTML file creation request object containing content and metadata
     * @return ApiResult containing list of created file information with their details
     * @throws BusinessException when HTML file creation fails or validation errors occur
     */
    @PostMapping("/create-html-file")
    @SpacePreAuth(key = "FileController_createHtmlFile_POST",
            module = "File", point = "Create HTML File", description = "Create HTML File")
    public ApiResult<List<FileInfoV2>> createHtmlFile(@RequestBody HtmlFileVO htmlFileVO) {
        return ApiResult.success(fileInfoV2Service.createHtmlFile(htmlFileVO));
    }

    /**
     * Slice files into smaller chunks based on specified configuration Sets default separator to
     * newline if not provided
     *
     * @param sliceFileVO the file slicing request object containing file IDs and slice configuration
     * @return ApiResult containing Boolean indicating whether slicing operation succeeded
     * @throws InterruptedException if the current thread is interrupted during execution
     * @throws ExecutionException if the computation threw an exception during async processing
     */
    @PostMapping("/slice")
    public ApiResult<Boolean> sliceFiles(@RequestBody DealFileVO sliceFileVO) throws InterruptedException, ExecutionException {
        if (StringUtils.isEmpty(sliceFileVO.getSliceConfig().getSeperator().get(0))) {
            sliceFileVO.getSliceConfig().setSeperator(Collections.singletonList("\n"));
        }
        Result<Boolean> result = fileInfoV2Service.sliceFiles(sliceFileVO);
        if (result.noError()) {
            return ApiResult.success(result.getData());
        } else {
            return ApiResult.error(result.getCode(), result.getMessage());
        }
    }

    /**
     * Perform knowledge embedding on specified files to create vector representations This is a
     * synchronous operation that processes files immediately
     *
     * @param dealFileVO the file processing request object containing file IDs and processing
     *        parameters
     * @param request HTTP servlet request containing user authentication and context information
     * @return ApiResult with void data indicating operation completion status
     * @throws ExecutionException if the computation threw an exception during async processing
     * @throws InterruptedException if the current thread is interrupted during execution
     */
    @PostMapping("/embedding")
    public ApiResult<Void> embeddingFiles(@RequestBody DealFileVO dealFileVO, HttpServletRequest request) throws ExecutionException, InterruptedException {
        fileInfoV2Service.embeddingFiles(dealFileVO, request);
        return ApiResult.success();
    }

    /**
     * Perform background knowledge embedding on specified files This is an asynchronous operation that
     * processes files in background tasks
     *
     * @param dealFileVO the file processing request object containing file IDs and processing
     *        parameters
     * @param request HTTP servlet request containing user authentication and context information
     * @return ApiResult with void data indicating operation was successfully queued
     * @throws ExecutionException if the computation threw an exception during async processing
     * @throws InterruptedException if the current thread is interrupted during execution
     */
    @PostMapping("/embedding-back")
    public ApiResult<Void> embeddingBack(@RequestBody DealFileVO dealFileVO, HttpServletRequest request) throws ExecutionException, InterruptedException {
        fileInfoV2Service.embeddingBack(dealFileVO, request);
        return ApiResult.success();
    }

    /**
     * Retry failed file processing operations Attempts to reprocess files that previously failed during
     * slicing or embedding
     *
     * @param dealFileVO the file processing request object containing file IDs to retry
     * @param request HTTP servlet request containing user authentication and context information
     * @return ApiResult with void data indicating retry operation was initiated
     * @throws ExecutionException if the computation threw an exception during async processing
     * @throws InterruptedException if the current thread is interrupted during execution
     */
    @PostMapping("/retry")
    public ApiResult<Void> retry(@RequestBody DealFileVO dealFileVO, HttpServletRequest request) throws ExecutionException, InterruptedException {
        fileInfoV2Service.retry(dealFileVO, request);
        return ApiResult.success();
    }


    /**
     * Retrieve the current indexing status for specified files Shows progress and state of file
     * processing operations
     *
     * @param dealFileVO the file processing request object containing file IDs to check status for
     * @return ApiResult containing list of FileInfoV2Dto with indexing status details
     * @throws BusinessException when status retrieval fails or files are not found
     */
    @PostMapping("/file-indexing-status")
    @SpacePreAuth(key = "FileController_fileIndexingStatus_POST",
            module = "File", point = "File Indexing Status", description = "File Indexing Status")
    public ApiResult<List<FileInfoV2Dto>> getIndexingStatus(@RequestBody DealFileVO dealFileVO) {
        return ApiResult.success(fileInfoV2Service.getIndexingStatus(dealFileVO));
    }

    /**
     * Generate and retrieve summary information for specified files Provides statistical data about
     * file content and processing status
     *
     * @param dealFileVO the file processing request object containing file IDs to summarize
     * @param request HTTP servlet request containing user authentication and context information
     * @return ApiResult containing FileSummary with aggregated file statistics
     * @throws BusinessException when summary generation fails or files are not accessible
     */
    @PostMapping("/file-summary")
    public ApiResult<FileSummary> getFileSummary(@RequestBody DealFileVO dealFileVO, HttpServletRequest request) {
        return ApiResult.success(fileInfoV2Service.getFileSummary(dealFileVO, request));
    }

    /**
     * List preview knowledge entries with pagination support Returns a preview of knowledge content
     * before full processing
     *
     * @param knowledgeQueryVO the knowledge query request object containing search criteria and
     *        pagination parameters
     * @return Object containing paginated preview knowledge data (specific return type depends on
     *         implementation)
     * @throws BusinessException when knowledge preview retrieval fails or query parameters are invalid
     */
    @PostMapping("/list-preview-knowledge-by-page")
    public Object listPreviewKnowledgeByPage(@RequestBody KnowledgeQueryVO knowledgeQueryVO) {
        return fileInfoV2Service.listPreviewKnowledgeByPage(knowledgeQueryVO);
    }

    /**
     * List processed knowledge entries with pagination support Returns fully processed knowledge
     * content with metadata
     *
     * @param knowledgeQueryVO the knowledge query request object containing search criteria and
     *        pagination parameters
     * @return ApiResult containing PageData with KnowledgeDto entries and pagination information
     * @throws BusinessException when knowledge retrieval fails or query parameters are invalid
     */
    @PostMapping("/list-knowledge-by-page")
    public ApiResult<PageData<KnowledgeDto>> listKnowledgeByPage(@RequestBody KnowledgeQueryVO knowledgeQueryVO) {
        return ApiResult.success(fileInfoV2Service.listKnowledgeByPage(knowledgeQueryVO));
    }

    /**
     * Download knowledge data that violates certain criteria or rules Exports knowledge entries that
     * don't meet quality standards for review
     *
     * @param response HTTP servlet response to write the download data to
     * @param knowledgeQueryVO the knowledge query request object containing filter criteria for
     *        violation detection
     * @throws BusinessException when download generation fails or no violations are found
     */
    @PostMapping("/download-knowledge-by-violation")
    public void downloadKnowledgeByViolation(HttpServletResponse response, @RequestBody KnowledgeQueryVO knowledgeQueryVO) {
        fileInfoV2Service.downloadKnowledgeByViolation(response, knowledgeQueryVO);
    }


    /**
     * Query and retrieve file list with pagination support Returns files based on repository, directory
     * hierarchy, and access permissions
     *
     * @param repoId the ID of target repository to query files from
     * @param parentId the ID of parent directory, defaults to -1 for root directory
     * @param pageNo the page number for pagination, defaults to 1
     * @param pageSize the number of items per page, defaults to 10
     * @param tag the file source tag for filtering, defaults to empty string
     * @param isRepoPage access control flag: 0 for workflow knowledge base (completed files only), 1
     *        for knowledge base (all files)
     * @param request HTTP servlet request containing user authentication and context information
     * @return Object containing paginated file list data with metadata (specific return type depends on
     *         implementation)
     * @throws BusinessException when file list retrieval fails or access is denied
     */
    @GetMapping("/query-file-list")
    public Object queryFileList(@RequestParam(value = "repoId") Long repoId,
            @RequestParam(value = "parentId", defaultValue = "-1") Long parentId,
            @RequestParam(value = "pageNo", defaultValue = "1") Integer pageNo,
            @RequestParam(value = "pageSize", defaultValue = "10") Integer pageSize,
            @RequestParam(value = "tag", defaultValue = "") String tag,
            @RequestParam(value = "isRepoPage", defaultValue = "1") Integer isRepoPage,
            HttpServletRequest request) {
        return fileInfoV2Service.queryFileList(repoId, parentId, pageNo, pageSize, tag, request, isRepoPage);
    }

    /**
     * Create a new folder in the specified repository and parent directory Validates that tag length
     * does not exceed 30 characters
     *
     * @param folderVO the folder creation request object containing folder name, parent ID, and tags
     * @return ApiResult with void data indicating successful folder creation
     * @throws BusinessException when tag length exceeds 30 characters or folder creation fails
     */
    @PostMapping("/create-folder")
    public ApiResult<Void> createFolder(@RequestBody CreateFolderVO folderVO) {
        if (CollectionUtils.isNotEmpty(folderVO.getTags())) {
            for (String tag : folderVO.getTags()) {
                if (tag.length() > 30) {
                    throw new BusinessException(ResponseEnum.REPO_KNOWLEDGE_TAG_TOO_LONG);
                }
            }
        }

        fileInfoV2Service.createFolder(folderVO);
        return ApiResult.success();
    }

    /**
     * Update existing folder properties such as name, tags, or metadata
     *
     * @param folderVO the folder update request object containing folder ID and updated properties
     * @return ApiResult with void data indicating successful folder update
     * @throws BusinessException when folder update fails or folder is not found
     */
    @PostMapping("/update-folder")
    public ApiResult<Void> updateFolder(@RequestBody CreateFolderVO folderVO) {
        fileInfoV2Service.updateFolder(folderVO);
        return ApiResult.success();
    }

    /**
     * Delete an existing folder and its contents This operation will recursively remove all files and
     * subfolders
     *
     * @param id the unique identifier of the folder to be deleted
     * @return ApiResult with void data indicating successful folder deletion
     * @throws BusinessException when folder deletion fails or folder is not found
     */
    @DeleteMapping("/delete-folder")
    public ApiResult<Void> deleteFolder(@RequestParam("id") Long id) {
        fileInfoV2Service.deleteFolder(id);
        return ApiResult.success();
    }

    /**
     * Update existing file properties such as name, tags, or metadata
     *
     * @param folderVO the file update request object containing file ID and updated properties
     * @return ApiResult with void data indicating successful file update
     * @throws BusinessException when file update fails or file is not found
     */
    @PostMapping("/update-file")
    public ApiResult<Void> updateFile(@RequestBody CreateFolderVO folderVO) {
        fileInfoV2Service.updateFile(folderVO);
        return ApiResult.success();
    }

    /**
     * Retrieve the directory tree structure for a specific file Shows the hierarchical path from root
     * to the specified file
     *
     * @param fileId the unique identifier of the file to get directory tree for
     * @return ApiResult containing list of FileDirectoryTree representing the path hierarchy
     * @throws BusinessException when file is not found or directory tree retrieval fails
     */
    @GetMapping("/list-file-directory-tree")
    public ApiResult<List<FileDirectoryTree>> listFileDirectoryTree(@RequestParam("fileId") Long fileId) {
        return ApiResult.success(fileInfoV2Service.listFileDirectoryTree(fileId));
    }

    /**
     * Search for files and folders by name with real-time streaming results Uses Server-Sent Events
     * (SSE) to stream search results as they are found
     *
     * @param repoId the ID of repository to search within
     * @param fileName the name or partial name of file/folder to search for
     * @param isFile search type flag: 1 for files only, 0 for folders only, null for both
     * @param pid the parent directory ID to limit search scope, null for entire repository
     * @param tag the file source tag for filtering search results
     * @param isRepoPage access control flag: 0 for workflow knowledge base (completed files only), 1
     *        for knowledge base (all files)
     * @param response HTTP servlet response for SSE configuration
     * @param request HTTP servlet request containing user authentication and context information
     * @return SseEmitter for streaming real-time search results to the client
     * @throws BusinessException when search operation fails or parameters are invalid
     */
    @GetMapping("/search-file")
    public SseEmitter searchFile(@RequestParam Long repoId, String fileName, Integer isFile, Long pid, String tag,
            @RequestParam(value = "isRepoPage", defaultValue = "1") Integer isRepoPage, HttpServletResponse response, HttpServletRequest request) {
        // Disable caching for real-time streaming
        response.addHeader("X-Accel-Buffering", "no");
        return fileInfoV2Service.searchFile(repoId, fileName, isFile, pid, tag, isRepoPage, request);
    }

    /**
     * Enable or disable a file's availability in the knowledge base Controls whether the file is
     * included in search and retrieval operations
     *
     * @param id the unique identifier of the file to enable/disable
     * @param enabled status flag: 1 to enable the file, 0 to disable it
     * @return ApiResult with void data indicating successful status change
     * @throws BusinessException when file status update fails or file is not found
     */
    @PutMapping("/enable-file")
    public ApiResult<Void> enableFile(@RequestParam("id") Long id, @RequestParam("enabled") Integer enabled) {
        fileInfoV2Service.enableFile(id, enabled);
        return ApiResult.success();
    }

    /**
     * Delete a file or directory and all its associated data Removes file content, metadata, and
     * directory tree structure
     *
     * @param id the unique identifier of the file or directory to delete
     * @param tag the source tag associated with the file for validation
     * @param repoId the repository ID where the file is located
     * @param request HTTP servlet request containing user authentication and context information
     * @return ApiResult with void data indicating successful deletion
     * @throws BusinessException when file deletion fails or file is not found
     */
    @DeleteMapping("/delete-file")
    public ApiResult<Void> deleteFile(@RequestParam("id") String id, @RequestParam("tag") String tag, @RequestParam("repoId") Long repoId, HttpServletRequest request) {
        fileInfoV2Service.deleteFileDirectoryTree(id, tag, repoId, request);
        return ApiResult.success();
    }

    /**
     * Retrieve detailed file information using its source identifier Returns comprehensive file
     * metadata and processing status
     *
     * @param sourceId the unique source identifier of the file to retrieve
     * @return ApiResult containing FileInfoV2 with complete file information and metadata
     * @throws BusinessException when file is not found or access is denied
     */
    @GetMapping("/get-file-info-by-source-id")
    public ApiResult<FileInfoV2> getFileInfoV2BySourceId(@RequestParam("sourceId") String sourceId) {
        return ApiResult.success(fileInfoV2Service.getFileInfoV2BySourceId(sourceId));
    }
}
