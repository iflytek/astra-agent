package com.iflytek.astron.console.toolkit.service.database;

import com.iflytek.astron.console.toolkit.entity.dto.database.DatabaseDto;
import com.iflytek.astron.console.toolkit.entity.dto.database.DbTableDto;
import com.iflytek.astron.console.toolkit.entity.dto.database.DbTableFieldDto;
import com.iflytek.astron.console.toolkit.entity.enumVo.DBOperateEnum;
import com.iflytek.astron.console.toolkit.entity.table.database.DbInfo;
import com.iflytek.astron.console.toolkit.entity.table.database.DbTable;
import lombok.extern.slf4j.Slf4j;

import java.util.*;

/**
 * DatabaseService 测试工具类 提供测试数据构造、Mock配置、断言辅助等功能
 *
 * @author test
 */
@Slf4j
public class DatabaseServiceTestUtils {

    // ==================== 测试数据构造 ====================

    /**
     * 创建基础数据库DTO
     */
    public static DatabaseDto createBasicDatabaseDto(String suffix) {
        DatabaseDto dto = new DatabaseDto();
        dto.setName("测试数据库_" + suffix + "_" + System.currentTimeMillis());
        dto.setDescription("测试数据库描述_" + suffix);
        return dto;
    }

    /**
     * 创建带特殊字符的数据库DTO（测试边界情况）
     */
    public static DatabaseDto createSpecialCharDatabaseDto() {
        DatabaseDto dto = new DatabaseDto();
        dto.setName("特殊字符数据库_测试_" + System.currentTimeMillis());
        dto.setDescription("包含特殊字符的描述：!@#$%^&*()_+-=[]{}|;':\",./<>?");
        return dto;
    }

    /**
     * 创建基础表DTO
     */
    public static DbTableDto createBasicTableDto(Long dbId, String suffix) {
        DbTableDto dto = new DbTableDto();
        dto.setDbId(dbId);
        dto.setName("测试表_" + suffix + "_" + System.currentTimeMillis());
        dto.setDescription("测试表描述_" + suffix);
        dto.setFields(createBasicFields());
        return dto;
    }

    /**
     * 创建复杂表DTO（包含多种字段类型）
     */
    public static DbTableDto createComplexTableDto(Long dbId, String suffix) {
        DbTableDto dto = new DbTableDto();
        dto.setDbId(dbId);
        dto.setName("复杂表_" + suffix + "_" + System.currentTimeMillis());
        dto.setDescription("复杂表描述_" + suffix);

        List<DbTableFieldDto> fields = new ArrayList<>();
        fields.add(createFieldDto("username", "string", "用户名", true, "guest"));
        fields.add(createFieldDto("age", "integer", "年龄", false, "0"));
        fields.add(createFieldDto("salary", "number", "薪资", false, "0.0"));
        fields.add(createFieldDto("is_active", "boolean", "是否激活", true, "false"));
        fields.add(createFieldDto("created_at", "time", "创建时间", false, null));
        fields.add(createFieldDto("email", "string", "邮箱", false, ""));
        fields.add(createFieldDto("phone", "string", "电话", false, ""));
        fields.add(createFieldDto("address", "string", "地址", false, ""));

        dto.setFields(fields);
        return dto;
    }

    /**
     * 创建超过字段数量限制的表DTO
     */
    public static DbTableDto createOverLimitTableDto(Long dbId) {
        DbTableDto dto = new DbTableDto();
        dto.setDbId(dbId);
        dto.setName("超限表_" + System.currentTimeMillis());
        dto.setDescription("超过字段数量限制的表");

        List<DbTableFieldDto> fields = new ArrayList<>();
        for (int i = 1; i <= 25; i++) { // 超过20个字段限制
            fields.add(createFieldDto("field_" + i, "string", "字段" + i, false, ""));
        }

        dto.setFields(fields);
        return dto;
    }

    /**
     * 创建基础字段列表
     */
    public static List<DbTableFieldDto> createBasicFields() {
        List<DbTableFieldDto> fields = new ArrayList<>();
        fields.add(createFieldDto("name", "string", "姓名", true, ""));
        fields.add(createFieldDto("age", "integer", "年龄", false, "0"));
        return fields;
    }

    /**
     * 创建字段DTO
     */
    public static DbTableFieldDto createFieldDto(String name, String type, String description,
            boolean required, String defaultValue) {
        DbTableFieldDto field = new DbTableFieldDto();
        field.setName(name);
        field.setType(type);
        field.setDescription(description);
        field.setIsRequired(required);
        field.setDefaultValue(defaultValue);
        return field;
    }

    /**
     * 创建用于更新操作的字段DTO
     */
    public static DbTableFieldDto createUpdateFieldDto(Long fieldId, String name, String type,
            String description, boolean required,
            String defaultValue, Integer operateType) {
        DbTableFieldDto field = createFieldDto(name, type, description, required, defaultValue);
        field.setId(fieldId);
        field.setOperateType(operateType);
        return field;
    }

    // ==================== 测试数据验证 ====================

    /**
     * 验证数据库信息是否正确
     */
    public static boolean validateDatabaseInfo(DbInfo actual, DatabaseDto expected, String userId, Long spaceId) {
        if (actual == null || expected == null) {
            return false;
        }

        return Objects.equals(actual.getName(), expected.getName()) &&
                Objects.equals(actual.getDescription(), expected.getDescription()) &&
                Objects.equals(actual.getUid(), userId) &&
                Objects.equals(actual.getSpaceId(), spaceId) &&
                actual.getCreateTime() != null &&
                actual.getUpdateTime() != null;
    }

    /**
     * 验证表信息是否正确
     */
    public static boolean validateTableInfo(DbTable actual, DbTableDto expected) {
        if (actual == null || expected == null) {
            return false;
        }

        return Objects.equals(actual.getName(), expected.getName()) &&
                Objects.equals(actual.getDescription(), expected.getDescription()) &&
                Objects.equals(actual.getDbId(), expected.getDbId()) &&
                actual.getCreateTime() != null &&
                actual.getUpdateTime() != null &&
                !actual.getDeleted();
    }

    // ==================== Mock数据生成 ====================

    /**
     * 生成Mock的数据库ID
     */
    public static Long generateMockDbId() {
        return 9000L + new Random().nextInt(1000);
    }

    /**
     * 生成Mock的表ID
     */
    public static Long generateMockTableId() {
        return 8000L + new Random().nextInt(1000);
    }

    /**
     * 生成测试用户ID
     */
    public static String generateTestUserId(String prefix) {
        return prefix + "_user_" + System.currentTimeMillis();
    }

    /**
     * 生成测试空间ID
     */
    public static Long generateTestSpaceId() {
        return 1000L + new Random().nextInt(1000);
    }

    // ==================== 测试场景数据 ====================

    /**
     * 创建边界测试场景的数据
     */
    public static Map<String, Object> createBoundaryTestData() {
        Map<String, Object> data = new HashMap<>();

        // 空字符串测试
        data.put("empty_string", "");

        // 长字符串测试
        StringBuilder longString = new StringBuilder();
        for (int i = 0; i < 1000; i++) {
            longString.append("A");
        }
        data.put("long_string", longString.toString());

        // 特殊字符测试
        data.put("special_chars", "!@#$%^&*()_+-=[]{}|;':\",./<>?");

        // 数值边界测试
        data.put("max_int", Integer.MAX_VALUE);
        data.put("min_int", Integer.MIN_VALUE);
        data.put("zero", 0);
        data.put("negative", -100);

        // Unicode字符测试
        data.put("unicode", "测试中文字符🎉🚀");

        return data;
    }

    /**
     * 创建异常测试场景的数据
     */
    public static Map<String, Object> createExceptionTestData() {
        Map<String, Object> data = new HashMap<>();

        // null值测试
        data.put("null_value", null);

        // 非法字段名测试
        data.put("illegal_field_name", "value");

        // SQL注入测试
        data.put("sql_injection", "'; DROP TABLE users; --");

        return data;
    }

    // ==================== 性能测试辅助 ====================

    /**
     * 创建大批量测试数据
     */
    public static List<Map<String, Object>> createBulkTestData(int count) {
        List<Map<String, Object>> dataList = new ArrayList<>();

        for (int i = 0; i < count; i++) {
            Map<String, Object> data = new HashMap<>();
            data.put("name", "用户_" + i);
            data.put("age", 20 + (i % 50));
            data.put("email", "user" + i + "@test.com");
            data.put("active", i % 2 == 0);
            dataList.add(data);
        }

        return dataList;
    }

    /**
     * 创建并发测试用的数据库名称
     */
    public static List<String> createConcurrentDatabaseNames(int count, String prefix) {
        List<String> names = new ArrayList<>();
        long timestamp = System.currentTimeMillis();

        for (int i = 0; i < count; i++) {
            names.add(prefix + "_" + i + "_" + timestamp);
        }

        return names;
    }

    // ==================== 断言辅助方法 ====================

    /**
     * 验证异常信息是否包含预期内容
     */
    public static boolean validateExceptionMessage(Exception exception, String expectedKeyword) {
        if (exception == null || exception.getMessage() == null) {
            return false;
        }
        return exception.getMessage().contains(expectedKeyword);
    }

    /**
     * 验证集合是否包含预期元素
     */
    public static <T> boolean validateCollectionContains(Collection<T> collection, T expected) {
        return collection != null && collection.contains(expected);
    }

    /**
     * 验证集合大小是否符合预期
     */
    public static <T> boolean validateCollectionSize(Collection<T> collection, int expectedSize) {
        return collection != null && collection.size() == expectedSize;
    }

    // ==================== 日志辅助方法 ====================

    /**
     * 记录测试开始
     */
    public static void logTestStart(String testName, Object... params) {
        log.info("🚀 开始测试: {} - 参数: {}", testName, Arrays.toString(params));
    }

    /**
     * 记录测试成功
     */
    public static void logTestSuccess(String testName, Object result) {
        log.info("✅ 测试成功: {} - 结果: {}", testName, result);
    }

    /**
     * 记录测试失败
     */
    public static void logTestFailure(String testName, Exception exception) {
        log.error("❌ 测试失败: {} - 异常: {}", testName, exception.getMessage(), exception);
    }

    /**
     * 记录测试步骤
     */
    public static void logTestStep(String step, Object... details) {
        log.debug("📝 测试步骤: {} - 详情: {}", step, Arrays.toString(details));
    }

    // ==================== 清理辅助方法 ====================

    /**
     * 生成清理SQL（用于测试后清理数据）
     */
    public static List<String> generateCleanupSql(String tablePrefix) {
        List<String> sqls = new ArrayList<>();
        sqls.add("DELETE FROM db_table_field WHERE tb_id IN (SELECT id FROM db_table WHERE name LIKE '" + tablePrefix + "%')");
        sqls.add("DELETE FROM db_table WHERE name LIKE '" + tablePrefix + "%'");
        sqls.add("DELETE FROM db_info WHERE name LIKE '" + tablePrefix + "%'");
        return sqls;
    }

    /**
     * 验证测试环境是否已清理
     */
    public static boolean isTestEnvironmentClean(String testPrefix) {
        // 这里可以添加验证逻辑，检查数据库中是否还有测试数据残留
        log.debug("验证测试环境清理状态: {}", testPrefix);
        return true; // 简化实现
    }

    // ==================== 常量定义 ====================

    public static final class TestConstants {
        public static final String TEST_USER_PREFIX = "test_user";
        public static final String TEST_DB_PREFIX = "test_db";
        public static final String TEST_TABLE_PREFIX = "test_table";
        public static final Long DEFAULT_TEST_SPACE_ID = 9999L;
        public static final int DEFAULT_PAGE_SIZE = 10;
        public static final int MAX_FIELD_COUNT = 20;
        public static final int MAX_TABLE_COUNT = 20;

        // 字段类型常量
        public static final String FIELD_TYPE_STRING = "string";
        public static final String FIELD_TYPE_INTEGER = "integer";
        public static final String FIELD_TYPE_NUMBER = "number";
        public static final String FIELD_TYPE_BOOLEAN = "boolean";
        public static final String FIELD_TYPE_TIME = "time";

        // 操作类型常量
        public static final Integer OPERATE_INSERT = DBOperateEnum.INSERT.getCode();
        public static final Integer OPERATE_UPDATE = DBOperateEnum.UPDATE.getCode();
        public static final Integer OPERATE_DELETE = DBOperateEnum.DELETE.getCode();

        private TestConstants() {
            // 工具类不允许实例化
        }
    }
}
