package com.iflytek.astra.console.toolkit.entity.core.workflow.sse;

import lombok.Data;

@Data
public class WorkflowStep {
    Node node;
    Integer seq;
    Integer progress;
}
