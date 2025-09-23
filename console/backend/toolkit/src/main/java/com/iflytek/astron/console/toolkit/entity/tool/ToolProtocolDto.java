package com.iflytek.astron.console.toolkit.entity.tool;

import lombok.Data;

@Data
public class ToolProtocolDto {
    ToolHeader header;
    ToolParameter parameter;
    ToolPayload payload;
}
