package com.iflytek.astron.console.toolkit.entity.core.workflow;

import lombok.Data;

@Data
public class NodeDebugResponse {
    Integer code;
    String message;
    String sid;
    Object data;
}
