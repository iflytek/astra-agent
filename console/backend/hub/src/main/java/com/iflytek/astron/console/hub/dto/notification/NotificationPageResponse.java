package com.iflytek.astron.console.hub.dto.notification;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.List;

@Data
@Schema(name = "NotificationPageResponse", description = "Notification page response object")
public class NotificationPageResponse {

    @Schema(description = "Notification message list")
    private List<NotificationDto> notifications;

    @Schema(description = "Current page number")
    private int pageIndex;

    @Schema(description = "Page size")
    private int pageSize;

    @Schema(description = "Total record count")
    private long totalCount;

    @Schema(description = "Total page count")
    private int totalPages;

    @Schema(description = "Unread message count")
    private long unreadCount;

    public NotificationPageResponse(List<NotificationDto> notifications, int pageIndex, int pageSize, long totalCount, long unreadCount) {
        this.notifications = notifications;
        this.pageIndex = pageIndex;
        this.pageSize = pageSize;
        this.totalCount = totalCount;
        this.totalPages = pageSize > 0 ? (int) Math.ceil((double) totalCount / pageSize) : 0;
        this.unreadCount = unreadCount;
    }
}
