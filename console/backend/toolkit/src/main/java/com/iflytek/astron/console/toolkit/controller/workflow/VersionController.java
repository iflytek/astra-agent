package com.iflytek.astron.console.toolkit.controller.workflow;

import com.alibaba.fastjson2.JSONObject;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.iflytek.astron.console.commons.response.ApiResult;
import com.iflytek.astron.console.toolkit.common.anno.ResponseResultBody;
import com.iflytek.astron.console.toolkit.entity.table.workflow.WorkflowVersion;
import com.iflytek.astron.console.toolkit.service.workflow.VersionService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

/**
 * REST controller for managing workflow versions.
 * <p>
 * Provides APIs for listing, creating, restoring, updating, and querying workflow version
 * information.
 * </p>
 */
@RestController
@RequestMapping("/workflow/version")
@Slf4j
@ResponseResultBody
@Tag(name = "Workflow version management interface")
public class VersionController {

    @Resource
    VersionService versionService;

    /**
     * Query workflow versions by flowId with pagination.
     *
     * @param page pagination information
     * @param flowId workflow identifier
     * @return paginated list of workflow versions
     * @throws IllegalArgumentException if {@code flowId} is blank
     */
    @GetMapping("/list")
    public Object list(Page<WorkflowVersion> page,
            @RequestParam String flowId) {
        return versionService.listPage(page, flowId);
    }

    /**
     * Query workflow versions by botId with pagination.
     *
     * @param page pagination information
     * @param botId bot identifier
     * @return paginated list of workflow versions
     * @throws IllegalArgumentException if {@code botId} is blank
     */
    @GetMapping("/list-botId")
    public Object list_botId(Page<WorkflowVersion> page,
            @RequestParam String botId) {
        return versionService.list_botId_Page(page, botId);
    }

    /**
     * Create a new workflow version.
     *
     * <p>
     * Request body fields:
     * <ul>
     * <li>flowId - workflow identifier</li>
     * <li>botId - bot identifier</li>
     * <li>name - version name</li>
     * <li>publishChannel - publish channel (1: WeChat, 2: Spark Desk, 3: API, 4: MCP)</li>
     * <li>publishResult - publish result (success / failed / reviewing)</li>
     * <li>description - version description</li>
     * </ul>
     * </p>
     *
     * @param createDto workflow version object to create
     * @return result containing created workflow version data
     * @throws IllegalArgumentException if required fields are missing
     */
    @PostMapping
    public ApiResult<JSONObject> create(@RequestBody WorkflowVersion createDto) {
        return versionService.create(createDto);
    }

    /**
     * Restore a workflow version.
     *
     * @param createDto workflow version data to restore
     * @return restore result
     * @throws IllegalArgumentException if the version does not exist
     */
    @PostMapping("/restore")
    public Object restore(@RequestBody WorkflowVersion createDto) {
        return versionService.restore(createDto);
    }

    /**
     * Update workflow version publish result.
     *
     * @param createDto workflow version data with ID and publish result
     * @return update result
     * @throws IllegalArgumentException if {@code id} is null
     */
    @PostMapping("/update-channel-result")
    public Object update_channel_result(@RequestBody WorkflowVersion createDto) {
        return versionService.update_channel_result(createDto);
    }

    /**
     * Get workflow version name.
     *
     * @param createDto workflow version filter object
     * @return version name
     * @throws IllegalArgumentException if required parameters are missing
     */
    @PostMapping("/get-version-name")
    public Object getVersionName(@RequestBody WorkflowVersion createDto) {
        return versionService.getVersionName(createDto);
    }

    /**
     * Get the maximum version number of a workflow by botId.
     *
     * @param botId bot identifier
     * @return maximum version information
     * @throws IllegalArgumentException if {@code botId} is blank
     */
    @GetMapping("/get-max-version")
    public Object getMaxVersion(@RequestParam String botId) {
        return versionService.getMaxVersion(botId);
    }

    /**
     * Get system data of a workflow version.
     *
     * @param createDto workflow version filter object
     * @return system data of the version
     * @throws IllegalArgumentException if version is not found
     */
    @PostMapping("/get-version-sys-data")
    public Object getVersionSysData(@RequestBody WorkflowVersion createDto) {
        return versionService.getVersionSysData(createDto);
    }

    /**
     * Check whether a workflow version has system data.
     *
     * @param createDto workflow version filter object
     * @return check result (true/false or equivalent object)
     * @throws IllegalArgumentException if version is not found
     */
    @PostMapping("/have-version-sys-data")
    public Object haveVersionSysData(@RequestBody WorkflowVersion createDto) {
        return versionService.haveVersionSysData(createDto);
    }

    /**
     * Query publish result of a workflow version by flowId and name.
     *
     * @param flowId workflow identifier
     * @param name version name
     * @return publish result object
     * @throws IllegalArgumentException if {@code flowId} or {@code name} is blank
     */
    @GetMapping("/publish-result")
    public Object publishResult(@RequestParam String flowId,
            @RequestParam String name) {
        return versionService.publishResult(flowId, name);
    }
}
