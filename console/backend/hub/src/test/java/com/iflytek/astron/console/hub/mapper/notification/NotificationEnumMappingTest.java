package com.iflytek.astron.console.hub.mapper.notification;

import com.iflytek.astron.console.hub.dto.notification.NotificationDto;
import com.iflytek.astron.console.hub.enums.NotificationType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 专门测试 NotificationType 枚举映射兼容性的单元测试 验证 MyBatis 枚举映射的正确性，无需实际数据库连接
 */
class NotificationEnumMappingTest {

    @Test
    @DisplayName("验证枚举常量名与code值一致性（MyBatis映射关键）")
    void testEnumNameAndCodeConsistency() {
        for (NotificationType type : NotificationType.values()) {
            assertEquals(type.name(), type.getCode(),
                    String.format("枚举 %s 的 name() 和 getCode() 必须一致，以确保 MyBatis 正确映射", type.name()));
        }
    }

    @Test
    @DisplayName("验证 MyBatis valueOf 映射兼容性")
    void testMybatisValueOfCompatibility() {
        // MyBatis 在反序列化时会使用 valueOf(String) 方法
        String[] dbStringValues = {"PERSONAL", "BROADCAST", "SYSTEM", "PROMOTION"};

        for (String dbValue : dbStringValues) {
            // 模拟 MyBatis 的枚举转换过程
            NotificationType enumValue = NotificationType.valueOf(dbValue);

            assertNotNull(enumValue);
            assertEquals(dbValue, enumValue.name());
            assertEquals(dbValue, enumValue.getCode());
        }
    }

    @Test
    @DisplayName("验证 MyBatis name() 序列化兼容性")
    void testMybatisNameSerializationCompatibility() {
        // MyBatis 在序列化时会使用 name() 方法
        for (NotificationType type : NotificationType.values()) {
            String serializedValue = type.name();

            // 验证序列化后的值可以正确反序列化
            NotificationType deserializedEnum = NotificationType.valueOf(serializedValue);
            assertEquals(type, deserializedEnum);

            // 验证与 code 值一致
            assertEquals(type.getCode(), serializedValue);
        }
    }

    @Test
    @DisplayName("验证 fromCode 方法的数据库兼容性")
    void testFromCodeDatabaseCompatibility() {
        // 模拟从数据库查询到的字符串值
        String[] potentialDbValues = {"PERSONAL", "BROADCAST", "SYSTEM", "PROMOTION",
                "personal", "broadcast", "invalid", null, ""};

        for (String dbValue : potentialDbValues) {
            NotificationType result = NotificationType.fromCode(dbValue);

            if (Arrays.asList("PERSONAL", "BROADCAST", "SYSTEM", "PROMOTION").contains(dbValue)) {
                assertNotNull(result, "有效的数据库值 " + dbValue + " 应该返回对应的枚举");
                assertEquals(dbValue, result.getCode());
            } else {
                assertNull(result, "无效的数据库值 " + dbValue + " 应该返回 null");
            }
        }
    }

    @Test
    @DisplayName("验证 NotificationDto 类型分组功能的枚举兼容性")
    void testNotificationDtoTypeGroupingCompatibility() {
        // 创建模拟的 NotificationDto 列表
        List<NotificationDto> notifications = createMockNotificationDtos();

        // 使用 Stream API 按类型分组（模拟 NotificationPageResponse 的逻辑）
        Map<NotificationType, List<NotificationDto>> groupedByType = notifications.stream()
                .collect(Collectors.groupingBy(NotificationDto::getType));

        // 验证分组结果
        assertEquals(4, groupedByType.size(), "应该有4种不同的通知类型");

        for (NotificationType type : NotificationType.values()) {
            assertTrue(groupedByType.containsKey(type),
                    "分组结果应该包含" + type.name() + "类型");

            List<NotificationDto> typeNotifications = groupedByType.get(type);
            assertFalse(typeNotifications.isEmpty());

            // 验证该组中的所有通知都是相同类型
            typeNotifications.forEach(dto -> assertEquals(type, dto.getType(), "分组中的通知类型应该一致"));
        }
    }

    @Test
    @DisplayName("验证空类型处理的兼容性")
    void testNullTypeHandlingCompatibility() {
        // 创建包含 null 类型的通知
        NotificationDto nullTypeDto = new NotificationDto();
        nullTypeDto.setId(999L);
        nullTypeDto.setType(null);
        nullTypeDto.setTitle("空类型通知");

        List<NotificationDto> notifications = createMockNotificationDtos();
        notifications.add(nullTypeDto);

        // 测试分组时对 null 类型的处理（使用与 NotificationPageResponse 相同的逻辑）
        Map<NotificationType, List<NotificationDto>> groupedByType = notifications.stream()
                .collect(Collectors.groupingBy(dto -> dto.getType() != null ? dto.getType() : NotificationType.SYSTEM));

        // 验证 null 类型被映射为 SYSTEM 类型
        assertTrue(groupedByType.containsKey(NotificationType.SYSTEM), "应该包含 SYSTEM 类型的分组");
        List<NotificationDto> systemNotifications = groupedByType.get(NotificationType.SYSTEM);
        assertTrue(systemNotifications.size() >= 1, "SYSTEM 类型分组应该至少有一个元素");

        // 验证包含 null 类型的通知
        boolean hasNullTypeNotification = systemNotifications.stream()
                .anyMatch(dto -> "空类型通知".equals(dto.getTitle()));
        assertTrue(hasNullTypeNotification, "应该包含标题为 '空类型通知' 的通知");
    }

    @Test
    @DisplayName("验证枚举的 ordinal 值稳定性")
    void testEnumOrdinalStability() {
        // 验证枚举的序号值（如果使用 EnumOrdinalTypeHandler）
        assertEquals(0, NotificationType.PERSONAL.ordinal());
        assertEquals(1, NotificationType.BROADCAST.ordinal());
        assertEquals(2, NotificationType.SYSTEM.ordinal());
        assertEquals(3, NotificationType.PROMOTION.ordinal());

        // 警告：ordinal 值不应该用于持久化，因为添加新枚举值会改变序号
        // 这个测试主要是为了确保枚举值的顺序保持稳定
    }

    @Test
    @DisplayName("模拟 MyBatis TypeHandler 转换过程")
    void testMybatisTypeHandlerSimulation() {
        for (NotificationType type : NotificationType.values()) {
            // 模拟 MyBatis 写入数据库时的转换（枚举 -> 字符串）
            String dbValue = type.name(); // 默认 EnumTypeHandler 使用 name()

            // 模拟 MyBatis 从数据库读取时的转换（字符串 -> 枚举）
            NotificationType reconstructedEnum = NotificationType.valueOf(dbValue);

            // 验证往返转换的正确性
            assertEquals(type, reconstructedEnum);
            assertEquals(type.getCode(), dbValue);
            assertEquals(type.name(), dbValue);
        }
    }

    /**
     * 创建模拟的 NotificationDto 列表用于测试
     */
    private List<NotificationDto> createMockNotificationDtos() {
        return Arrays.stream(NotificationType.values())
                .map(type -> {
                    NotificationDto dto = new NotificationDto();
                    dto.setId((long) type.ordinal() + 1);
                    dto.setType(type);
                    dto.setTitle("测试通知 - " + type.getDescription());
                    dto.setBody("测试内容");
                    return dto;
                })
                .collect(Collectors.toList());
    }
}
