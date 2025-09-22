package com.iflytek.astra.console.toolkit.entity.vo;

import lombok.Data;

import java.util.List;

@Data
public class WorkflowErrorModelVo {

    private String nodeName;

    private Long callNum;

    private Long errorNum;

    private List<WorkflowErrorVo> info;
}
