package com.iflytek.astra.console.toolkit.entity.tool;

import lombok.Data;

import java.util.List;

@Data
public class ToolPayload {
    List<Tool> tools;
    Message message;

    // Tool run resp
    Text text;
}
