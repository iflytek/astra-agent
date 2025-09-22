package com.iflytek.astra.console.commons.entity.workflow;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import jakarta.validation.constraints.NotBlank;

/**
 * Workflow resume request DTO
 */
@Data
@Schema(description = "Workflow resume request")
public class WorkflowResumeReq {

    @NotBlank(message = "Event ID cannot be empty")
    @Schema(description = "Event ID, used to resume interrupted workflows", example = "event_123")
    private String eventId;

    @NotBlank(message = "Event type cannot be empty")
    @Schema(description = "Event type", example = "interrupt")
    private String eventType;

    @NotBlank(message = "Operation type cannot be empty")
    @Schema(description = "Operation type: resume, ignore, abort", example = "resume")
    private String operation;

    @Schema(description = "Resume content, required when operation is resume")
    private String content;

    @Schema(description = "User ID", example = "user_456")
    private String userId;

    @Schema(description = "Chat ID", example = "chat_789")
    private String chatId;
}
