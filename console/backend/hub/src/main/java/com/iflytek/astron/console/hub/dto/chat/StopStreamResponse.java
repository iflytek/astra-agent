package com.iflytek.astron.console.hub.dto.chat;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * Stop SSE stream response DTO
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Stop SSE stream response")
public class StopStreamResponse {

    @Schema(description = "Whether the operation was successful", example = "true")
    private Boolean success;

    @Schema(description = "Response message", example = "Stream stopped")
    private String message;

    @Schema(description = "Stream ID", example = "chat_123_user_456_1234567890")
    private String streamId;

    @Schema(description = "Operation time")
    private LocalDateTime operationTime;

    @Schema(description = "Response timestamp")
    private Long timestamp;

    /**
     * Create success response
     */
    public static StopStreamResponse success(String streamId) {
        return StopStreamResponse.builder()
                .success(true)
                .message("Stream stopped")
                .streamId(streamId)
                .operationTime(LocalDateTime.now())
                .timestamp(System.currentTimeMillis())
                .build();
    }

    /**
     * Create failure response
     */
    public static StopStreamResponse failure(String streamId, String errorMessage) {
        return StopStreamResponse.builder()
                .success(false)
                .message(errorMessage)
                .streamId(streamId)
                .operationTime(LocalDateTime.now())
                .timestamp(System.currentTimeMillis())
                .build();
    }
}
