package com.iflytek.astron.console.hub.dto.notification;

import com.iflytek.astron.console.hub.enums.NotificationType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;

/**
 * NotificationDto 单元测试 测试枚举类型映射和基本功能
 */
class NotificationDtoTest {

    private NotificationDto notificationDto;

    @BeforeEach
    void setUp() {
        notificationDto = new NotificationDto();
    }

    @Test
    void testNotificationTypeEnum() {
        // 测试设置和获取枚举类型
        notificationDto.setType(NotificationType.PERSONAL);
        assertEquals(NotificationType.PERSONAL, notificationDto.getType());

        notificationDto.setType(NotificationType.BROADCAST);
        assertEquals(NotificationType.BROADCAST, notificationDto.getType());

        notificationDto.setType(NotificationType.SYSTEM);
        assertEquals(NotificationType.SYSTEM, notificationDto.getType());

        notificationDto.setType(NotificationType.PROMOTION);
        assertEquals(NotificationType.PROMOTION, notificationDto.getType());
    }

    @Test
    void testNotificationDtoFields() {
        // 设置所有字段
        LocalDateTime now = LocalDateTime.now();

        notificationDto.setId(1L);
        notificationDto.setType(NotificationType.PERSONAL);
        notificationDto.setTitle("测试标题");
        notificationDto.setBody("测试内容");
        notificationDto.setTemplateCode("TEST_TEMPLATE");
        notificationDto.setPayload("{\"key\":\"value\"}");
        notificationDto.setCreatorUid("creator123");
        notificationDto.setCreatedAt(now);
        notificationDto.setExpireAt(now.plusDays(7));
        notificationDto.setMeta("{\"meta\":\"data\"}");
        notificationDto.setIsRead(false);
        notificationDto.setReadAt(null);
        notificationDto.setReceivedAt(now);

        // 验证所有字段
        assertEquals(1L, notificationDto.getId());
        assertEquals(NotificationType.PERSONAL, notificationDto.getType());
        assertEquals("测试标题", notificationDto.getTitle());
        assertEquals("测试内容", notificationDto.getBody());
        assertEquals("TEST_TEMPLATE", notificationDto.getTemplateCode());
        assertEquals("{\"key\":\"value\"}", notificationDto.getPayload());
        assertEquals("creator123", notificationDto.getCreatorUid());
        assertEquals(now, notificationDto.getCreatedAt());
        assertEquals(now.plusDays(7), notificationDto.getExpireAt());
        assertEquals("{\"meta\":\"data\"}", notificationDto.getMeta());
        assertFalse(notificationDto.getIsRead());
        assertNull(notificationDto.getReadAt());
        assertEquals(now, notificationDto.getReceivedAt());
    }

    @Test
    void testNotificationDtoEqualsAndHashCode() {
        NotificationDto dto1 = new NotificationDto();
        dto1.setId(1L);
        dto1.setType(NotificationType.PERSONAL);
        dto1.setTitle("标题");

        NotificationDto dto2 = new NotificationDto();
        dto2.setId(1L);
        dto2.setType(NotificationType.PERSONAL);
        dto2.setTitle("标题");

        // Lombok 生成的 equals 和 hashCode
        assertEquals(dto1, dto2);
        assertEquals(dto1.hashCode(), dto2.hashCode());
    }

    @Test
    void testNotificationDtoToString() {
        notificationDto.setId(1L);
        notificationDto.setType(NotificationType.SYSTEM);
        notificationDto.setTitle("系统通知");

        String toString = notificationDto.toString();

        // 验证 toString 包含关键信息
        assertNotNull(toString);
        assertTrue(toString.contains("NotificationDto"));
        assertTrue(toString.contains("id=1"));
        assertTrue(toString.contains("SYSTEM"));
        assertTrue(toString.contains("系统通知"));
    }

    @Test
    void testNullTypeHandling() {
        // 测试空类型处理
        notificationDto.setType(null);
        assertNull(notificationDto.getType());
    }

    @Test
    void testReadStatusFields() {
        LocalDateTime readTime = LocalDateTime.now();

        // 测试未读状态
        notificationDto.setIsRead(false);
        notificationDto.setReadAt(null);
        assertFalse(notificationDto.getIsRead());
        assertNull(notificationDto.getReadAt());

        // 测试已读状态
        notificationDto.setIsRead(true);
        notificationDto.setReadAt(readTime);
        assertTrue(notificationDto.getIsRead());
        assertEquals(readTime, notificationDto.getReadAt());
    }
}
