package com.iflytek.astron.console.toolkit.entity.core.workflow.sse;

import com.alibaba.fastjson2.JSONObject;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

@Data
public class Node {
    String id;
    @JsonProperty("alias_name")
    String aliasName;
    @JsonProperty("finish_reason")
    String finishReason;
    JSONObject inputs;
    JSONObject outputs;
    @JsonProperty("error_outputs")
    JSONObject errorOutputs;
    @JsonProperty("executed_time")
    Double executedTime;
    Usage usage;
    Object ext;
}
