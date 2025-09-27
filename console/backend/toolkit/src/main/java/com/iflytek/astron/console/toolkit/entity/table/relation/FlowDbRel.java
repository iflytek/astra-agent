package com.iflytek.astron.console.toolkit.entity.table.relation;

import lombok.Data;

import java.util.Date;

@Data
public class FlowDbRel {

    private Long id;

    private String dbId;

    private String flowId;

    private Long tbId;

    private Date createTime;

    private Date updateTime;
}
