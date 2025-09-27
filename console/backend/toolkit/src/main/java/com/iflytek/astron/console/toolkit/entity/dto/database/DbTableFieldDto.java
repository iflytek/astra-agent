package com.iflytek.astron.console.toolkit.entity.dto.database;


import lombok.Data;

import java.io.Serializable;

@Data
public class DbTableFieldDto implements Serializable {
    private static final long serialVersionUID = 1L;

    private Long id;

    private String name;

    private String type;


    /**
     * Field description
     */
    private String description;

    private String defaultValue;

    private Boolean isRequired = false;

    private Integer operateType;

    private Boolean isSystem;

    private String nameErrMsg;

    private String descriptionErrMsg;
}
