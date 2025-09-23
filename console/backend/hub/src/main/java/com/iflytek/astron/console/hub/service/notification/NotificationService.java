package com.iflytek.astron.console.hub.service.notification;

import com.iflytek.astron.console.hub.dto.notification.MarkReadRequest;
import com.iflytek.astron.console.hub.dto.notification.NotificationPageResponse;
import com.iflytek.astron.console.hub.dto.notification.NotificationQueryRequest;
import com.iflytek.astron.console.hub.dto.notification.SendNotificationRequest;

/**
 * Notification center business service interface
 *
 * Responsibilities: 1. Handle notification sending business logic 2. Handle user notification query
 * and management 3. System-level notification management
 */
public interface NotificationService {

    // ==================== Send Notification ====================

    /**
     * Send notification (unified entry)
     *
     * @param request Send notification request
     * @return Notification ID
     */
    Long sendNotification(SendNotificationRequest request);

    // ==================== Query Notification ====================

    /**
     * Query specified user's message list (paginated)
     *
     * @param receiverUid Receiver user ID
     * @param queryRequest Query request parameters
     * @return Paginated response
     */
    NotificationPageResponse getUserNotifications(String receiverUid, NotificationQueryRequest queryRequest);

    /**
     * Query specified user's unread message count
     *
     * @param receiverUid Receiver user ID
     * @return Unread message count
     */
    long getUnreadNotificationCount(String receiverUid);

    // ==================== Manage Notification ====================

    /**
     * Mark specified user's messages as read
     *
     * @param receiverUid Receiver user ID
     * @param request Mark as read request
     * @return Whether operation succeeded
     */
    boolean markNotificationsAsRead(String receiverUid, MarkReadRequest request);

    /**
     * Delete specified user's notification
     *
     * @param receiverUid Receiver user ID
     * @param notificationId Notification ID
     * @return Whether operation succeeded
     */
    boolean deleteNotification(String receiverUid, Long notificationId);

    // ==================== System Management ====================

    /**
     * Clean expired messages (used by admin or scheduled tasks)
     *
     * @return Number of cleaned messages
     */
    int cleanExpiredNotifications();
}
