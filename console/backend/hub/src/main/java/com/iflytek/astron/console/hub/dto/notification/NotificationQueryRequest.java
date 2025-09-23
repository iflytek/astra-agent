package com.iflytek.astron.console.hub.dto.notification;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Max;

@Data
@Schema(name = "NotificationQueryRequest", description = "Notification query request object")
public class NotificationQueryRequest {

    @Schema(description = "Message type filter (personal, broadcast, system, promotion)")
    private String type;

    @Schema(description = "Query only unread messages")
    private Boolean unreadOnly;

    @Min(value = 1, message = "{notification.query.page.invalid}")
    @Schema(description = "Page number, starting from 1", example = "1")
    private int pageIndex = 1;

    @Min(value = 1, message = "{notification.query.page.invalid}")
    @Max(value = 100, message = "{notification.query.page.invalid}")
    @Schema(description = "Page size", example = "20")
    private int pageSize = 20;

    public int getOffset() {
        return Math.max(0, (pageIndex - 1) * pageSize);
    }
}
