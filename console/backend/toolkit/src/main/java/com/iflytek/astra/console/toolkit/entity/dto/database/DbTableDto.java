package com.iflytek.astra.console.toolkit.entity.dto.database;


import lombok.Data;

import java.io.Serializable;
import java.util.List;

@Data
public class DbTableDto implements Serializable {
    private static final long serialVersionUID = 1L;

    private Long id;

    private Long dbId;

    private String name;

    /**
     * Table description
     */
    private String description;


    private List<DbTableFieldDto> fields;

}
