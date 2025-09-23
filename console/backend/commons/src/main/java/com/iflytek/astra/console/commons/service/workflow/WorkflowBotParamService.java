package com.iflytek.astra.console.commons.service.workflow;

import com.alibaba.fastjson2.JSONObject;

import java.util.List;

public interface WorkflowBotParamService {
    void handleSingleParam(String uid, Long chatId, String sseId, Long leftId, String fileUrl,
            JSONObject extraInputs, Long reqId, JSONObject inputs, Integer botId);

    boolean handleMultiFileParam(String uid, Long chatId, Long leftId, List<JSONObject> extraInputsConfig, JSONObject inputs, Long reqId);
}
