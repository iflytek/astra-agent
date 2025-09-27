package com.iflytek.astron.console.toolkit.entity.tool;

import com.alibaba.fastjson2.annotation.JSONField;
import lombok.Data;

@Data
public class ToolParameter {
    @JSONField(name = "tool_id")
    String toolId;
    @JSONField(name = "operation_id")
    String operationId;
}
