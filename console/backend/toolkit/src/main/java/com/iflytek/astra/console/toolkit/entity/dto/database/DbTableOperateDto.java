package com.iflytek.astra.console.toolkit.entity.dto.database;


import lombok.Data;

import java.io.Serializable;
import java.util.List;

@Data
public class DbTableOperateDto implements Serializable {

    private static final long serialVersionUID = 1L;

    private Long tbId;

    private Integer execDev;

    private List<DbTableDataDto> data;
}
