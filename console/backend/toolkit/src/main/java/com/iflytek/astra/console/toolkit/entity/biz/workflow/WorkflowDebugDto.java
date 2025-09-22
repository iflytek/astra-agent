package com.iflytek.astra.console.toolkit.entity.biz.workflow;


import lombok.Data;

@Data
public class WorkflowDebugDto {
    /**
     * aka flow id
     */
    String flowId;
    String name;
    String description;
    BizWorkflowData data;
}
