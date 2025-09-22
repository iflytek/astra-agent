package com.iflytek.astra.console.hub.dto.notification;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Schema(name = "NotificationDto", description = "Notification message response object")
public class NotificationDto {

    @Schema(description = "Message ID")
    private Long id;

    @Schema(description = "Message type")
    private String type;

    @Schema(description = "Message title")
    private String title;

    @Schema(description = "Message body")
    private String body;

    @Schema(description = "Template code")
    private String templateCode;

    @Schema(description = "Message payload, JSON format")
    private String payload;

    @Schema(description = "Creator ID")
    private String creatorUid;

    @Schema(description = "Creation time")
    private LocalDateTime createdAt;

    @Schema(description = "Expiration time")
    private LocalDateTime expireAt;

    @Schema(description = "Metadata, JSON format")
    private String meta;

    @Schema(description = "Whether read (only available for user messages)")
    private Boolean isRead;

    @Schema(description = "Read time (only available for user messages)")
    private LocalDateTime readAt;

    @Schema(description = "Received time (only available for user messages)")
    private LocalDateTime receivedAt;
}
