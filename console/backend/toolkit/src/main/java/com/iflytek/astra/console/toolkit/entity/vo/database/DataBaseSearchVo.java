package com.iflytek.astra.console.toolkit.entity.vo.database;

import lombok.Data;

@Data
public class DataBaseSearchVo {

    private String search;

    private Long tbId;

    private Long pageSize;

    private Long pageNum;
}
