package com.iflytek.astra.console.toolkit.entity.biz.workflow.node;

import com.alibaba.fastjson2.JSONObject;
import lombok.Data;

import java.util.List;

@Data
public class BizNodeData {
    @Deprecated
    String id;
    Boolean allowInputReference;
    Boolean allowOutputReference;
    String label;
    Boolean labelEdit;
    Object references;
    String status;
    JSONObject nodeMeta;
    List<BizInputOutput> inputs;
    List<BizInputOutput> outputs;
    JSONObject nodeParam;
    /**
     * Retry strategy
     */
    JSONObject retryConfig;
    /**
     * Node error output, effective when not interrupted. errorCode - error code, errorMessage - error
     * message
     */
    JSONObject errorOutputs;
    String icon;
    String description;
    String parentId;
    Object originPosition;
    Boolean updatable;
    Boolean isLatest;
    String pluginName;
}
