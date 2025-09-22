package com.iflytek.astra.console.hub.controller.workflow;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import com.iflytek.astra.console.commons.constant.ResponseEnum;
import com.iflytek.astra.console.commons.entity.bot.BotCreateForm;
import com.iflytek.astra.console.commons.entity.bot.BotInfoDto;
import com.iflytek.astra.console.commons.entity.bot.UserLangChainInfo;
import com.iflytek.astra.console.commons.entity.workflow.WorkflowInputTypeDto;
import com.iflytek.astra.console.commons.response.ApiResult;
import com.iflytek.astra.console.commons.service.data.UserLangChainDataService;
import com.iflytek.astra.console.commons.util.MaasUtil;
import com.iflytek.astra.console.commons.util.RequestContextUtil;
import com.iflytek.astra.console.hub.entity.WorkflowTemplateGroup;
import com.iflytek.astra.console.hub.entity.maas.MaasDuplicate;
import com.iflytek.astra.console.hub.entity.maas.MaasTemplate;
import com.iflytek.astra.console.hub.entity.maas.WorkflowTemplateQueryDto;
import com.iflytek.astra.console.hub.service.workflow.BotMaasService;
import com.iflytek.astra.console.hub.service.workflow.WorkflowTemplateGroupService;
import com.iflytek.astra.console.hub.util.BotPermissionUtil;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
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
    @Operation(summary = "work flow template", description = "获取星辰工作流的分组信息")
    public ApiResult<List<WorkflowTemplateGroup>> templateGroup(HttpServletRequest request) {
        // 拦截器进行登录校验
        return ApiResult.success(workflowTemplateGroupService.getTemplateGroup());
    }

    @Operation(summary = "work flow template", description = "根据模板创建工作流助手")
    @PostMapping("/createFromTemplate")
    @Transactional(rollbackFor = Exception.class)
    public ApiResult<BotInfoDto> createFromTemplate(@RequestBody MaasDuplicate maasDuplicate) {
        String uid = RequestContextUtil.getUID();
        return ApiResult.success(botMaasService.createFromTemplate(uid, maasDuplicate));
    }

    @PostMapping("/templateList")
    @Operation(summary = "work flow template", description = "获取星辰工作流的模版")
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
