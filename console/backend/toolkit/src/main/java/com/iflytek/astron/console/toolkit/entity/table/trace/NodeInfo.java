package com.iflytek.astron.console.toolkit.entity.table.trace;

import com.baomidou.mybatisplus.annotation.*;
import com.iflytek.astron.console.toolkit.common.anno.ExcelHeader;
import lombok.Data;

import java.util.Date;

@Data
@TableName("node_info")
public class NodeInfo {
    @TableId(type = IdType.AUTO)
    String id;
    String appId;
    String botId;
    String flowId;
    String sub;
    String caller;
    String sid;
    String nodeId;
    @ExcelHeader(value = "Node Name", order = 0)
    String nodeName;
    String nodeType;
    @ExcelHeader(value = "Status", order = 6)
    Boolean runningStatus;
    @ExcelHeader(value = "Input", order = 1)
    String nodeInput;
    @ExcelHeader(value = "Output", order = 2)
    String nodeOutput;
    @ExcelHeader(value = "Expected Output", order = 3)
    @TableField(exist = false)
    String expectOutput;
    String config;
    String llmOutput;
    String domain;
    @ExcelHeader(value = "Performance Duration", order = 4)
    String costTime;
    @ExcelHeader(value = "First Frame Duration", order = 5)
    String firstCostTime;
    String nextLogIds;
    Integer token;
    Date createTime;
}
