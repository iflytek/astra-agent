package com.iflytek.astron.console.toolkit.entity.core.openapi;

import com.alibaba.fastjson2.annotation.JSONField;
import lombok.Data;

import java.util.List;
import java.util.Map;

@Data
public class Property {
    String type;
    String description;
    Map<String, Property> properties;
    @JSONField(name = "x-from")
    Integer xFrom;
    @JSONField(name = "x-display")
    Boolean xDisplay;
    List<String> required;
    @JSONField(name = "default")
    Object dft;
    Property items;
}
