package com.iflytek.astron.console.toolkit.entity.core.openapi;

import lombok.Data;

import java.util.List;
import java.util.Map;

@Data
public class Operation {
    String summary;
    String description;
    String operationId;
    List<Parameter> parameters;
    RequestBody requestBody;
    Map<String, Response> responses;
    List<Map<String, Object>> security;
}
