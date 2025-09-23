package com.iflytek.astron.console.toolkit.entity.tool;

import com.alibaba.fastjson2.JSONObject;
import com.alibaba.fastjson2.annotation.JSONField;
import lombok.Data;

@Data
public class ToolDebugRequest {
    String server;
    String method;
    Object path;
    JSONObject query;
    JSONObject header;
    JSONObject body;

    @JSONField(name = "response_schema")
    Object responseSchema;

    @JSONField(name = "openapi_schema")
    String openapiSchema;
}
