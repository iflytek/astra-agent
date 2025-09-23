package com.iflytek.astron.console.toolkit.entity.vo;

import lombok.Data;

@Data
public class WorkflowListVo {

    private Long id;
    private Long workflowId;
    private String name;
    private String flowId;
    private String description;
    private Boolean isCanPublish;
    private Boolean isLLm;
    private Boolean isMultiParams;
}
