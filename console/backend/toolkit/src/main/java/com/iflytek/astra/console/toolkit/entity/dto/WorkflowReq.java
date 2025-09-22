package com.iflytek.astra.console.toolkit.entity.dto;

import com.alibaba.fastjson2.JSONObject;
import com.iflytek.astra.console.toolkit.entity.biz.workflow.BizWorkflowData;
import lombok.Data;

import java.util.Map;

@Data
public class WorkflowReq {
    Long id;
    String name;
    String description;
    BizWorkflowData data;
    Integer status;

    String appId;
    String avatarIcon;
    String avatarColor;

    String domain;
    Boolean commonUser;

    Integer source;
    String sourceCode;
    /**
     * Advanced configuration
     */
    Map<String, Object> advancedConfig;
    JSONObject ext;
    Integer category;
    String flowId;
    Long spaceId;
}
