package com.iflytek.astron.console.toolkit.controller.tool;

import com.iflytek.astron.console.commons.annotation.space.SpacePreAuth;
import com.iflytek.astron.console.commons.constant.ResponseEnum;
import com.iflytek.astron.console.commons.exception.BusinessException;
import com.iflytek.astron.console.toolkit.common.anno.ResponseResultBody;
import com.iflytek.astron.console.toolkit.entity.dto.*;
import com.iflytek.astron.console.toolkit.service.tool.ToolBoxService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/tool")
@Slf4j
@ResponseResultBody
@Tag(name = "插件管理")
public class ToolBoxController {
    @Resource
    ToolBoxService toolBoxService;

    @PostMapping("/create-tool")
    @Operation(summary = "创建插件")
    @SpacePreAuth(key = "ToolBoxController_createTool_POST")
    public Object createTool(@RequestBody ToolBoxDto toolBoxDto) {
        if (toolBoxDto.getName() == null) {
            throw new BusinessException(ResponseEnum.TOOLBOX_NAME_EMPTY);
        }
        if (toolBoxDto.getDescription() == null) {
            throw new BusinessException(ResponseEnum.TOOLBOX_NAME_EMPTY);
        }
        return toolBoxService.createTool(toolBoxDto);
    }

    @PostMapping("/temporary-tool")
    @Operation(summary = "暂存插件")
    @SpacePreAuth(key = "ToolBoxController_temporaryTool_POST")
    public Object temporaryTool(@RequestBody ToolBoxDto toolBoxDto) {
        if (toolBoxDto.getName() == null) {
            throw new BusinessException(ResponseEnum.TOOLBOX_NAME_EMPTY);
        }
        return toolBoxService.temporaryTool(toolBoxDto);
    }

    @PutMapping("/update-tool")
    @Operation(summary = "编辑插件")
    @SpacePreAuth(key = "ToolBoxController_updateTool_PUT")
    public Object updateTool(@RequestBody ToolBoxDto toolBoxDto) {
        return toolBoxService.updateTool(toolBoxDto);
    }

    @GetMapping("/list-tools")
    @Operation(summary = "插件分页列表")
    @SpacePreAuth(key = "ToolBoxController_listTools_GET")
    public Object listTools(@RequestParam(value = "pageNo", defaultValue = "1") Integer pageNo,
            @RequestParam(value = "pageSize", defaultValue = "10") Integer pageSize,
            @RequestParam(value = "content", required = false) String content,
            @RequestParam(value = "status", required = false) Integer status) {
        return toolBoxService.pageListTools(pageNo, pageSize, content, status);
    }

    @GetMapping("/detail")
    @Operation(summary = "插件详情")
    @SpacePreAuth(key = "ToolBoxController_detail_GET")
    public Object getDetail(@RequestParam("id") Long id, Boolean temporary) {
        return toolBoxService.getDetail(id, temporary);
    }

    @GetMapping("/get-tool-default-icon")
    @Operation(summary = "插件默认图标")
    @SpacePreAuth(key = "ToolBoxController_getToolDefaultIcon_GET")
    public Object getToolDefaultIcon() {
        return toolBoxService.getToolDefaultIcon();
    }

    @DeleteMapping("/delete-tool")
    @Operation(summary = "插件删除")
    @SpacePreAuth(key = "ToolBoxController_deleteTool_DELETE")
    public Object deleteTool(@RequestParam("id") Long id) {
        return toolBoxService.deleteTool(id);
    }

    @PostMapping("/debug-tool")
    @Operation(summary = "插件调试")
    @SpacePreAuth(key = "ToolBoxController_debugTool_POST")
    public Object debugToolV2(@RequestBody ToolBoxDto toolBoxDto) {
        return toolBoxService.debugToolV2(toolBoxDto);
    }

    @Operation(summary = "插件广场查询列表")
    @PostMapping("/list-tool-square")
    @SpacePreAuth(key = "ToolBoxController_listToolSquare_POST")
    public Object listToolSquare(@RequestBody ToolSquareDto dto) {
        return toolBoxService.listToolSquare(dto);
    }

    @Operation(summary = "收藏/取消收藏工具")
    @GetMapping("/favorite")
    @SpacePreAuth(key = "ToolBoxController_favorite_GET")
    public Object favorite(@RequestParam("toolId") String toolId,
            @RequestParam("favoriteFlag") Integer favoriteFlag,
            @RequestParam("isMcp") Boolean isMcp) {
        return toolBoxService.favorite(toolId, favoriteFlag, isMcp);
    }

    @Operation(summary = "获取插件历史版本")
    @GetMapping("/get-tool-version")
    @SpacePreAuth(key = "ToolBoxController_getToolVersion_GET")
    public List<ToolBoxVo> getToolVersion(@RequestParam("toolId") String toolId) {
        return toolBoxService.getToolVersion(toolId);
    }

    @Operation(summary = "获取插件最新版本")
    @GetMapping("/get-tool-latestVersion")
    @SpacePreAuth(key = "ToolBoxController_getToolLatestVersion_GET")
    public Map<String, String> getToolLatestVersion(@RequestParam("toolIds") List<String> toolIds) {
        return toolBoxService.getToolLatestVersion(toolIds);
    }

    @Operation(summary = "插件用户操作记录")
    @GetMapping("/add-tool-operateHistory")
    public void addToolOperateHistory(@RequestParam("toolId") String toolId) {
        toolBoxService.addToolOperateHistory(toolId);
    }

    @Operation(summary = "用户反馈")
    @PostMapping("/feedback")
    public void addToolOperateHistory(@RequestBody ToolBoxFeedbackReq toolBoxFeedbackReq) {
        toolBoxService.feedback(toolBoxFeedbackReq);
    }

    @Operation(summary = "工具发布到广场")
    @GetMapping("/publish-square")
    public void publishSquare(Long id) {
        toolBoxService.publishSquare(id);
    }

}
