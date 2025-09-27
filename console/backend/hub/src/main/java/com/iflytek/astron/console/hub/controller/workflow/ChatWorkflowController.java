package com.iflytek.astron.console.hub.controller.workflow;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import com.iflytek.astron.console.commons.constant.ResponseEnum;
import com.iflytek.astron.console.commons.response.ApiResult;
import com.iflytek.astron.console.commons.util.space.SpaceInfoUtil;
import com.iflytek.astron.console.commons.service.bot.ChatBotDataService;
import com.iflytek.astron.console.commons.service.data.UserLangChainDataService;
import com.iflytek.astron.console.commons.entity.workflow.WorkflowInfoDto;
import com.iflytek.astron.console.commons.entity.bot.ChatBotBase;
import com.iflytek.astron.console.commons.entity.bot.UserLangChainInfo;
import com.iflytek.astron.console.toolkit.service.workflow.WorkflowService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.*;

/**
 * Workflow related
 *
 * @author yingpeng
 */
@Slf4j
@RestController
@RequestMapping(value = "/workflow/web")
public class ChatWorkflowController {

    @Autowired
    private ChatBotDataService chatBotDataService;

    @Autowired
    private UserLangChainDataService userLangChainDataService;

    @Autowired
    private WorkflowService workflowService;

    @GetMapping(value = "/info")
    public ApiResult<WorkflowInfoDto> info(@RequestParam Integer botId) {
        WorkflowInfoDto workflowInfo = new WorkflowInfoDto();
        ChatBotBase botBase = chatBotDataService.findById(botId).orElse(null);
        if (botBase == null) {
            return ApiResult.error(ResponseEnum.BOT_NOT_EXIST);
        }
        workflowInfo.setOpenedTool(botBase.getOpenedTool());

        List<UserLangChainInfo> botList = userLangChainDataService.findListByBotId(botId);
        if (Objects.isNull(botList) || botList.isEmpty()) {
            log.info("***** source assistant does not exist, id: {}", botId);
            return ApiResult.success(workflowInfo);
        }

        // Handle workflow tool usage
        try {
            String flowId = botList.getFirst().getFlowId();
            Object detail = workflowService.detail(flowId, SpaceInfoUtil.getSpaceId());
            JSONObject dataObj = JSON.parseObject(JSONObject.toJSONString(detail));
            // Parse nested JSON string again
            JSONArray nodes = dataObj.getJSONObject("data").getJSONArray("nodes");
            List<String> idPrefixes = new ArrayList<>();
            // Extract id prefix from nodes
            for (int i = 0; i < nodes.size(); i++) {
                JSONObject node = nodes.getJSONObject(i);
                String id = node.getString("id");
                if (id != null) {
                    int index = id.indexOf("::");
                    if (index > 0) {
                        String tool = id.substring(0, index);
                        if ("spark-llm".equalsIgnoreCase(tool)) {
                            JSONObject nodeParam = node.getJSONObject("data").getJSONObject("nodeParam");
                            // Extract model field value
                            String serviceId = nodeParam.getString("serviceId");
                            idPrefixes.add(serviceId);

                        } else {
                            idPrefixes.add(tool);
                        }
                    }
                }
            }
            workflowInfo.setConfig(idPrefixes);
        } catch (Exception e) {
            log.info("Configuration processing exception", e);
        }

        return ApiResult.success(workflowInfo);
    }
}
