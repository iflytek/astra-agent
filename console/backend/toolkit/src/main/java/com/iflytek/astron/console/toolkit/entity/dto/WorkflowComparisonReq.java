package com.iflytek.astron.console.toolkit.entity.dto;

import com.iflytek.astron.console.toolkit.entity.biz.workflow.BizWorkflowData;
import lombok.Data;

@Data
public class WorkflowComparisonReq {

    Long id;

    String flowId;

    BizWorkflowData data;

    String version;

    String name;

    Integer type;

    String promptId;
}
