package com.iflytek.astron.console.hub.data;

import com.iflytek.astron.console.hub.dto.notification.NotificationDto;
import com.iflytek.astron.console.hub.dto.notification.NotificationQueryRequest;
import com.iflytek.astron.console.hub.entity.notification.Notification;
import com.iflytek.astron.console.hub.entity.notification.UserBroadcastRead;
import com.iflytek.astron.console.hub.entity.notification.UserNotification;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * Notification data service interface
 *
 * Responsibilities: 1. Provide pure data layer operations without business logic 2. Manage caching
 * strategies 3. Encapsulate complex query logic
 */
public interface NotificationDataService {

    // ==================== Basic CRUD Operations ====================

    /**
     * Query notification by ID
     */
    Optional<Notification> getNotificationById(Long id);

    /**
     * Create notification
     */
    Notification createNotification(Notification notification);

    /**
     * Batch create user notification associations
     */
    int batchCreateUserNotifications(List<UserNotification> userNotifications);

    /**
     * Create broadcast read record
     */
    int createBroadcastReadRecord(UserBroadcastRead readRecord);

    /**
     * Batch create broadcast read records
     */
    int batchCreateBroadcastReadRecords(List<UserBroadcastRead> readRecords);

    // ==================== Query Operations ====================

    /**
     * Query all user messages (including personal messages and broadcast messages)
     */
    List<NotificationDto> getUserNotifications(String receiverUid, NotificationQueryRequest queryRequest);

    /**
     * Query user unread messages (including personal messages and broadcast messages)
     */
    List<NotificationDto> getUserUnreadNotifications(String receiverUid, NotificationQueryRequest queryRequest);

    /**
     * Count user unread messages (including personal messages and broadcast messages)
     */
    long countUserUnreadNotifications(String receiverUid);

    /**
     * Count all user messages (including personal messages and broadcast messages)
     */
    long countUserAllNotifications(String receiverUid);

    // ==================== Broadcast Message Special Operations ====================

    /**
     * Filter out broadcast message ID list
     */
    List<Long> filterBroadcastNotificationIds(List<Long> notificationIds);

    /**
     * Query all broadcast messages with pagination
     */
    List<Notification> getAllBroadcastNotifications(int offset, int limit);

    /**
     * Batch check the list of broadcast message IDs that user has read
     */
    List<Long> getUserReadBroadcastIds(String receiverUid, List<Long> notificationIds);

    // ==================== Update Operations ====================

    /**
     * Mark user messages as read
     */
    int markUserNotificationsAsRead(String receiverUid, List<Long> notificationIds);

    /**
     * Mark all user unread messages as read
     */
    int markAllUserNotificationsAsRead(String receiverUid);

    // ==================== Delete and Cleanup Operations ====================

    /**
     * Clean up expired messages
     */
    int deleteExpiredNotifications(LocalDateTime expireTime);

    /**
     * Delete user notification
     */
    int deleteUserNotification(String receiverUid, Long notificationId);

    /**
     * Get the count of broadcast messages visible to user (broadcasts after user registration) This
     * method needs to be exposed for self-proxy calls to support caching
     */
    long getUserVisibleBroadcastCount(String receiverUid);
}
