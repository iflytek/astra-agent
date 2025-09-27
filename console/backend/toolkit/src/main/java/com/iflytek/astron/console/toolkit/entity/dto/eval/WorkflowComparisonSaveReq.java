package com.iflytek.astron.console.toolkit.entity.dto.eval;

import lombok.Data;

import java.util.Map;

@Data
public class WorkflowComparisonSaveReq {

    String flowId;

    Map<String, Object> data;

    Integer type;

    String promptId;
}
