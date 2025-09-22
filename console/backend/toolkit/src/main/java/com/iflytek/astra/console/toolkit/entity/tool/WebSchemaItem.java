package com.iflytek.astra.console.toolkit.entity.tool;


import com.alibaba.fastjson2.annotation.JSONField;
import lombok.Data;

import java.util.List;

@Data
public class WebSchemaItem {

    String id;
    String key;
    String fatherType;

    /**
     * Parameter name
     */
    String name;

    /**
     * Parameter type, int, string, etc.
     */
    String type;

    /**
     * Description
     */
    String description;


    /**
     * Value source
     */
    Integer from;

    /**
     * Whether required
     */
    Boolean required;

    /**
     * Default value
     */
    @JSONField(name = "default")
    Object dft;

    /**
     * Parameter position, header, path, url, body, etc.
     */
    String location;

    /**
     * Child nodes
     */
    List<WebSchemaItem> children;

    Boolean open;


    /**
     * Old name
     */
    @Deprecated
    String title;

    /**
     * Parameter name explanation
     */
    @Deprecated
    String paramName;
}
