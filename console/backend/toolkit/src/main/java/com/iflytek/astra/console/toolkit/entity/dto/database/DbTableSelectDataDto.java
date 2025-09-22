package com.iflytek.astra.console.toolkit.entity.dto.database;


import lombok.Data;

import java.io.Serializable;

@Data
public class DbTableSelectDataDto implements Serializable {

    private static final long serialVersionUID = 1L;

    private Long tbId;

    private Integer execDev;

    private Long pageNum;

    private Long pageSize;
}
