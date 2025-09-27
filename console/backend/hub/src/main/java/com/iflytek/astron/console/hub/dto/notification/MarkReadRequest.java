package com.iflytek.astron.console.hub.dto.notification;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import jakarta.validation.constraints.Size;
import java.util.List;

@Data
@Schema(name = "MarkReadRequest", description = "Mark message as read request object")
public class MarkReadRequest {

    @Size(max = 100, message = "{notification.ids.invalid}")
    @Schema(description = "List of message IDs to mark as read")
    private List<Long> notificationIds;

    @Schema(description = "Whether to mark all unread messages as read")
    private Boolean markAll = false;
}
