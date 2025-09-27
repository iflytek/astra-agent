package com.iflytek.astron.console.toolkit.entity.biz.workflow;

import com.alibaba.fastjson2.JSONObject;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

@Data
public class ChatBizReq {
    @JsonProperty("flow_id")
    String flowId;
    JSONObject inputs;
    String chatId;
    Boolean debugger;
    Boolean close;
    Boolean regen;
    Integer outputType;
    String version;
    Boolean promptDebugger = false;
}
