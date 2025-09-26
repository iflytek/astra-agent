package com.iflytek.astron.console.hub.dto.notification;

import com.iflytek.astron.console.hub.enums.NotificationType;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 测试通知分组功能，特别是空类型处理
 */
class NotificationGroupingTest {

    @Test
    void testNullTypeHandling() {
        // 创建测试数据
        List<NotificationDto> notifications = createTestNotifications();

        // 模拟 NotificationPageResponse 的分组逻辑
        Map<NotificationType, List<NotificationDto>> groupedByType = notifications.stream()
                .collect(Collectors.groupingBy(notification -> notification.getType() != null ? notification.getType() : NotificationType.SYSTEM));

        // 验证分组结果
        assertNotNull(groupedByType);
        assertTrue(groupedByType.containsKey(NotificationType.PERSONAL));
        assertTrue(groupedByType.containsKey(NotificationType.SYSTEM));

        // 验证 SYSTEM 类型包含原始和 null 类型的通知
        List<NotificationDto> systemNotifications = groupedByType.get(NotificationType.SYSTEM);
        assertTrue(systemNotifications.size() >= 2); // 至少包含原始的 SYSTEM 和 null 类型的通知

        // 验证包含 null 类型通知
        boolean hasNullTypeNotification = systemNotifications.stream()
                .anyMatch(n -> "Null类型通知".equals(n.getTitle()));
        assertTrue(hasNullTypeNotification);
    }

    @Test
    void testNotificationPageResponseConstruction() {
        List<NotificationDto> notifications = createTestNotifications();

        // 测试 NotificationPageResponse 构造
        NotificationPageResponse response = new NotificationPageResponse(
                notifications, 0, 10, 5L, 2L);

        assertNotNull(response);
        assertEquals(notifications, response.getNotifications());
        assertEquals(0, response.getPageIndex());
        assertEquals(10, response.getPageSize());
        assertEquals(5L, response.getTotalCount());
        assertEquals(2L, response.getUnreadCount());
        assertEquals(1, response.getTotalPages());

        // 验证分组功能
        Map<NotificationType, List<NotificationDto>> groupedNotifications =
                response.getNotificationsByType();
        assertNotNull(groupedNotifications);
        assertTrue(groupedNotifications.containsKey(NotificationType.PERSONAL));
        assertTrue(groupedNotifications.containsKey(NotificationType.BROADCAST));
        assertTrue(groupedNotifications.containsKey(NotificationType.SYSTEM));
        assertTrue(groupedNotifications.containsKey(NotificationType.PROMOTION));

        // 验证 null 类型被映射到 SYSTEM
        List<NotificationDto> systemNotifications = groupedNotifications.get(NotificationType.SYSTEM);
        boolean hasNullTypeNotification = systemNotifications.stream()
                .anyMatch(n -> "Null类型通知".equals(n.getTitle()));
        assertTrue(hasNullTypeNotification, "null类型通知应该被映射到SYSTEM类型");
    }

    private List<NotificationDto> createTestNotifications() {
        NotificationDto personal = new NotificationDto();
        personal.setId(1L);
        personal.setType(NotificationType.PERSONAL);
        personal.setTitle("个人通知");

        NotificationDto broadcast = new NotificationDto();
        broadcast.setId(2L);
        broadcast.setType(NotificationType.BROADCAST);
        broadcast.setTitle("广播通知");

        NotificationDto system = new NotificationDto();
        system.setId(3L);
        system.setType(NotificationType.SYSTEM);
        system.setTitle("系统通知");

        NotificationDto promotion = new NotificationDto();
        promotion.setId(4L);
        promotion.setType(NotificationType.PROMOTION);
        promotion.setTitle("推广通知");

        NotificationDto nullType = new NotificationDto();
        nullType.setId(5L);
        nullType.setType(null); // 空类型
        nullType.setTitle("Null类型通知");

        return Arrays.asList(personal, broadcast, system, promotion, nullType);
    }
}
