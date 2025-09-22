package com.iflytek.astra.console.commons.dto.llm;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

@Data
@Schema(description = "Spark LLM chat request")
public class SparkChatRequest {

    @Schema(description = "Chat message list")
    @Size(min = 1, message = "Message list cannot be empty")
    private List<MessageDto> messages;

    @Schema(description = "Chat ID", example = "chat_123456")
    private String chatId;

    @Schema(description = "User ID", example = "user_123")
    private String userId;

    @Schema(description = "Model name, supports spark-x1, spark-lite, spark-pro, spark-max, spark-4.0-ultra", example = "spark-x1")
    private String model = "spark-x1";

    @Schema(description = "Whether to enable web search")
    private Boolean enableWebSearch = false;

    @Schema(description = "Search mode", example = "deep")
    private String searchMode = "deep";

    @Schema(description = "Whether to show reference labels")
    private Boolean showRefLabel = true;

    @Data
    @Schema(description = "Message content")
    public static class MessageDto {
        @Schema(description = "Role", example = "user")
        @NotBlank(message = "Role cannot be empty")
        private String role;

        @Schema(description = "Message content")
        @NotBlank(message = "Message content cannot be empty")
        private String content;
    }
}
