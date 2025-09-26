package com.iflytek.astron.console.hub.enums;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * NotificationType 枚举单元测试 测试枚举的基本功能和代码映射
 */
class NotificationTypeTest {

    @Test
    void testEnumValues() {
        NotificationType[] types = NotificationType.values();
        assertEquals(4, types.length);

        assertTrue(containsType(types, NotificationType.PERSONAL));
        assertTrue(containsType(types, NotificationType.BROADCAST));
        assertTrue(containsType(types, NotificationType.SYSTEM));
        assertTrue(containsType(types, NotificationType.PROMOTION));
    }

    @Test
    void testEnumCodeValues() {
        assertEquals("PERSONAL", NotificationType.PERSONAL.getCode());
        assertEquals("BROADCAST", NotificationType.BROADCAST.getCode());
        assertEquals("SYSTEM", NotificationType.SYSTEM.getCode());
        assertEquals("PROMOTION", NotificationType.PROMOTION.getCode());
    }

    @Test
    void testEnumDescriptions() {
        assertEquals("Personal message", NotificationType.PERSONAL.getDescription());
        assertEquals("Broadcast message", NotificationType.BROADCAST.getDescription());
        assertEquals("System notification", NotificationType.SYSTEM.getDescription());
        assertEquals("Promotion message", NotificationType.PROMOTION.getDescription());
    }

    @Test
    void testFromCode_ValidCodes() {
        assertEquals(NotificationType.PERSONAL, NotificationType.fromCode("PERSONAL"));
        assertEquals(NotificationType.BROADCAST, NotificationType.fromCode("BROADCAST"));
        assertEquals(NotificationType.SYSTEM, NotificationType.fromCode("SYSTEM"));
        assertEquals(NotificationType.PROMOTION, NotificationType.fromCode("PROMOTION"));
    }

    @Test
    void testFromCode_InvalidCode() {
        assertNull(NotificationType.fromCode("INVALID"));
        assertNull(NotificationType.fromCode("invalid"));
        assertNull(NotificationType.fromCode(""));
        assertNull(NotificationType.fromCode("personal")); // 大小写敏感
    }

    @Test
    void testFromCode_NullCode() {
        assertNull(NotificationType.fromCode(null));
    }

    @Test
    void testEnumName() {
        // 测试枚举常量名（用于 MyBatis 默认映射）
        assertEquals("PERSONAL", NotificationType.PERSONAL.name());
        assertEquals("BROADCAST", NotificationType.BROADCAST.name());
        assertEquals("SYSTEM", NotificationType.SYSTEM.name());
        assertEquals("PROMOTION", NotificationType.PROMOTION.name());
    }

    @Test
    void testCodeEqualsName() {
        // 验证 code 值与枚举常量名一致（确保 MyBatis 映射正确）
        for (NotificationType type : NotificationType.values()) {
            assertEquals(type.name(), type.getCode(),
                    "枚举 " + type.name() + " 的 code 值应该与常量名一致");
        }
    }

    @Test
    void testValueOf() {
        // 测试 valueOf 方法（MyBatis 可能使用）
        assertEquals(NotificationType.PERSONAL, NotificationType.valueOf("PERSONAL"));
        assertEquals(NotificationType.BROADCAST, NotificationType.valueOf("BROADCAST"));
        assertEquals(NotificationType.SYSTEM, NotificationType.valueOf("SYSTEM"));
        assertEquals(NotificationType.PROMOTION, NotificationType.valueOf("PROMOTION"));
    }

    @Test
    void testValueOf_InvalidValue() {
        assertThrows(IllegalArgumentException.class,
                () -> NotificationType.valueOf("INVALID"));
        assertThrows(IllegalArgumentException.class,
                () -> NotificationType.valueOf("personal"));
    }

    @Test
    void testEnumOrdinal() {
        // 测试枚举序号（如果使用 EnumOrdinalTypeHandler）
        assertEquals(0, NotificationType.PERSONAL.ordinal());
        assertEquals(1, NotificationType.BROADCAST.ordinal());
        assertEquals(2, NotificationType.SYSTEM.ordinal());
        assertEquals(3, NotificationType.PROMOTION.ordinal());
    }

    @Test
    void testEnumEquality() {
        NotificationType type1 = NotificationType.PERSONAL;
        NotificationType type2 = NotificationType.valueOf("PERSONAL");
        NotificationType type3 = NotificationType.fromCode("PERSONAL");

        assertEquals(type1, type2);
        assertEquals(type1, type3);
        assertEquals(type2, type3);

        // 测试 == 比较
        assertSame(type1, type2);
        // fromCode 返回的是通过遍历找到的，应该仍然是同一个实例
        assertSame(type1, type3);
    }

    @Test
    void testEnumToString() {
        // 默认 toString 返回枚举常量名
        assertEquals("PERSONAL", NotificationType.PERSONAL.toString());
        assertEquals("BROADCAST", NotificationType.BROADCAST.toString());
        assertEquals("SYSTEM", NotificationType.SYSTEM.toString());
        assertEquals("PROMOTION", NotificationType.PROMOTION.toString());
    }

    @Test
    void testMybatisCompatibility() {
        // 模拟 MyBatis 可能的转换场景

        // 场景 1: 数据库值到枚举（使用 code）
        String dbValue = "PERSONAL";
        NotificationType fromDb = NotificationType.fromCode(dbValue);
        assertEquals(NotificationType.PERSONAL, fromDb);

        // 场景 2: 枚举到数据库值（使用 name）
        String toDb = NotificationType.PERSONAL.name();
        assertEquals("PERSONAL", toDb);

        // 场景 3: 验证双向转换一致性
        for (NotificationType type : NotificationType.values()) {
            String code = type.getCode();
            NotificationType converted = NotificationType.fromCode(code);
            assertEquals(type, converted);

            String name = type.name();
            NotificationType fromName = NotificationType.valueOf(name);
            assertEquals(type, fromName);
        }
    }

    private boolean containsType(NotificationType[] types, NotificationType target) {
        for (NotificationType type : types) {
            if (type == target) {
                return true;
            }
        }
        return false;
    }
}
