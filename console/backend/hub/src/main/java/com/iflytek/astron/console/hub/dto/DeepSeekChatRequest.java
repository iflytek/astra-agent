package com.iflytek.astron.console.hub.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

@Data
@Schema(description = "DeepSeek large model chat request")
public class DeepSeekChatRequest {

    @Schema(description = "Chat message list")
    @Size(min = 1, message = "Message list cannot be empty")
    private List<MessageDto> messages;

    @Schema(description = "Chat ID", example = "chat_123456")
    private String chatId;

    @Schema(description = "User ID", example = "user_123")
    private String userId;

    @Schema(description = "Model name", example = "deepseek-chat")
    private String model = "x1";

    @Schema(description = "Controls randomness, between 0.0-2.0", example = "0.7")
    private Double temperature = 0.7;

    @Schema(description = "Nucleus sampling, between 0.0-1.0", example = "0.95")
    private Double topP = 0.95;

    @Schema(description = "Maximum number of tokens to generate", example = "4096")
    private Integer maxTokens = 4096;

    @Schema(description = "Whether to use streaming")
    private Boolean stream = true;

    @Schema(description = "Stop words list")
    private List<String> stop;

    @Schema(description = "Frequency penalty, between -2.0-2.0", example = "0.0")
    private Double frequencyPenalty = 0.0;

    @Schema(description = "Presence penalty, between -2.0-2.0", example = "0.0")
    private Double presencePenalty = 0.0;

    @Data
    @Schema(description = "Message content")
    public static class MessageDto {
        @Schema(description = "Role: system, user, assistant", example = "user")
        @NotBlank(message = "Role cannot be empty")
        private String role;

        @Schema(description = "Message content")
        @NotBlank(message = "Message content cannot be empty")
        private String content;
    }
}
