package com.iflytek.astron.console.hub.controller.workflow;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import com.iflytek.astron.console.commons.constant.ResponseEnum;
import com.iflytek.astron.console.commons.entity.bot.BotCreateForm;
import com.iflytek.astron.console.commons.entity.bot.BotInfoDto;
import com.iflytek.astron.console.commons.entity.bot.UserLangChainInfo;
import com.iflytek.astron.console.commons.entity.workflow.WorkflowInputTypeDto;
import com.iflytek.astron.console.commons.response.ApiResult;
import com.iflytek.astron.console.commons.service.data.UserLangChainDataService;
import com.iflytek.astron.console.commons.util.MaasUtil;
import com.iflytek.astron.console.commons.util.RequestContextUtil;
import com.iflytek.astron.console.hub.entity.WorkflowTemplateGroup;
import com.iflytek.astron.console.hub.entity.maas.MaasDuplicate;
import com.iflytek.astron.console.hub.entity.maas.MaasTemplate;
import com.iflytek.astron.console.hub.entity.maas.WorkflowTemplateQueryDto;
import com.iflytek.astron.console.hub.service.workflow.BotMaasService;
import com.iflytek.astron.console.hub.service.workflow.WorkflowTemplateGroupService;
import com.iflytek.astron.console.hub.util.BotPermissionUtil;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Workflow related
 *
 * @author cherry
 */
@Slf4j
@Tag(name = "Workflow Assistant Interface")
@RestController
@RequestMapping(value = "/workflow/bot")
public class WorkflowBotController {

    @Autowired
    private WorkflowTemplateGroupService workflowTemplateGroupService;

    @Autowired
    private BotMaasService botMaasService;

    @Autowired
    private BotPermissionUtil botPermissionUtil;

    @Autowired
    private UserLangChainDataService userLangChainDataService;

    @Autowired
    private MaasUtil maasUtil;

    @GetMapping("/templateGroup")
    @Operation(summary = "work flow template", description = "Get workflow group information")
    public ApiResult<List<WorkflowTemplateGroup>> templateGroup(HttpServletRequest request) {
        // Interceptor performs login verification
        return ApiResult.success(workflowTemplateGroupService.getTemplateGroup());
    }

    @Operation(summary = "work flow template", description = "Create workflow assistant from template")
    @PostMapping("/createFromTemplate")
    @Transactional(rollbackFor = Exception.class)
    public ApiResult<BotInfoDto> createFromTemplate(@RequestBody MaasDuplicate maasDuplicate) {
        String uid = RequestContextUtil.getUID();
        return ApiResult.success(botMaasService.createFromTemplate(uid, maasDuplicate));
    }

    @PostMapping("/templateList")
    @Operation(summary = "work flow template", description = "Get workflow templates")
    public ApiResult<List<MaasTemplate>> templateList(HttpServletRequest request,
            @RequestBody WorkflowTemplateQueryDto queryDto) {
        return ApiResult.success(botMaasService.templateList(queryDto));
    }

    @PostMapping("/get-inputs-type")
    public ApiResult<List<WorkflowInputTypeDto>> getInputsType(HttpServletRequest request, @RequestBody BotCreateForm bot) {
        Integer botId = bot.getBotId();
        botPermissionUtil.checkBot(botId);
        List<UserLangChainInfo> chainInfo = userLangChainDataService.findListByBotId(botId);
        log.info("user long chain info:{}", JSON.toJSONString(chainInfo));
        if (chainInfo == null || chainInfo.isEmpty()) {
            return ApiResult.error(ResponseEnum.ACTIVITY_NOT_FOUND_ERROR);
        }
        String authorizationHeader = MaasUtil.getAuthorizationHeader(request);
        JSONObject data = maasUtil.getInputsType(botId, chainInfo.getFirst(), authorizationHeader);
        List<WorkflowInputTypeDto> args = data.getJSONArray("data").toJavaList(WorkflowInputTypeDto.class);
        return ApiResult.success(args);
    }
}
