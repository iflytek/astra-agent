package com.iflytek.astron.console.toolkit.entity.core.workflow.sse;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

@Data
public class EventData {
    @JsonProperty("event_id")
    String event_id;
    @JsonProperty("event_type")
    String event_type;
    @JsonProperty("value")
    Value value;
    @JsonProperty("need_reply")
    Boolean need_reply;
    Integer timeout;
}
