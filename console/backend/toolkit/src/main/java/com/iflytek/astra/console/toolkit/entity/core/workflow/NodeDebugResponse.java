package com.iflytek.astra.console.toolkit.entity.core.workflow;

import com.alibaba.fastjson2.JSONObject;
import lombok.Data;

@Data
public class NodeDebugResponse {
    Integer code;
    String message;
    String sid;
    Object data;
}
