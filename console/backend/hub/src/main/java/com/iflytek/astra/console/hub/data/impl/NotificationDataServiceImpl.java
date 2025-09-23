package com.iflytek.astra.console.hub.data.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.iflytek.astra.console.commons.data.UserInfoDataService;
import com.iflytek.astra.console.hub.data.NotificationDataService;
import com.iflytek.astra.console.hub.dto.notification.NotificationDto;
import com.iflytek.astra.console.hub.dto.notification.NotificationQueryRequest;
import com.iflytek.astra.console.hub.entity.notification.Notification;
import com.iflytek.astra.console.hub.entity.notification.UserBroadcastRead;
import com.iflytek.astra.console.hub.entity.notification.UserNotification;
import com.iflytek.astra.console.hub.enums.NotificationType;
import com.iflytek.astra.console.hub.mapper.notification.NotificationMapper;
import com.iflytek.astra.console.hub.mapper.notification.UserBroadcastReadMapper;
import com.iflytek.astra.console.hub.mapper.notification.UserNotificationMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.cache.CacheManager;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Slf4j
@Service
public class NotificationDataServiceImpl implements NotificationDataService {
    // Cache key constants
    private static final String USER_UNREAD_COUNT_CACHE = "user_unread_count";
    private static final String USER_TOTAL_COUNT_CACHE = "user_total_count";
    private static final String BROADCAST_COUNT_INTERNAL_CACHE = "broadcast_count_internal";
    private static final String USER_VISIBLE_BROADCAST_COUNT_CACHE = "user_visible_broadcast_count";

    private final NotificationMapper notificationMapper;
    private final UserNotificationMapper userNotificationMapper;
    private final UserBroadcastReadMapper userBroadcastReadMapper;
    private final CacheManager cacheManager;
    private final UserInfoDataService userInfoDataService;

    // Inject self proxy to solve @Cacheable internal call failure problem
    @Lazy
    private final NotificationDataService self;

    public NotificationDataServiceImpl(
            NotificationMapper notificationMapper,
            UserNotificationMapper userNotificationMapper,
            UserBroadcastReadMapper userBroadcastReadMapper,
            @Qualifier("cacheManager5min") CacheManager cacheManager,
            UserInfoDataService userInfoDataService,
            @Lazy NotificationDataService self) {
        this.notificationMapper = notificationMapper;
        this.userNotificationMapper = userNotificationMapper;
        this.userBroadcastReadMapper = userBroadcastReadMapper;
        this.cacheManager = cacheManager;
        this.userInfoDataService = userInfoDataService;
        this.self = self;
    }

    @Override
    public Optional<Notification> getNotificationById(Long id) {
        return Optional.ofNullable(notificationMapper.selectById(id));
    }

    @Override
    public Notification createNotification(Notification notification) {
        notification.setCreatedAt(LocalDateTime.now());
        notificationMapper.insert(notification);

        // Determine cache eviction strategy based on message type
        if (NotificationType.BROADCAST.getCode().equals(notification.getType())) {
            // Broadcast message: evict internal broadcast count cache
            evictBroadcastCountInternalCache();
            log.debug("Created broadcast notification: {}", notification.getId());
        }
        // Personal messages do not evict cache here, evict when sent to specific users

        return notification;
    }

    @Override
    public int batchCreateUserNotifications(List<UserNotification> userNotifications) {
        if (userNotifications.isEmpty()) {
            return 0;
        }

        try {
            int result = userNotificationMapper.batchInsert(userNotifications);
            if (result != userNotifications.size()) {
                log.error("Batch insert incomplete: expected {}, actual {}", userNotifications.size(), result);
                throw new IllegalStateException("Batch insert of user notifications incomplete");
            }

            // Precisely evict cache for affected users
            userNotifications.stream()
                    .map(UserNotification::getReceiverUid)
                    .distinct()
                    .forEach(this::evictUserCountCaches);

            log.debug("Batch created {} user notifications successfully", userNotifications.size());
            return result;
        } catch (Exception e) {
            log.error("Failed to batch create user notifications, count: {}", userNotifications.size(), e);
            throw e;
        }
    }

    @Override
    public int createBroadcastReadRecord(UserBroadcastRead readRecord) {
        readRecord.setReadAt(LocalDateTime.now());
        return userBroadcastReadMapper.insert(readRecord);
    }

    @Override
    public int batchCreateBroadcastReadRecords(List<UserBroadcastRead> readRecords) {
        if (readRecords.isEmpty()) {
            return 0;
        }

        readRecords.forEach(r -> r.setReadAt(LocalDateTime.now()));
        int batchInsertCount = userBroadcastReadMapper.batchInsert(readRecords);

        // Precisely evict unread count cache for affected users
        readRecords.stream()
                .map(UserBroadcastRead::getReceiverUid)
                .distinct()
                .forEach(this::evictUserUnreadCountCache);

        return batchInsertCount;
    }

    @Override
    public List<NotificationDto> getUserNotifications(String receiverUid, NotificationQueryRequest queryRequest) {
        // Use JOIN query for personal messages (solve N+1 problem)
        List<NotificationDto> personalNotifications = userNotificationMapper
                .selectUserNotificationsWithDetails(receiverUid, queryRequest.getOffset(), queryRequest.getPageSize());

        return mergeWithBroadcastNotifications(personalNotifications, receiverUid, queryRequest.getPageSize(), false);
    }

    @Override
    public List<NotificationDto> getUserUnreadNotifications(String receiverUid, NotificationQueryRequest queryRequest) {
        // Use JOIN query for unread personal messages (solve N+1 problem)
        List<NotificationDto> unreadPersonalNotifications = userNotificationMapper
                .selectUserUnreadNotificationsWithDetails(
                        receiverUid,
                        queryRequest.getOffset(),
                        queryRequest.getPageSize());

        return mergeWithBroadcastNotifications(
                unreadPersonalNotifications,
                receiverUid,
                queryRequest.getPageSize(),
                true);
    }

    @Override
    @Cacheable(value = USER_UNREAD_COUNT_CACHE, key = "#receiverUid", cacheManager = "cacheManager5min")
    public long countUserUnreadNotifications(String receiverUid) {
        try {
            // Count personal unread messages
            int unreadPersonalCount = userNotificationMapper.countUnreadByUid(receiverUid);

            // Get total visible broadcast messages for user (broadcast messages after registration)
            // Call through self proxy to enable caching
            long userVisibleBroadcastCount = self.getUserVisibleBroadcastCount(receiverUid);
            if (userVisibleBroadcastCount == 0) {
                return unreadPersonalCount;
            }

            // Count user's read broadcast messages
            long readBroadcastCount = userBroadcastReadMapper.countUserReadBroadcastMessages(receiverUid);
            long unreadBroadcastCount = Math.max(0, userVisibleBroadcastCount - readBroadcastCount);

            return unreadPersonalCount + unreadBroadcastCount;
        } catch (Exception e) {
            log.error("Failed to count unread notifications for user: {}", receiverUid, e);
            throw e;
        }
    }

    @Override
    @Cacheable(value = USER_TOTAL_COUNT_CACHE, key = "#receiverUid", cacheManager = "cacheManager5min")
    public long countUserAllNotifications(String receiverUid) {
        // Count total personal messages
        long personalCount = userNotificationMapper.selectCount(
                new QueryWrapper<UserNotification>().eq("receiver_uid", receiverUid));

        // Get total visible broadcast messages for user (broadcast messages after registration)
        // Call through self proxy to enable caching
        long userVisibleBroadcastCount = self.getUserVisibleBroadcastCount(receiverUid);

        long totalCount = personalCount + userVisibleBroadcastCount;
        log.debug("Counted all notifications for user {}: personal={}, visible_broadcast={}, total={}",
                receiverUid, personalCount, userVisibleBroadcastCount, totalCount);

        return totalCount;
    }

    @Override
    public List<Long> filterBroadcastNotificationIds(List<Long> notificationIds) {
        if (notificationIds.isEmpty()) {
            return new ArrayList<>();
        }

        List<Notification> notifications = notificationMapper.selectBatchIds(notificationIds);
        return notifications.stream()
                .filter(notification -> NotificationType.BROADCAST.getCode().equals(notification.getType()))
                .map(Notification::getId)
                .toList();
    }

    @Override
    public List<Notification> getAllBroadcastNotifications(int offset, int limit) {
        return notificationMapper.selectByType(NotificationType.BROADCAST.getCode(), offset, limit);
    }

    @Override
    public List<Long> getUserReadBroadcastIds(String receiverUid, List<Long> notificationIds) {
        if (notificationIds.isEmpty()) {
            return new ArrayList<>();
        }
        return userBroadcastReadMapper.selectReadBroadcastIds(receiverUid, notificationIds);
    }

    @Override
    public int markUserNotificationsAsRead(String receiverUid, List<Long> notificationIds) {
        int result = userNotificationMapper.batchMarkAsRead(receiverUid, notificationIds);
        if (result > 0) {
            evictUserUnreadCountCache(receiverUid);
        }
        return result;
    }

    @Override
    public int markAllUserNotificationsAsRead(String receiverUid) {
        int result = userNotificationMapper.markAllAsRead(receiverUid);
        if (result > 0) {
            evictUserUnreadCountCache(receiverUid);
        }
        return result;
    }

    @Override
    public int deleteExpiredNotifications(LocalDateTime expireTime) {
        int result = notificationMapper.deleteExpiredMessages(expireTime);
        if (result > 0) {
            // Expiry deletion affects all caches, but frequency is low
            evictAllCaches();
        }
        return result;
    }

    @Override
    public int deleteUserNotification(String receiverUid, Long notificationId) {
        QueryWrapper<UserNotification> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq("receiver_uid", receiverUid)
                .eq("notification_id", notificationId);
        int result = userNotificationMapper.delete(queryWrapper);
        if (result > 0) {
            evictUserCountCaches(receiverUid);
        }
        return result;
    }

    // ==================== Private Helper Methods ====================

    private NotificationDto convertToDto(Notification notification) {
        NotificationDto dto = new NotificationDto();
        BeanUtils.copyProperties(notification, dto);
        return dto;
    }

    /**
     * Generic method for merging personal messages and broadcast messages
     *
     * @param personalNotifications List of personal messages
     * @param receiverUid Receiver ID
     * @param limit Total limit count
     * @param unreadOnly Whether to query only unread messages
     * @return Merged message list
     */
    private List<NotificationDto> mergeWithBroadcastNotifications(
            List<NotificationDto> personalNotifications, String receiverUid, int limit, boolean unreadOnly) {

        List<NotificationDto> result = new ArrayList<>(personalNotifications);

        // If personal messages already satisfy the quantity requirement, return directly
        if (result.size() >= limit) {
            return result.subList(0, limit);
        }

        // Query and merge broadcast messages
        List<NotificationDto> broadcastDtos = getBroadcastNotificationDtos(receiverUid, limit - result.size(), unreadOnly);
        result.addAll(broadcastDtos);

        // Sort by time and return limited number of results
        result.sort((a, b) -> b.getReceivedAt().compareTo(a.getReceivedAt()));
        return result.size() > limit ? result.subList(0, limit) : result;
    }

    /**
     * Get broadcast message DTO list
     */
    private List<NotificationDto> getBroadcastNotificationDtos(String receiverUid, int remainingLimit, boolean unreadOnly) {
        List<Notification> broadcastNotifications = notificationMapper.selectByType(
                NotificationType.BROADCAST.getCode(), 0, remainingLimit * 2);

        if (broadcastNotifications.isEmpty()) {
            return new ArrayList<>();
        }

        List<Long> broadcastIds = broadcastNotifications.stream()
                .map(Notification::getId)
                .toList();
        List<Long> readBroadcastIds = userBroadcastReadMapper.selectReadBroadcastIds(receiverUid, broadcastIds);

        List<NotificationDto> result = new ArrayList<>();
        for (Notification broadcast : broadcastNotifications) {
            boolean isRead = readBroadcastIds.contains(broadcast.getId());

            // Decide whether to include this message based on unreadOnly parameter
            if (!unreadOnly || !isRead) {
                NotificationDto dto = convertToDto(broadcast);
                dto.setIsRead(isRead);
                // Set broadcast message receive time to creation time, consistent with business logic
                dto.setReceivedAt(broadcast.getCreatedAt());
                result.add(dto);

                if (result.size() >= remainingLimit) {
                    break;
                }
            }
        }
        return result;
    }

    /**
     * Generic method for creating broadcast message query conditions
     */
    private LambdaQueryWrapper<Notification> createBroadcastQueryWrapper() {
        // SELECT * FROM notifications
        // WHERE type = 'broadcast'
        // AND (expire_at IS NULL OR expire_at > NOW())
        return Wrappers.lambdaQuery(Notification.class)
                .eq(Notification::getType, NotificationType.BROADCAST.getCode())
                .and(wrapper -> wrapper.isNull(Notification::getExpireAt).or().gt(Notification::getExpireAt, LocalDateTime.now()));
    }

    // ==================== Cache Eviction Helper Methods ====================

    /**
     * Internal broadcast message count cache method - not exposed publicly, for internal use only to
     * improve performance
     */
    @Cacheable(value = BROADCAST_COUNT_INTERNAL_CACHE, key = "'total'", cacheManager = "cacheManager5min")
    public long getBroadcastCountInternal() {
        return notificationMapper.selectCount(createBroadcastQueryWrapper());
    }

    /**
     * Get count of broadcast messages visible to user (broadcast messages after user registration) -
     * based on user-level cache
     */
    @Cacheable(value = USER_VISIBLE_BROADCAST_COUNT_CACHE, key = "#receiverUid", cacheManager = "cacheManager5min")
    public long getUserVisibleBroadcastCount(String receiverUid) {
        try {
            // Get user creation time
            var userInfoOpt = userInfoDataService.findByUid(receiverUid);
            if (userInfoOpt.isEmpty()) {
                log.warn("User not found for uid: {}", receiverUid);
                return 0L;
            }

            LocalDateTime userCreateTime = userInfoOpt.get().getCreateTime();
            if (userCreateTime == null) {
                log.warn("User create time is null for uid: {}", receiverUid);
                return 0L;
            }

            // Count broadcast messages after user registration
            long count = notificationMapper.countBroadcastMessagesAfter(userCreateTime);
            log.debug("User {} can see {} broadcast messages (created after {})",
                    receiverUid, count, userCreateTime);
            return count;
        } catch (Exception e) {
            log.error("Failed to get user visible broadcast count for user: {}", receiverUid, e);
            return 0L;
        }
    }

    /**
     * Evict user unread count cache
     */
    private void evictUserUnreadCountCache(String userId) {
        try {
            var cache = cacheManager.getCache(USER_UNREAD_COUNT_CACHE);
            if (cache != null) {
                cache.evict(userId);
                log.debug("Evicted user unread count cache for user: {}", userId);
            }
        } catch (Exception e) {
            log.warn("Failed to evict user unread count cache for user: {}", userId, e);
        }
    }

    /**
     * Evict all user count caches
     */
    private void evictUserCountCaches(String userId) {
        evictUserUnreadCountCache(userId);
        evictUserTotalCountCache(userId);
        evictUserVisibleBroadcastCountCache(userId);
    }

    /**
     * Evict user total count cache
     */
    private void evictUserTotalCountCache(String userId) {
        try {
            var cache = cacheManager.getCache(USER_TOTAL_COUNT_CACHE);
            if (cache != null) {
                cache.evict(userId);
                log.debug("Evicted user total count cache for user: {}", userId);
            }
        } catch (Exception e) {
            log.warn("Failed to evict user total count cache for user: {}", userId, e);
        }
    }

    /**
     * Evict user visible broadcast count cache
     */
    private void evictUserVisibleBroadcastCountCache(String userId) {
        try {
            var cache = cacheManager.getCache(USER_VISIBLE_BROADCAST_COUNT_CACHE);
            if (cache != null) {
                cache.evict(userId);
                log.debug("Evicted user visible broadcast count cache for user: {}", userId);
            }
        } catch (Exception e) {
            log.warn("Failed to evict user visible broadcast count cache for user: {}", userId, e);
        }
    }

    /**
     * Evict internal broadcast count cache
     */
    private void evictBroadcastCountInternalCache() {
        try {
            var cache = cacheManager.getCache(BROADCAST_COUNT_INTERNAL_CACHE);
            if (cache != null) {
                cache.evict("total");
                log.debug("Evicted internal broadcast count cache");
            }
        } catch (Exception e) {
            log.warn("Failed to evict internal broadcast count cache", e);
        }
    }

    /**
     * Evict all caches (for batch operations such as expiry cleanup)
     */
    private void evictAllCaches() {
        try {
            // Clear all user-related caches
            var unreadCache = cacheManager.getCache(USER_UNREAD_COUNT_CACHE);
            if (unreadCache != null) {
                unreadCache.clear();
            }

            var totalCache = cacheManager.getCache(USER_TOTAL_COUNT_CACHE);
            if (totalCache != null) {
                totalCache.clear();
            }

            var visibleBroadcastCache = cacheManager.getCache(USER_VISIBLE_BROADCAST_COUNT_CACHE);
            if (visibleBroadcastCache != null) {
                visibleBroadcastCache.clear();
            }

            // Clear internal broadcast count cache
            evictBroadcastCountInternalCache();

            log.info("Evicted all notification caches");
        } catch (Exception e) {
            log.warn("Failed to evict all notification caches", e);
        }
    }
}
