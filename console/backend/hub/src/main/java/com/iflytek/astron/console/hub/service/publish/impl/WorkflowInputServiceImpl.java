package com.iflytek.astron.console.hub.service.publish.impl;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import com.iflytek.astron.console.commons.constant.ResponseEnum;
import com.iflytek.astron.console.commons.exception.BusinessException;
import com.iflytek.astron.console.commons.dto.workflow.WorkflowInputsResponseDto;
import com.iflytek.astron.console.commons.entity.bot.UserLangChainInfo;
import com.iflytek.astron.console.commons.mapper.bot.ChatBotBaseMapper;
import com.iflytek.astron.console.commons.mapper.UserLangChainInfoMapper;
import com.iflytek.astron.console.hub.service.publish.WorkflowInputService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Workflow Input Service Implementation
 *
 * @author Omuigix
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class WorkflowInputServiceImpl implements WorkflowInputService {

    private final ChatBotBaseMapper chatBotBaseMapper;
    private final UserLangChainInfoMapper userLangChainInfoMapper;

    @Override
    public WorkflowInputsResponseDto getInputsType(Integer botId, String currentUid, Long spaceId) {
        log.info("Get workflow input parameter types: botId={}, uid={}, spaceId={}", botId, currentUid, spaceId);

        // 1. Permission verification
        int hasPermission = chatBotBaseMapper.checkBotPermission(botId, currentUid, spaceId);
        if (hasPermission == 0) {
            throw new BusinessException(ResponseEnum.BOT_NOT_EXISTS);
        }

        // 2. Query workflow configuration information
        UserLangChainInfo chainInfo = userLangChainInfoMapper.selectOne(
                new com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<UserLangChainInfo>()
                        .eq("bot_id", botId)
                        .orderByDesc("create_time")
                        .last("LIMIT 1"));
        if (chainInfo == null) {
            log.warn("Bot workflow configuration not found: botId={}", botId);
            throw new BusinessException(ResponseEnum.BOT_CHAIN_SUBMIT_ERROR);
        }

        log.info("Workflow configuration info: botId={}, flowId={}", botId, chainInfo.getFlowId());

        // 3. Parse additional input parameters
        List<WorkflowInputsResponseDto.InputParameter> parameters = new ArrayList<>();

        try {
            // Parse extraInputs field (JSON format additional input parameters)
            String extraInputs = chainInfo.getExtraInputs();
            if (extraInputs != null && !extraInputs.trim().isEmpty()) {
                // Try to parse as JSONObject or JSONArray
                Object parsedInputs = JSON.parse(extraInputs);
                if (parsedInputs instanceof JSONArray) {
                    JSONArray inputArray = (JSONArray) parsedInputs;
                    for (int i = 0; i < inputArray.size(); i++) {
                        JSONObject input = inputArray.getJSONObject(i);
                        parameters.add(convertToInputParameter(input));
                    }
                } else if (parsedInputs instanceof JSONObject) {
                    JSONObject inputObj = (JSONObject) parsedInputs;
                    parameters.add(convertToInputParameter(inputObj));
                }
            }

            // Filter out fixed input parameter AGENT_USER_INPUT
            parameters.removeIf(param -> "AGENT_USER_INPUT".equals(param.getName()));

        } catch (Exception e) {
            log.warn("Failed to parse workflow input parameters: botId={}, extraInputs={}", botId, chainInfo.getExtraInputs(), e);
            // If parsing fails, return empty parameter list
        }

        // 4. Build response
        WorkflowInputsResponseDto response = new WorkflowInputsResponseDto();
        response.setParameters(parameters);

        log.info("Workflow input parameter types retrieved successfully: botId={}, paramCount={}", botId, parameters.size());
        return response;
    }

    /**
     * Convert JSONObject to InputParameter
     */
    private WorkflowInputsResponseDto.InputParameter convertToInputParameter(JSONObject input) {
        WorkflowInputsResponseDto.InputParameter parameter = new WorkflowInputsResponseDto.InputParameter();

        parameter.setId(input.getString("id"));
        parameter.setName(input.getString("name"));
        parameter.setType(input.getString("type"));
        parameter.setRequired(input.getBoolean("required"));
        parameter.setDescription(input.getString("description"));
        parameter.setDeleteDisabled(input.getBoolean("deleteDisabled"));
        parameter.setNameErrMsg(input.getString("nameErrMsg"));

        // Process schema field
        Object schema = input.get("schema");
        if (schema instanceof JSONObject) {
            parameter.setSchema(((JSONObject) schema).toJavaObject(Map.class));
        } else if (schema instanceof Map) {
            parameter.setSchema((Map<String, Object>) schema);
        } else {
            parameter.setSchema(new HashMap<>());
        }

        return parameter;
    }
}
