package com.iflytek.astron.console.toolkit.entity.table.workflow.node;

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
