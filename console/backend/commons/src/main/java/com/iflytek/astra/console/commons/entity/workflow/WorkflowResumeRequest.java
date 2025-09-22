package com.iflytek.astra.console.commons.entity.workflow;

import com.alibaba.fastjson2.annotation.JSONField;
import lombok.Builder;
import lombok.Data;

/**
 * Workflow resume request parameters
 */
@Data
@Builder
public class WorkflowResumeRequest {

    /**
     * Session event ID
     */
    @JSONField(name = "event_id")
    private String eventId;

    /**
     * Event type
     */
    @JSONField(name = "event_type")
    @Builder.Default
    private String eventType = WorkflowEventData.WorkflowOperation.RESUME.getOperation();

    /**
     * Session content
     */
    @JSONField(name = "content")
    private String content;
}
