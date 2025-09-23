package com.iflytek.astron.console.toolkit.entity.biz.workflow.node;

import com.alibaba.fastjson2.annotation.JSONField;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.util.List;

@Data
public class BizProperty {
    String id;
    String name;
    @JSONField(name = "default")
    @JsonProperty("default")
    String dft;
    Boolean required;
    String type;
    List<BizProperty> properties;
}
