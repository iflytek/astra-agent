package com.iflytek.astra.console.toolkit.controller.knowledge;


import com.iflytek.astra.console.commons.annotation.space.SpacePreAuth;
import com.iflytek.astra.console.commons.response.ApiResult;
import com.iflytek.astra.console.toolkit.common.anno.ResponseResultBody;
import com.iflytek.astra.console.toolkit.entity.common.PageData;
import com.iflytek.astra.console.toolkit.entity.dto.RepoDto;
import com.iflytek.astra.console.toolkit.entity.table.repo.HitTestHistory;
import com.iflytek.astra.console.toolkit.entity.table.repo.Repo;
import com.iflytek.astra.console.toolkit.entity.vo.knowledge.RepoVO;
import com.iflytek.astra.console.toolkit.service.repo.RepoService;
import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

/**
 * Repository Controller
 * <p>
 * This controller handles all repository-related operations including creation, updating, listing,
 * deletion, and various repository management functions. It provides RESTful APIs for repository
 * management in the knowledge base system.
 * </p>
 *
 * @author Astra Team
 * @version 1.0
 * @since 2024
 */
@RestController
@RequestMapping("/repo")
@Slf4j
@ResponseResultBody
public class RepoController {
    @Resource
    private RepoService repoService;

    /**
     * Create a new repository
     * <p>
     * Creates a new repository in the knowledge base system with the provided configuration. The
     * repository will be initialized with default settings and made available for file uploads.
     * </p>
     *
     * @param repoVO the repository creation request object containing repository configuration
     * @return ApiResult containing the created repository information with generated ID and timestamps
     * @throws IllegalArgumentException if the repository configuration is invalid
     * @throws RuntimeException if repository creation fails due to system error
     */
    @PostMapping("/create-repo")
    @SpacePreAuth(key = "RepoController_createRepo_POST",
                    module = "Knowledge Base", point = "Create Repository", description = "Create Repository")
    public ApiResult<Repo> createRepo(@RequestBody RepoVO repoVO) {
        return ApiResult.success(repoService.createRepo(repoVO));
    }

    /**
     * Update an existing repository
     * <p>
     * Updates the configuration and metadata of an existing repository. Only the specified fields in
     * the request object will be updated.
     * </p>
     *
     * @param repoVO the repository update request object containing updated configuration
     * @return ApiResult containing the updated repository information
     * @throws IllegalArgumentException if the repository ID is invalid or update data is malformed
     * @throws RuntimeException if repository update fails due to system error
     */
    @PostMapping("/update-repo")
    @SpacePreAuth(key = "RepoController_updateRepo_POST",
                    module = "Knowledge Base", point = "Update Repository", description = "Update Repository")
    public ApiResult<Repo> updateRepo(@RequestBody RepoVO repoVO) {
        return ApiResult.success(repoService.updateRepo(repoVO));
    }

    /**
     * Update repository status
     * <p>
     * Updates the status of a repository (e.g., active, inactive, processing). This operation affects
     * the repository's availability for queries and operations.
     * </p>
     *
     * @param repoVO the repository update request object containing status information
     * @return ApiResult containing boolean result indicating success or failure of the status update
     * @throws IllegalArgumentException if the repository ID is invalid or status value is not supported
     * @throws RuntimeException if status update fails due to system error
     */
    @PutMapping("/update-repo-status")
    public ApiResult<Boolean> updateRepoStatus(@RequestBody RepoVO repoVO) {
        return ApiResult.success(repoService.updateRepoStatus(repoVO));
    }

    /**
     * List repositories with pagination
     * <p>
     * Retrieves a paginated list of repositories accessible to the current user. Supports optional
     * content-based filtering to search repositories by name or description.
     * </p>
     *
     * @param pageNo the page number to retrieve (1-based indexing, defaults to 1)
     * @param pageSize the number of repositories per page (defaults to 10)
     * @param content optional search content to filter repositories by name or description
     * @param request the HTTP servlet request containing user context and authentication info
     * @return ApiResult containing PageData with repository list and pagination metadata
     * @throws IllegalArgumentException if pageNo or pageSize is invalid (negative or zero)
     * @throws RuntimeException if repository listing fails due to system error
     */
    @GetMapping("/list-repos")
    @SpacePreAuth(key = "RepoController_listRepos_GET",
                    module = "Knowledge Base", point = "Repository List", description = "Repository List")
    public ApiResult<PageData<RepoDto>> listRepos(@RequestParam(value = "pageNo", defaultValue = "1") Integer pageNo,
                    @RequestParam(value = "pageSize", defaultValue = "10") Integer pageSize,
                    @RequestParam(value = "content", required = false) String content,
                    HttpServletRequest request) {
        return ApiResult.success(repoService.listRepos(pageNo, pageSize, content, request));
    }

    /**
     * Get simplified repository list with advanced filtering
     * <p>
     * Retrieves a simplified list of repositories with advanced filtering options. This endpoint
     * provides a lightweight response format suitable for dropdown lists and quick selections.
     * </p>
     *
     * @param pageNo the page number to retrieve (1-based indexing, defaults to 1)
     * @param pageSize the number of repositories per page (defaults to 10)
     * @param content optional search content to filter repositories by name or description
     * @param orderBy optional field name to sort repositories (e.g., "name", "createTime")
     * @param tag optional repository tag for category-based filtering
     * @param request the HTTP servlet request containing user context and authentication info
     * @return Object containing simplified repository list data with basic information
     * @throws IllegalArgumentException if pagination parameters are invalid
     * @throws RuntimeException if repository listing fails due to system error
     */
    @GetMapping("/list")
    @SpacePreAuth(key = "RepoController_list_GET",
                    module = "Knowledge Base", point = "Simplified Repository List", description = "Simplified Repository List")
    public Object list(
                    @RequestParam(value = "pageNo", defaultValue = "1") Integer pageNo,
                    @RequestParam(value = "pageSize", defaultValue = "10") Integer pageSize,
                    @RequestParam(value = "content", required = false) String content,
                    @RequestParam(required = false) String orderBy,
                    @RequestParam(value = "tag", required = false) String tag,
                    HttpServletRequest request) {
        return ApiResult.success(repoService.list(pageNo, pageSize, content, orderBy, request, tag));
    }

    /**
     * Get detailed repository information
     * <p>
     * Retrieves comprehensive information about a specific repository including metadata,
     * configuration, statistics, and associated files.
     * </p>
     *
     * @param id the unique identifier of the repository to retrieve
     * @param tag optional repository tag for version or environment specification (defaults to empty
     *        string)
     * @param request the HTTP servlet request containing user context and authentication info
     * @return ApiResult containing detailed repository information as RepoDto
     * @throws IllegalArgumentException if the repository ID is invalid or not found
     * @throws RuntimeException if repository detail retrieval fails due to system error
     */
    @GetMapping("/detail")
    @SpacePreAuth(key = "RepoController_detail_GET",
                    module = "Knowledge Base", point = "Repository Detail", description = "Repository Detail")
    public ApiResult<RepoDto> getDetail(@RequestParam("id") Long id, @RequestParam(value = "tag", defaultValue = "") String tag, HttpServletRequest request) {
        return ApiResult.success(repoService.getDetail(id, tag, request));
    }

    /**
     * Perform hit test on repository content
     * <p>
     * Tests the repository's search capability by executing a query against its indexed content.
     * Returns the most relevant matches to help evaluate the repository's search performance.
     * </p>
     *
     * @param id the unique identifier of the repository to test
     * @param query the search query string to test against repository content
     * @param topN the maximum number of top matching results to return (defaults to 3)
     * @return Object containing hit test results with relevance scores and matched content
     * @throws IllegalArgumentException if repository ID is invalid or query is empty
     * @throws RuntimeException if hit test execution fails due to system error
     */
    @GetMapping("/hit-test")
    public Object hitTest(
                    @RequestParam("id") Long id,
                    @RequestParam("query") String query,
                    @RequestParam(value = "topN", defaultValue = "3") Integer topN) {
        return repoService.hitTest(id, query, topN, true);
    }

    /**
     * List hit test history with pagination
     * <p>
     * Retrieves the historical record of hit tests performed on a specific repository. Provides
     * paginated access to test queries, results, and execution timestamps.
     * </p>
     *
     * @param repoId the unique identifier of the repository whose hit test history to retrieve
     * @param pageNo the page number to retrieve (1-based indexing, defaults to 1)
     * @param pageSize the number of history records per page (defaults to 10)
     * @return ApiResult containing PageData with hit test history records and pagination metadata
     * @throws IllegalArgumentException if repository ID is invalid or pagination parameters are invalid
     * @throws RuntimeException if hit test history retrieval fails due to system error
     */
    @GetMapping("/list-hit-test-history-by-page")
    public ApiResult<PageData<HitTestHistory>> listHitTestHistoryByPage(
                    @RequestParam(value = "repoId") Long repoId,
                    @RequestParam(value = "pageNo", defaultValue = "1") Integer pageNo,
                    @RequestParam(value = "pageSize", defaultValue = "10") Integer pageSize) {
        return ApiResult.success(repoService.listHitTestHistoryByPage(repoId, pageNo, pageSize));
    }

    /**
     * Enable or disable a repository
     * <p>
     * Toggles the enabled/disabled state of a repository, affecting its availability for search
     * operations and content management. Disabled repositories remain in the system but are not
     * accessible for queries.
     * </p>
     *
     * @param id the unique identifier of the repository to enable or disable
     * @param enabled integer flag indicating desired state (1 to enable, 0 to disable)
     * @return ApiResult with Void data indicating operation success
     * @throws IllegalArgumentException if repository ID is invalid or enabled flag is not 0 or 1
     * @throws RuntimeException if repository enable/disable operation fails due to system error
     */
    @PutMapping("/enable-repo")
    @SpacePreAuth(key = "RepoController_enableRepo_PUT",
                    module = "Knowledge Base", point = "Enable Repository", description = "Enable Repository")
    public ApiResult<Void> enableRepo(@RequestParam("id") Long id, @RequestParam("enabled") Integer enabled) {
        repoService.enableRepo(id, enabled);
        return ApiResult.success();
    }

    /**
     * Delete a repository permanently
     * <p>
     * Permanently removes a repository and all its associated data including files, indexes, and
     * metadata. This operation cannot be undone and will affect any systems or applications that depend
     * on this repository.
     * </p>
     *
     * @param id the unique identifier of the repository to delete
     * @param tag optional repository tag for version or environment specification
     * @param request the HTTP servlet request containing user context and authentication info
     * @return Object containing deletion operation result and status information
     * @throws IllegalArgumentException if repository ID is invalid or repository not found
     * @throws RuntimeException if repository deletion fails due to system error or dependencies
     */
    @DeleteMapping("/delete-repo")
    @SpacePreAuth(key = "RepoController_deleteRepo_DELETE",
                    module = "Knowledge Base", point = "Delete Repository", description = "Delete Repository")
    public Object deleteRepo(@RequestParam("id") Long id, String tag, HttpServletRequest request) {
        return repoService.deleteRepo(id, tag, request);
    }


    /**
     * Set repository to top priority
     * <p>
     * Marks a repository as high priority, typically affecting its display order in lists and search
     * results. Top repositories are usually shown first in user interfaces for better accessibility.
     * </p>
     *
     * @param id the unique identifier of the repository to set as top priority
     * @return Object containing operation result and updated priority information
     * @throws IllegalArgumentException if repository ID is invalid or repository not found
     * @throws RuntimeException if priority update fails due to system error
     */
    @GetMapping("/set-top")
    public Object setTop(@RequestParam("id") Long id) {
        repoService.setTop(id);
        return ApiResult.success();
    }

    /**
     * List all files contained in a repository
     * <p>
     * Retrieves a comprehensive list of all files stored in the specified repository, including file
     * metadata such as names, sizes, upload dates, and processing status.
     * </p>
     *
     * @param id the unique identifier of the repository whose files to list
     * @return Object containing file list data with metadata and file information
     * @throws IllegalArgumentException if repository ID is invalid or repository not found
     * @throws RuntimeException if file listing fails due to system error or access issues
     */
    @GetMapping("/file-list")
    public Object listFiles(@RequestParam("id") Long id) {
        return repoService.listFiles(id);
    }

    /**
     * Get repository usage status and statistics
     * <p>
     * Retrieves comprehensive usage statistics and status information for a repository, including
     * storage utilization, query frequency, active connections, and performance metrics. This
     * information is useful for monitoring and resource planning.
     * </p>
     *
     * @param repoId the unique identifier of the repository whose usage status to retrieve
     * @param request the HTTP servlet request containing user context and authentication info
     * @return Object containing detailed repository usage status and statistics
     * @throws IllegalArgumentException if repository ID is invalid or repository not found
     * @throws RuntimeException if usage status retrieval fails due to system error
     */
    @GetMapping("/get-repo-use-status")
    public Object getRepoUseStatus(Long repoId, HttpServletRequest request) {
        return repoService.getRepoUseStatus(repoId, request);
    }
}
