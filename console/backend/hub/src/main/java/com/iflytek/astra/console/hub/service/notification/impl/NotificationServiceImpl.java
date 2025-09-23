package com.iflytek.astra.console.hub.service.notification.impl;

import com.iflytek.astra.console.commons.constant.ResponseEnum;
import com.iflytek.astra.console.commons.exception.BusinessException;
import com.iflytek.astra.console.commons.util.RequestContextUtil;
import com.iflytek.astra.console.hub.annotation.DistributedLock;
import com.iflytek.astra.console.hub.data.NotificationDataService;
import com.iflytek.astra.console.hub.dto.notification.*;
import com.iflytek.astra.console.hub.entity.notification.Notification;
import com.iflytek.astra.console.hub.entity.notification.UserBroadcastRead;
import com.iflytek.astra.console.hub.entity.notification.UserNotification;
import com.iflytek.astra.console.hub.enums.NotificationType;
import com.iflytek.astra.console.hub.service.notification.NotificationService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.CollectionUtils;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class NotificationServiceImpl implements NotificationService {

    private final NotificationDataService notificationDataService;

    // Batch operation limit constants
    private static final int MAX_BATCH_SIZE = 1000;
    private static final int MAX_NOTIFICATION_IDS = 100;

    // ==================== Send Notification ====================

    @Override
    @Transactional
    public Long sendNotification(SendNotificationRequest request) {
        // Parameter validation
        if (request == null || request.getType() == null) {
            throw new BusinessException(ResponseEnum.PARAMETER_ERROR);
        }

        NotificationType notificationType = request.getType();

        // Route to different processing logic based on message type
        switch (notificationType) {
            case BROADCAST:
                return sendBroadcastNotificationInternal(request);
            case PERSONAL, SYSTEM, PROMOTION:
                return sendToUsersNotificationInternal(request, notificationType);
            default:
                throw new BusinessException(ResponseEnum.NOTIFICATION_TYPE_INVALID);
        }
    }

    // ==================== Query Notification ====================

    @Override
    public NotificationPageResponse getUserNotifications(String receiverUid, NotificationQueryRequest queryRequest) {
        return getUserNotificationsByUid(receiverUid, queryRequest);
    }

    @Override
    public long getUnreadNotificationCount(String receiverUid) {
        if (receiverUid == null) {
            throw new BusinessException(ResponseEnum.PARAMETER_ERROR);
        }
        return notificationDataService.countUserUnreadNotifications(receiverUid);
    }

    // ==================== Manage Notification ====================

    @Override
    @Transactional
    @DistributedLock(
            key = "notification:mark_read:#{#receiverUid}",
            waitTime = 3L,
            leaseTime = 10L,
            failStrategy = DistributedLock.FailStrategy.CONTINUE,
            description = "Lock for marking user messages as read")
    public boolean markNotificationsAsRead(String receiverUid, MarkReadRequest request) {
        if (receiverUid == null) {
            throw new BusinessException(ResponseEnum.PARAMETER_ERROR);
        }

        try {
            if (Boolean.TRUE.equals(request.getMarkAll())) {
                // Mark all unread messages as read
                markAllNotificationsAsRead(receiverUid);
            } else if (!CollectionUtils.isEmpty(request.getNotificationIds())) {
                // Mark specific messages as read
                markSpecificNotificationsAsRead(receiverUid, request.getNotificationIds());
            }

            log.info("Notifications marked as read successfully, receiverUid: {}, markAll: {}, notificationIds: {}",
                    receiverUid, request.getMarkAll(), request.getNotificationIds());

            return true;
        } catch (Exception e) {
            log.error("Failed to mark notifications as read, receiverUid: {}", receiverUid, e);
            throw new BusinessException(ResponseEnum.NOTIFICATION_MARK_READ_FAILED);
        }
    }

    @Override
    @Transactional
    @DistributedLock(
            key = "notification:delete:#{#receiverUid}",
            waitTime = 2L,
            leaseTime = 5L,
            failStrategy = DistributedLock.FailStrategy.CONTINUE,
            description = "Lock for deleting user messages")
    public boolean deleteNotification(String receiverUid, Long notificationId) {
        if (receiverUid == null || notificationId == null) {
            throw new BusinessException(ResponseEnum.PARAMETER_ERROR);
        }

        try {
            int deleted = notificationDataService.deleteUserNotification(receiverUid, notificationId);
            if (deleted > 0) {
                log.info("Notification deleted successfully, receiverUid: {}, notificationId: {}",
                        receiverUid, notificationId);
                return true;
            } else {
                throw new BusinessException(ResponseEnum.NOTIFICATION_NOT_EXISTS);
            }
        } catch (BusinessException e) {
            throw e; // Re-throw business exception
        } catch (Exception e) {
            log.error("Failed to delete notification, receiverUid: {}, notificationId: {}",
                    receiverUid, notificationId, e);
            throw new BusinessException(ResponseEnum.NOTIFICATION_DELETE_FAILED);
        }
    }

    // ==================== System Management ====================

    @Override
    @Transactional
    public int cleanExpiredNotifications() {
        try {
            LocalDateTime expireTime = LocalDateTime.now();
            int deleted = notificationDataService.deleteExpiredNotifications(expireTime);
            log.info("Expired notifications cleaned, count: {}", deleted);
            return deleted;
        } catch (Exception e) {
            log.error("Failed to clean expired notifications", e);
            throw new BusinessException(ResponseEnum.OPERATION_FAILED);
        }
    }

    // ==================== Internal Methods ====================

    /**
     * Broadcast notification internal processing method
     */
    private Long sendBroadcastNotificationInternal(@Valid SendNotificationRequest request) {
        // Create broadcast notification
        Notification notification = createNotificationEntity(request, NotificationType.BROADCAST);
        notification = notificationDataService.createNotification(notification);

        log.info("Broadcast notification sent successfully, notificationId: {}", notification.getId());
        return notification.getId();
    }

    /**
     * Internal processing method for sending notifications to specified user list
     */
    private Long sendToUsersNotificationInternal(@Valid SendNotificationRequest request, NotificationType type) {
        if (CollectionUtils.isEmpty(request.getReceiverUids())) {
            throw new BusinessException(ResponseEnum.NOTIFICATION_RECEIVER_EMPTY);
        }

        // Validate the recipient quantity limit for batch sending
        if (request.getReceiverUids().size() > MAX_BATCH_SIZE) {
            throw new BusinessException(ResponseEnum.PARAMETER_ERROR,
                    String.format("Number of receivers cannot exceed %d", MAX_BATCH_SIZE));
        }

        return sendNotificationToUsers(request, type, request.getReceiverUids());
    }

    /**
     * Unified method for sending notifications to users
     */
    private Long sendNotificationToUsers(SendNotificationRequest request, NotificationType type, List<String> receiverUids) {
        // Create notification
        Notification notification = createNotificationEntity(request, type);
        notification = notificationDataService.createNotification(notification);

        // Batch create user notification associations
        List<UserNotification> userNotifications = new ArrayList<>();
        LocalDateTime now = LocalDateTime.now();

        for (String receiverUid : receiverUids) {
            UserNotification userNotification = new UserNotification();
            userNotification.setNotificationId(notification.getId());
            userNotification.setReceiverUid(receiverUid);
            userNotification.setIsRead(false);
            userNotification.setReceivedAt(now);
            userNotifications.add(userNotification);
        }

        notificationDataService.batchCreateUserNotifications(userNotifications);

        log.info("{} notification sent successfully, notificationId: {}, receiverCount: {}",
                type.getDescription(), notification.getId(), receiverUids.size());

        return notification.getId();
    }

    private Notification createNotificationEntity(SendNotificationRequest request, NotificationType type) {
        Notification notification = new Notification();

        // Manually copy properties, excluding type field
        notification.setTitle(request.getTitle());
        notification.setBody(request.getBody());
        notification.setTemplateCode(request.getTemplateCode());
        notification.setPayload(request.getPayload());
        notification.setExpireAt(request.getExpireAt());
        notification.setMeta(request.getMeta());

        // Set type as String type code
        notification.setType(type.getCode());

        // Get current operating user
        try {
            String currentUid = RequestContextUtil.getUID();
            notification.setCreatorUid(currentUid);
        } catch (Exception e) {
            log.warn("Failed to get current user ID, using system as creator");
        }

        return notification;
    }

    private NotificationPageResponse getUserNotificationsByUid(String receiverUid, NotificationQueryRequest queryRequest) {
        if (receiverUid == null) {
            throw new BusinessException(ResponseEnum.PARAMETER_ERROR);
        }

        List<NotificationDto> notifications;
        long unreadCount = notificationDataService.countUserUnreadNotifications(receiverUid);
        long totalCount;

        if (Boolean.TRUE.equals(queryRequest.getUnreadOnly())) {
            // Query unread messages only
            notifications = notificationDataService.getUserUnreadNotifications(
                    receiverUid, queryRequest);
            totalCount = unreadCount; // Total unread messages is the unread count
        } else {
            // Query all messages
            notifications = notificationDataService.getUserNotifications(
                    receiverUid, queryRequest);
            totalCount = notificationDataService.countUserAllNotifications(receiverUid); // Use dedicated count method
        }

        return new NotificationPageResponse(notifications, queryRequest.getPageIndex(),
                queryRequest.getPageSize(), totalCount, unreadCount);
    }

    /**
     * Mark all unread messages as read. Note: This method is called within @Transactional method to
     * ensure transaction consistency
     */
    private void markAllNotificationsAsRead(String receiverUid) {
        try {
            // Mark all personal unread messages as read
            notificationDataService.markAllUserNotificationsAsRead(receiverUid);

            // Mark all broadcast messages as read
            markAllBroadcastMessagesAsRead(receiverUid);

            log.debug("All notifications marked as read for user: {}", receiverUid);
        } catch (Exception e) {
            log.error("Failed to mark all notifications as read for user: {}", receiverUid, e);
            throw e; // Re-throw exception to trigger transaction rollback
        }
    }

    /**
     * Mark specific messages as read
     */
    private void markSpecificNotificationsAsRead(String receiverUid, List<Long> notificationIds) {
        // Validate notification ID quantity limit
        if (notificationIds.size() > MAX_NOTIFICATION_IDS) {
            throw new BusinessException(ResponseEnum.PARAMETER_ERROR,
                    String.format("Number of notifications marked at once cannot exceed %d", MAX_NOTIFICATION_IDS));
        }

        // Mark personal messages as read
        notificationDataService.markUserNotificationsAsRead(receiverUid, notificationIds);

        // Handle broadcast messages - filter out actual broadcast messages first
        List<Long> broadcastIds = notificationDataService.filterBroadcastNotificationIds(notificationIds);
        if (!broadcastIds.isEmpty()) {
            markBroadcastMessagesAsRead(receiverUid, broadcastIds);
        }
    }

    /**
     * Mark all broadcast messages as read - optimize performance, process in batches
     */
    private void markAllBroadcastMessagesAsRead(String receiverUid) {
        int batchSize = 100;
        int offset = 0;
        List<Notification> broadcastNotifications;

        do {
            broadcastNotifications = notificationDataService.getAllBroadcastNotifications(offset, batchSize);

            if (!broadcastNotifications.isEmpty()) {
                List<Long> broadcastIds = broadcastNotifications.stream()
                        .map(Notification::getId)
                        .toList();

                markBroadcastMessagesAsRead(receiverUid, broadcastIds);
                offset += batchSize;
            }
        } while (broadcastNotifications.size() == batchSize);
    }

    /**
     * Mark specified broadcast messages as read
     */
    @DistributedLock(
            key = "notification:broadcast_read:#{#receiverUid}:#{T(Math).abs(#broadcastIds.hashCode())}",
            waitTime = 2L,
            leaseTime = 8L,
            failStrategy = DistributedLock.FailStrategy.CONTINUE,
            description = "Lock for marking broadcast messages as read")
    private void markBroadcastMessagesAsRead(String receiverUid, List<Long> broadcastIds) {
        // Filter out broadcast messages that the user has not read
        List<Long> readBroadcastIds = notificationDataService.getUserReadBroadcastIds(receiverUid, broadcastIds);
        List<Long> unreadBroadcastIds = broadcastIds.stream()
                .filter(id -> !readBroadcastIds.contains(id))
                .toList();

        if (!unreadBroadcastIds.isEmpty()) {
            List<UserBroadcastRead> readRecords = unreadBroadcastIds.stream()
                    .map(notificationId -> {
                        UserBroadcastRead readRecord = new UserBroadcastRead();
                        readRecord.setReceiverUid(receiverUid);
                        readRecord.setNotificationId(notificationId);
                        return readRecord;
                    })
                    .toList();

            notificationDataService.batchCreateBroadcastReadRecords(readRecords);
        }
    }
}
