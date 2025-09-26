package com.iflytek.astron.console.hub.dto.notification;

import com.iflytek.astron.console.hub.enums.NotificationType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * NotificationPageResponse 单元测试 测试分页响应和按类型分组功能
 */
class NotificationPageResponseTest {

    private List<NotificationDto> testNotifications;

    @BeforeEach
    void setUp() {
        testNotifications = createTestNotifications();
    }

    private List<NotificationDto> createTestNotifications() {
        LocalDateTime now = LocalDateTime.now();

        NotificationDto personal1 = new NotificationDto();
        personal1.setId(1L);
        personal1.setType(NotificationType.PERSONAL);
        personal1.setTitle("个人消息1");
        personal1.setCreatedAt(now);

        NotificationDto personal2 = new NotificationDto();
        personal2.setId(2L);
        personal2.setType(NotificationType.PERSONAL);
        personal2.setTitle("个人消息2");
        personal2.setCreatedAt(now.minusHours(1));

        NotificationDto broadcast1 = new NotificationDto();
        broadcast1.setId(3L);
        broadcast1.setType(NotificationType.BROADCAST);
        broadcast1.setTitle("广播消息1");
        broadcast1.setCreatedAt(now.minusHours(2));

        NotificationDto system1 = new NotificationDto();
        system1.setId(4L);
        system1.setType(NotificationType.SYSTEM);
        system1.setTitle("系统通知1");
        system1.setCreatedAt(now.minusHours(3));

        NotificationDto promotion1 = new NotificationDto();
        promotion1.setId(5L);
        promotion1.setType(NotificationType.PROMOTION);
        promotion1.setTitle("推广消息1");
        promotion1.setCreatedAt(now.minusHours(4));

        return Arrays.asList(personal1, personal2, broadcast1, system1, promotion1);
    }

    @Test
    void testBasicPageResponseFields() {
        NotificationPageResponse response = new NotificationPageResponse(
                testNotifications, 0, 10, 15L, 3L);

        assertEquals(testNotifications, response.getNotifications());
        assertEquals(0, response.getPageIndex());
        assertEquals(10, response.getPageSize());
        assertEquals(15L, response.getTotalCount());
        assertEquals(3L, response.getUnreadCount());
        assertEquals(2, response.getTotalPages()); // Math.ceil(15/10) = 2
    }

    @Test
    void testTotalPagesCalculation() {
        // 测试不同的总页数计算
        NotificationPageResponse response1 = new NotificationPageResponse(
                testNotifications, 0, 10, 25L, 5L);
        assertEquals(3, response1.getTotalPages()); // Math.ceil(25/10) = 3

        NotificationPageResponse response2 = new NotificationPageResponse(
                testNotifications, 0, 10, 10L, 2L);
        assertEquals(1, response2.getTotalPages()); // Math.ceil(10/10) = 1

        NotificationPageResponse response3 = new NotificationPageResponse(
                testNotifications, 0, 10, 0L, 0L);
        assertEquals(0, response3.getTotalPages()); // Math.ceil(0/10) = 0
    }

    @Test
    void testTotalPagesWithZeroPageSize() {
        // 测试页面大小为 0 的情况
        NotificationPageResponse response = new NotificationPageResponse(
                testNotifications, 0, 0, 15L, 3L);
        assertEquals(0, response.getTotalPages());
    }

    @Test
    void testNotificationsByTypeGrouping() {
        NotificationPageResponse response = new NotificationPageResponse(
                testNotifications, 0, 10, 15L, 3L);

        Map<NotificationType, List<NotificationDto>> groupedNotifications =
                response.getNotificationsByType();

        assertNotNull(groupedNotifications);
        assertEquals(4, groupedNotifications.size()); // 4种类型

        // 验证 PERSONAL 类型的通知
        List<NotificationDto> personalNotifications = groupedNotifications.get(NotificationType.PERSONAL);
        assertNotNull(personalNotifications);
        assertEquals(2, personalNotifications.size());
        assertTrue(personalNotifications.stream()
                .allMatch(n -> n.getType() == NotificationType.PERSONAL));
        assertTrue(personalNotifications.stream()
                .anyMatch(n -> "个人消息1".equals(n.getTitle())));
        assertTrue(personalNotifications.stream()
                .anyMatch(n -> "个人消息2".equals(n.getTitle())));

        // 验证 BROADCAST 类型的通知
        List<NotificationDto> broadcastNotifications = groupedNotifications.get(NotificationType.BROADCAST);
        assertNotNull(broadcastNotifications);
        assertEquals(1, broadcastNotifications.size());
        assertEquals("广播消息1", broadcastNotifications.get(0).getTitle());

        // 验证 SYSTEM 类型的通知
        List<NotificationDto> systemNotifications = groupedNotifications.get(NotificationType.SYSTEM);
        assertNotNull(systemNotifications);
        assertEquals(1, systemNotifications.size());
        assertEquals("系统通知1", systemNotifications.get(0).getTitle());

        // 验证 PROMOTION 类型的通知
        List<NotificationDto> promotionNotifications = groupedNotifications.get(NotificationType.PROMOTION);
        assertNotNull(promotionNotifications);
        assertEquals(1, promotionNotifications.size());
        assertEquals("推广消息1", promotionNotifications.get(0).getTitle());
    }

    @Test
    void testEmptyNotificationsList() {
        List<NotificationDto> emptyList = Arrays.asList();
        NotificationPageResponse response = new NotificationPageResponse(
                emptyList, 0, 10, 0L, 0L);

        assertEquals(0, response.getNotifications().size());
        assertEquals(0, response.getTotalPages());
        Map<NotificationType, List<NotificationDto>> groupedNotifications =
                response.getNotificationsByType();
        assertNotNull(groupedNotifications);
        assertEquals(0, groupedNotifications.size());
    }

    @Test
    void testSingleTypeNotifications() {
        // 测试只有一种类型的通知
        List<NotificationDto> singleTypeNotifications = Arrays.asList(
                testNotifications.get(0), testNotifications.get(1)); // 两个 PERSONAL 类型

        NotificationPageResponse response = new NotificationPageResponse(
                singleTypeNotifications, 0, 10, 2L, 1L);

        Map<NotificationType, List<NotificationDto>> groupedNotifications =
                response.getNotificationsByType();

        assertEquals(1, groupedNotifications.size());
        assertTrue(groupedNotifications.containsKey(NotificationType.PERSONAL));
        assertEquals(2, groupedNotifications.get(NotificationType.PERSONAL).size());
    }

    @Test
    void testNotificationsWithNullType() {
        // 创建一个类型为 null 的通知
        NotificationDto nullTypeNotification = new NotificationDto();
        nullTypeNotification.setId(99L);
        nullTypeNotification.setType(null);
        nullTypeNotification.setTitle("空类型消息");

        List<NotificationDto> mixedNotifications = Arrays.asList(
                testNotifications.get(0), nullTypeNotification);

        NotificationPageResponse response = new NotificationPageResponse(
                mixedNotifications, 0, 10, 2L, 1L);

        Map<NotificationType, List<NotificationDto>> groupedNotifications =
                response.getNotificationsByType();

        // 验证 null 类型被映射为 SYSTEM 类型
        assertTrue(groupedNotifications.containsKey(NotificationType.SYSTEM));
        // SYSTEM 类型应该包含原来的通知和 null 类型的通知
        List<NotificationDto> systemNotifications = groupedNotifications.get(NotificationType.SYSTEM);
        assertTrue(systemNotifications.size() >= 1);

        // 验证至少有一条通知的标题是 "空类型消息"
        boolean hasNullTypeNotification = systemNotifications.stream()
                .anyMatch(notification -> "空类型消息".equals(notification.getTitle()));
        assertTrue(hasNullTypeNotification, "应该包含标题为 '空类型消息' 的通知");

        assertTrue(groupedNotifications.containsKey(NotificationType.PERSONAL));
        assertEquals(1, groupedNotifications.get(NotificationType.PERSONAL).size());
    }

    @Test
    void testResponseEquals() {
        NotificationPageResponse response1 = new NotificationPageResponse(
                testNotifications, 0, 10, 15L, 3L);

        NotificationPageResponse response2 = new NotificationPageResponse(
                testNotifications, 0, 10, 15L, 3L);

        // 由于 Lombok 生成的 equals 方法
        assertEquals(response1, response2);
        assertEquals(response1.hashCode(), response2.hashCode());
    }

    @Test
    void testResponseToString() {
        NotificationPageResponse response = new NotificationPageResponse(
                testNotifications, 1, 5, 20L, 8L);

        String toString = response.toString();
        assertNotNull(toString);
        assertTrue(toString.contains("NotificationPageResponse"));
    }
}
