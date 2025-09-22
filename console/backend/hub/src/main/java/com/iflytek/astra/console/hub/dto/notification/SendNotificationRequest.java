package com.iflytek.astra.console.hub.dto.notification;

import com.iflytek.astra.console.hub.enums.NotificationType;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDateTime;
import java.util.List;

@Data
@Schema(name = "SendNotificationRequest", description = "Send notification request object")
public class SendNotificationRequest {

    @NotNull(message = "{notification.type.invalid}")
    @Schema(description = "Message type", requiredMode = Schema.RequiredMode.REQUIRED, example = "PERSONAL")
    private NotificationType type;

    @NotBlank(message = "{notification.title.not.empty}")
    @Schema(description = "Message title", requiredMode = Schema.RequiredMode.REQUIRED)
    private String title;

    @Schema(description = "Message body")
    private String body;

    @Schema(description = "Template code")
    private String templateCode;

    @Schema(description = "Message payload, JSON format")
    private String payload;

    @Schema(description = "Expiration time")
    private LocalDateTime expireAt;

    @Schema(description = "Metadata, JSON format")
    private String meta;

    @Schema(description = "List of receiver user IDs (required for personal messages)")
    private List<String> receiverUids;
}
