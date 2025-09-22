package com.iflytek.astra.console.toolkit.entity.biz.workflow;

import lombok.Data;

import java.util.List;

/**
 * Data containing nodes and edges
 */
@Data
public class BizWorkflowData {
    List<BizWorkflowNode> nodes;
    List<BizWorkflowEdge> edges;
}
