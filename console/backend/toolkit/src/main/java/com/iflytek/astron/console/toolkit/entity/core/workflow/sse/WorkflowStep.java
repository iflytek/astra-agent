package com.iflytek.astron.console.toolkit.entity.core.workflow.sse;

import lombok.Data;

@Data
public class WorkflowStep {
    Node node;
    Integer seq;
    Integer progress;
}
