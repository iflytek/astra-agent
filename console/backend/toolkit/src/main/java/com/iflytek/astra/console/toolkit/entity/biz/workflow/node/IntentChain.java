package com.iflytek.astra.console.toolkit.entity.biz.workflow.node;

import lombok.Data;

@Data
public class IntentChain {
    String id;
    Integer intentType;
    String name;
    String description;

    String nameErrMsg;
    String descriptionErrMsg;
}
