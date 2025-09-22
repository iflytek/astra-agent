package com.iflytek.astra.console.toolkit.entity.table.tool;

import lombok.Data;

import java.util.Date;

@Data
public class ToolBoxOperateHistory {

    private Long id;

    private String toolId;

    private String uid;

    private Integer type;

    private Date createTime;
}
