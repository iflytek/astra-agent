package com.iflytek.astron.console.toolkit.entity.core.workflow.node;

import com.alibaba.fastjson2.JSONObject;
import lombok.Data;

import java.util.List;

@Data
public class NodeData {
    JSONObject nodeMeta;
    List<InputOutput> inputs;
    List<InputOutput> outputs;
    JSONObject nodeParam;
    JSONObject retryConfig;
}
