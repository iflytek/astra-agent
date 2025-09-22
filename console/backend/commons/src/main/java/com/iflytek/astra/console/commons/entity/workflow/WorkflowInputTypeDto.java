package com.iflytek.astra.console.commons.entity.workflow;

import com.alibaba.fastjson2.JSONObject;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Workflow input type response DTO
 *
 * @author yingpeng
 */
@Data
@NoArgsConstructor
public class WorkflowInputTypeDto {

    /**
     * Random string ID required by frontend
     */
    private String id;

    /**
     * Variable name
     */
    private String name;

    /**
     * Variable name error message
     */
    private String nameErrMsg;

    /**
     * Variable constraints schema
     */
    private JSONObject schema;

    /**
     * Allowed file types
     */
    private List<String> allowedFileType;

    /**
     * File type
     */
    private String fileType;

    /**
     * Description
     */
    private String description;

    /**
     * Whether required
     */
    private Boolean required;

    /**
     * Reference ID
     */
    private Object refId;

    /**
     * Delete disabled flag
     */
    private Object deleteDisabled;

    /**
     * Disabled flag
     */
    private Object disabled;

    /**
     * Custom parameter type
     */
    private String customParameterType;
}