package com.iflytek.astra.console.commons.entity.workflow;

import com.iflytek.astra.console.commons.dto.llm.SparkChatRequest;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.List;
import java.util.Map;

/**
 * Workflow chat request DTO
 */
@Data
@Schema(description = "Workflow chat request")
public class WorkflowChatRequest {

    @NotBlank(message = "Workflow ID cannot be empty")
    @Schema(description = "Workflow ID", example = "workflow_123")
    private String flowId;

    @NotBlank(message = "User ID cannot be empty")
    @Schema(description = "User ID", example = "user_456")
    private String userId;

    @NotBlank(message = "Chat ID cannot be empty")
    @Schema(description = "Chat session ID", example = "chat_789")
    private String chatId;

    @NotNull(message = "Message history cannot be empty")
    @Schema(description = "Chat history messages")
    private List<SparkChatRequest.MessageDto> messages;

    @Schema(description = "Whether to enable streaming response", example = "true")
    private Boolean stream = true;

    @Schema(description = "Workflow custom parameters")
    private Map<String, Object> parameters;

    @Schema(description = "Extension data")
    private Map<String, Object> ext;

    @Schema(description = "File ID list for file upload scenarios")
    private List<String> fileIds;
}
