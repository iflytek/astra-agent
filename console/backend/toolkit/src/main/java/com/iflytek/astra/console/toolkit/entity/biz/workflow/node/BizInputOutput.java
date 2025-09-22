package com.iflytek.astra.console.toolkit.entity.biz.workflow.node;

import lombok.Data;

import java.util.List;

@Data
public class BizInputOutput {
    // Random string ID required by frontend
    String id;
    // Variable name
    String name;
    String nameErrMsg;
    // Variable constraints
    BizSchema schema;
    List<String> allowedFileType;
    String fileType;
    String description;
    Boolean required;
    Object refId;
    Object deleteDisabled;
    Object disabled;
    String customParameterType;
}
