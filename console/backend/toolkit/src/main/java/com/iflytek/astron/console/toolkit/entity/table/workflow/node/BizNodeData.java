package com.iflytek.astron.console.toolkit.entity.table.workflow.node;

import com.alibaba.fastjson2.JSONObject;
import com.iflytek.astron.console.toolkit.entity.biz.workflow.node.BizInputOutput;
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
    String icon;
    String description;
    String parentId;
    Object originPosition;
    Boolean updatable;
}
