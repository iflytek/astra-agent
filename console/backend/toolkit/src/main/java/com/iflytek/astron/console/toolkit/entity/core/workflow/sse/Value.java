package com.iflytek.astron.console.toolkit.entity.core.workflow.sse;

import com.alibaba.fastjson2.JSONArray;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

@Data
public class Value {
    @JsonProperty("type")
    String type;
    @JsonProperty("option")
    JSONArray option;
    @JsonProperty("content")
    String content;
}
