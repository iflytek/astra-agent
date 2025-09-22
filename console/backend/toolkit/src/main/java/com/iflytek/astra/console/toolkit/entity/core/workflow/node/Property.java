package com.iflytek.astra.console.toolkit.entity.core.workflow.node;

import lombok.Data;

import java.util.Map;

@Data
public class Property {
    Map<String, Property> properties;
    String type;
    Property items;
    Object required;
}
