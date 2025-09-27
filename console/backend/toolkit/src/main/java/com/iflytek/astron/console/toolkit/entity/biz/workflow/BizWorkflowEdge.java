package com.iflytek.astron.console.toolkit.entity.biz.workflow;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

@Data
public class BizWorkflowEdge {
    String source;
    String sourceHandle;
    String target;
    String targetHandle;
    Object type;
    String id;
    Object markerEnd;
    @JsonProperty("zIndex")
    Object zIndex;
    Object data;
}
