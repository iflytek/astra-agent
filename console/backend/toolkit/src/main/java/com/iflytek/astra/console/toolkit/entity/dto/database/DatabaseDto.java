package com.iflytek.astra.console.toolkit.entity.dto.database;


import lombok.Data;

import java.io.Serializable;

@Data
public class DatabaseDto implements Serializable {
    private static final long serialVersionUID = 1L;

    private Long id;

    private String name;

    /**
     * Database description
     */
    private String description;

}
