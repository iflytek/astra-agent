package com.iflytek.astra.console.toolkit.entity.tool;

import lombok.Data;

@Data
public class ToolResp {
    Integer code;
    String message;
    String sid;
    Object data;
}
