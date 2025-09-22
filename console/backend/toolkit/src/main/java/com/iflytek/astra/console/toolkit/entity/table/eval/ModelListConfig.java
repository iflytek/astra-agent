package com.iflytek.astra.console.toolkit.entity.table.eval;


import lombok.Data;

@Data
public class ModelListConfig {
    Long id;
    String nodeType;
    String name;
    String description;
    Object tag;
    Boolean deleted;
    String baseModelId;
    Boolean recommended;
}
