package com.iflytek.astra.console.toolkit.entity.core.workflow.sse;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

@Data
public class Choice {
    Delta delta;
    Integer index;

    @JsonProperty("finish_reason")
    Object finishReason;
}
