package com.iflytek.astra.console.toolkit.entity.botConfigProtocol;

import com.alibaba.fastjson2.annotation.JSONField;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.io.Serializable;
import java.util.List;

@Data
public class ModelProperty implements Serializable {
    String api;

    @JSONField(name = "patch_id")
    @JsonProperty("patch_id")
    List<String> patchId;

    String domain;

    @JSONField(name = "support_function_call")
    @JsonProperty("support_function_call")
    Boolean supportFunctionCall = false;

    ModelParameter parameter;

    String sk;

}
