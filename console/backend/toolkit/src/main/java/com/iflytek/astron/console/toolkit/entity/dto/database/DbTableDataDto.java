package com.iflytek.astron.console.toolkit.entity.dto.database;


import lombok.Data;

import java.io.Serializable;
import java.util.Map;

@Data
public class DbTableDataDto implements Serializable {
    private static final long serialVersionUID = 1L;

    private Map<String, Object> tableData;

    private Integer operateType;
}
