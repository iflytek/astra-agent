package com.iflytek.astra.console.toolkit.controller.workflow;

import com.alibaba.fastjson2.JSONObject;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.iflytek.astra.console.commons.response.ApiResult;
import com.iflytek.astra.console.toolkit.common.anno.ResponseResultBody;
import com.iflytek.astra.console.toolkit.entity.table.workflow.WorkflowVersion;
import com.iflytek.astra.console.toolkit.service.workflow.VersionService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;


@RestController
@RequestMapping("/workflow/version")
@Slf4j
@ResponseResultBody
@Tag(name = "Workflow version management interface")
public class VersionController {
    @Resource
    VersionService versionService;

    /**
     * 查询工作流版本
     *
     * @param flowId flowId
     * @param page 分页查询
     * @return
     */
    @GetMapping("/list")
    public Object list(
                    Page<WorkflowVersion> page,
                    @RequestParam String flowId) {
        return versionService.listPage(page, flowId);
    }

    @GetMapping("/list-botId")
    public Object list_botId(
                    Page<WorkflowVersion> page,
                    @RequestParam String botId) {
        return versionService.list_botId_Page(page, botId);
    }

    /**
     * 创建工作流版本 入参 createDto：新增参数： String flowId flowId String botId botId String name 版本名称 Long
     * publishChannel 工作流发布渠道信息 枚举值 1：微信公众号 2：星火desk 3:api 4:MCP String publishResult 工作流发布数据 3种枚举值： 成功
     * 失败 审核中 String description 工作流发布数描述
     */
    @PostMapping
    public ApiResult<JSONObject> create(@RequestBody WorkflowVersion createDto) {
        return versionService.create(createDto);
    }

    // 工作流版本还原
    @PostMapping("/restore")
    public Object restore(@RequestBody WorkflowVersion createDto) {
        return versionService.restore(createDto);
    }

    /**
     * 更新版本结果 接受参数： Long id String publishResult
     */
    @PostMapping("/update-channel-result")
    public Object update_channel_result(@RequestBody WorkflowVersion createDto) {
        return versionService.update_channel_result(createDto);
    }

    @PostMapping("/get-version-name")
    public Object getVersionName(@RequestBody WorkflowVersion createDto) {
        return versionService.getVersionName(createDto);
    }

    @GetMapping("/get-max-version")
    public Object getMaxVersion(@RequestParam String botId) {
        return versionService.getMaxVersion(botId);
    }

    @PostMapping("/get-version-sys-data")
    public Object getVersionSysData(@RequestBody WorkflowVersion createDto) {
        return versionService.getVersionSysData(createDto);
    }

    @PostMapping("/have-version-sys-data")
    public Object haveVersionSysData(@RequestBody WorkflowVersion createDto) {
        return versionService.haveVersionSysData(createDto);
    }

    @GetMapping("/publish-result")
    public Object publishResult(@RequestParam String flowId,
                    @RequestParam String name) {
        return versionService.publishResult(flowId, name);
    }
}
