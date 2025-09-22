package com.iflytek.astra.console.toolkit.entity.core.workflow.node;

import com.alibaba.fastjson2.annotation.JSONField;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.util.Map;

@Data
public class Schema {
    String type;
    Value value;
    @JSONField(name = "default")
    @JsonProperty("default")
    Object dft;

    String description;

    // Output-specific fields
    Map<String, Property> properties;
    Property items;
    Object required;
}
