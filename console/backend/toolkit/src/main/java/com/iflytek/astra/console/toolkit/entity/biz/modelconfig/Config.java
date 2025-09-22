package com.iflytek.astra.console.toolkit.entity.biz.modelconfig;

import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.annotation.JSONField;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

/**
 * @Author clliu19
 * @Date: 2025/7/10 09:23
 */
@Data
public class Config {
    /**
     * id
     */
    private String id;
    /**
     * Whether it is a standard field
     */
    private Boolean standard = true;

    /**
     * Constraint type, e.g., range, enum, switch, etc.
     */
    private String constraintType;

    /**
     * Default value of the field
     */
    @JSONField(name = "default")
    @JsonProperty("default")
    private Object dft;

    /**
     * Constraint content, range, enum value list, etc.
     */
    private JSONArray constraintContent;

    /**
     * Field name
     */
    private String name;

    /**
     * Field type, e.g., string, int, boolean, float, etc.
     */
    private String fieldType;

    /**
     * Initial value, typically used for field initialization
     */
    private Object initialValue;

    /**
     * Unique key corresponding to the field
     */
    private String key;

    /**
     * Whether it is a required field
     */
    private Boolean required;
    /**
     * Precision decimal places
     */
    private Float precision;
}
