package com.iflytek.astron.console.toolkit.entity.core.workflow;

import lombok.Data;

@Data
public class FlowProtocol {
    String id;
    String name;
    String description;
    String version = "v3.0.0";
    FlowProtocolData data;
    // Integer status;
}
