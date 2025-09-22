package com.iflytek.astra.console.toolkit.entity.biz.workflow;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

@Data
public class ChatResumeReq {
    String eventId;
    String eventType;
    String content;
    @JsonProperty("flow_id")
    String flowId;
    Boolean regen;
    Integer outputType;
    Boolean promptDebugger = false;
    String version;

}
