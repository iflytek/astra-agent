package com.iflytek.astra.console.toolkit.entity.core.openapi;


import com.alibaba.fastjson2.annotation.JSONField;
import lombok.Data;

import java.util.List;
import java.util.Map;

@Data
public class Schema {
    String type;
    List<String> required;
    Map<String, Property> properties;
    @JSONField(name = "x-from")
    Integer xFrom;
    @JSONField(name = "default")
    Object dft;
    @JSONField(name = "x-display")
    Boolean xDisplay;
    Property items;
}
