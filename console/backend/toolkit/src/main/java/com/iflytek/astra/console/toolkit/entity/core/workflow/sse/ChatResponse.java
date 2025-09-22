package com.iflytek.astra.console.toolkit.entity.core.workflow.sse;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
public class ChatResponse {
    Integer code;
    String message;

    /**
     * aka sid
     */
    String id;
    Integer created;

    @JsonProperty("workflow_step")
    WorkflowStep workflowStep;

    List<Choice> choices;
    Double executedTime;
    Usage usage;
    @JsonProperty("event_data")
    EventData eventData;

    String orderedMsg;

    public ChatResponse(String content) {
        code = -1;
        message = content;
    }
}
