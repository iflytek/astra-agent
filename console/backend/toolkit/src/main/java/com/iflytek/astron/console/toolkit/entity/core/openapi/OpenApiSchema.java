package com.iflytek.astron.console.toolkit.entity.core.openapi;

import lombok.Data;

import java.util.List;
import java.util.Map;

@Data
public class OpenApiSchema {
    String openapi = "3.1.0";
    Info info;
    List<Server> servers;
    Map<String, Map<String, Operation>> paths;
    Components components;
}
