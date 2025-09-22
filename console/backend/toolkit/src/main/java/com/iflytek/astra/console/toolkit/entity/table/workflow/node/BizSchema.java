package com.iflytek.astra.console.toolkit.entity.table.workflow.node;

import com.alibaba.fastjson2.JSONObject;
import com.alibaba.fastjson2.annotation.JSONField;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.util.List;

@Data
public class BizSchema {
    String type;
    BizValue value;
    @JSONField(name = "default")
    @JsonProperty("default")
    String dft;

    JSONObject item;
    String description;
    List<BizProperty> properties;
}
