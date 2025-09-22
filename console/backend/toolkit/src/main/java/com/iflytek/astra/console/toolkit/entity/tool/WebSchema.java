package com.iflytek.astra.console.toolkit.entity.tool;

import lombok.Data;

import java.util.List;

@Data
public class WebSchema {
    @Deprecated
    List<WebSchemaItem> toolHttpHeaders;
    @Deprecated
    List<WebSchemaItem> toolUrlParams;
    @Deprecated
    List<WebSchemaItem> toolRequestBody;

    List<WebSchemaItem> toolRequestInput;

    List<WebSchemaItem> toolRequestOutput;
}
