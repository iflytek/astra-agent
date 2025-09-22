package com.iflytek.astra.console.toolkit.entity.core.workflow.ws;

import com.alibaba.fastjson2.JSONObject;
import com.alibaba.fastjson2.annotation.JSONField;
import lombok.Data;

@Data
public class SparkFlowResponsePayloadContent {
    String status;
    Object inputs;
    Object outputs;
    @JSONField(name = "process_data")
    JSONObject processData;
    @JSONField(name = "edge_source_handle")
    String edgeSourceHandle;
    Object error;
    @JSONField(name = "raw_output")
    String rawOutput;
    @JSONField(name = "node_id")
    String nodeId;
    @JSONField(name = "alias_name")
    String aliasName;
    @JSONField(name = "node_type")
    String nodeType;

}
