package com.iflytek.astra.console.toolkit.entity.tool;

import com.alibaba.fastjson2.annotation.JSONField;
import lombok.Data;

@Data
public class Tool {
    String id;
    @JSONField(name = "schema_type")
    Integer schemaType;
    String name;
    String description;
    @JSONField(name = "openapi_schema")
    String openapiSchema;
    String version;
}
