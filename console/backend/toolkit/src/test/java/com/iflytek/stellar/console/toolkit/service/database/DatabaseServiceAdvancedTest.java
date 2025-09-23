package com.iflytek.astra.console.toolkit.service.database;

import com.alibaba.fastjson2.JSONObject;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.iflytek.astra.console.toolkit.config.exception.CustomException;
import com.iflytek.astra.console.toolkit.entity.dto.database.*;
import com.iflytek.astra.console.toolkit.entity.enumVo.DBOperateEnum;
import com.iflytek.astra.console.toolkit.entity.table.database.DbInfo;
import com.iflytek.astra.console.toolkit.entity.table.database.DbTable;
import com.iflytek.astra.console.toolkit.entity.table.database.DbTableField;
import com.iflytek.astra.console.toolkit.mapper.database.DbTableFieldMapper;
import com.iflytek.astra.console.toolkit.mapper.database.DbTableMapper;
import com.iflytek.astra.console.toolkit.service.extra.CoreSystemService;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.*;
import org.mockito.MockedStatic;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.annotation.Rollback;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * DatabaseService 高级集成测试 包含复杂业务场景、边界条件、异常处理、并发测试等
 *
 * @author test
 */
@SpringBootTest(classes = DatabaseServiceTestConfiguration.class)
@ActiveProfiles("test")
@Slf4j
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class DatabaseServiceAdvancedTest {

    @Autowired
    private DatabaseService databaseService;

    @Autowired
    private DbTableMapper dbTableMapper;

    @Autowired
    private DbTableFieldMapper dbTableFieldMapper;

    @MockitoBean
    private CoreSystemService coreSystemService;

    private static final String TEST_USER_ID = "advanced-test-user";
    private static final Long TEST_SPACE_ID = 2000L;
    private static final Long MOCK_DB_ID = 9998L;

    private MockedStatic<com.iflytek.astra.console.commons.util.space.SpaceInfoUtil> spaceInfoUtilMock;
    private MockedStatic<com.iflytek.astra.console.toolkit.handler.UserInfoManagerHandler> userInfoMock;

    @BeforeEach
    void setUp() {
        // Mock静态工具类
        spaceInfoUtilMock = mockStatic(com.iflytek.astra.console.commons.util.space.SpaceInfoUtil.class);
        userInfoMock = mockStatic(com.iflytek.astra.console.toolkit.handler.UserInfoManagerHandler.class);

        spaceInfoUtilMock.when(() -> com.iflytek.astra.console.commons.util.space.SpaceInfoUtil.getSpaceId())
                .thenReturn(TEST_SPACE_ID);
        userInfoMock.when(() -> com.iflytek.astra.console.toolkit.handler.UserInfoManagerHandler.getUserId())
                .thenReturn(TEST_USER_ID);
        userInfoMock.when(() -> com.iflytek.astra.console.toolkit.handler.UserInfoManagerHandler.getUserId())
                .thenReturn(TEST_USER_ID);

        // Mock核心系统服务
        when(coreSystemService.createDatabase(anyString(), anyString(), anyLong(), anyString()))
                .thenReturn(MOCK_DB_ID);
        when(coreSystemService.cloneDataBase(anyLong(), anyString(), anyString()))
                .thenReturn(MOCK_DB_ID + 1);
        doNothing().when(coreSystemService).modifyDataBase(anyLong(), anyString(), anyString());
        doNothing().when(coreSystemService).dropDataBase(anyLong(), anyString());
        doNothing().when(coreSystemService).execDDL(anyString(), anyString(), anyLong(), anyLong());
        when(coreSystemService.execDML(anyString(), anyString(), anyLong(), anyLong(), anyInt(), anyInt()))
                .thenReturn(new ArrayList<>());
    }

    @AfterEach
    void tearDown() {
        if (spaceInfoUtilMock != null) {
            spaceInfoUtilMock.close();
        }
        if (userInfoMock != null) {
            userInfoMock.close();
        }
    }

    // ==================== 复杂业务场景测试 ====================

    @Test
    @Order(1)
    @Transactional
    @Rollback
    @DisplayName("TC050: 复杂表结构更新测试")
    void testComplexTableUpdate() {
        // Arrange - 创建数据库和初始表
        DatabaseDto dbDto = new DatabaseDto();
        dbDto.setName("复杂更新测试DB_" + System.currentTimeMillis());
        DbInfo database = databaseService.create(dbDto);

        // 创建初始表
        DbTableDto initialTable = new DbTableDto();
        initialTable.setDbId(database.getId());
        initialTable.setName("复杂测试表");
        initialTable.setDescription("初始描述");

        List<DbTableFieldDto> initialFields = new ArrayList<>();
        initialFields.add(createField("old_field1", "string", "旧字段1", true));
        initialFields.add(createField("old_field2", "integer", "旧字段2", false));
        initialTable.setFields(initialFields);

        databaseService.createDbTable(initialTable);

        // 获取创建的表
        DbTable createdTable = dbTableMapper.selectList(null)
                .stream()
                .filter(t -> t.getName().equals("复杂测试表"))
                .findFirst()
                .orElseThrow();

        // 准备复杂更新：更新表名、描述，同时增删改字段
        DbTableDto updateDto = new DbTableDto();
        updateDto.setId(createdTable.getId());
        updateDto.setName("更新后的表名");
        updateDto.setDescription("更新后的描述");

        List<DbTableFieldDto> updateFields = new ArrayList<>();

        // 获取现有字段ID用于更新和删除操作
        List<DbTableField> existingFields = dbTableFieldMapper.selectList(null);
        Map<String, Long> fieldIdMap = new HashMap<>();
        for (DbTableField field : existingFields) {
            if (field.getTbId().equals(createdTable.getId())) {
                fieldIdMap.put(field.getName(), field.getId());
            }
        }

        // 1. 新增字段
        DbTableFieldDto newField = createField("new_field", "string", "新增字段", true);
        newField.setOperateType(DBOperateEnum.INSERT.getCode());
        updateFields.add(newField);

        // 2. 更新现有字段
        DbTableFieldDto updateField = createField("old_field1", "string", "更新后的字段1", false);
        updateField.setId(fieldIdMap.get("old_field1"));
        updateField.setOperateType(DBOperateEnum.UPDATE.getCode());
        updateFields.add(updateField);

        // 3. 删除字段
        DbTableFieldDto deleteField = new DbTableFieldDto();
        deleteField.setId(fieldIdMap.get("old_field2"));
        deleteField.setName("old_field2");
        deleteField.setOperateType(DBOperateEnum.DELETE.getCode());
        updateFields.add(deleteField);

        updateDto.setFields(updateFields);

        // Act
        assertDoesNotThrow(() -> {
            databaseService.updateTable(updateDto);
        });

        // Assert
        // 验证表信息更新
        DbTable updatedTable = dbTableMapper.selectById(createdTable.getId());
        assertEquals("更新后的表名", updatedTable.getName());
        assertEquals("更新后的描述", updatedTable.getDescription());

        // 验证DDL执行（表重命名、字段操作等）
        verify(coreSystemService, atLeastOnce())
                .execDDL(anyString(), eq(TEST_USER_ID), eq(TEST_SPACE_ID), eq(MOCK_DB_ID));

        log.info("✅ 复杂表结构更新测试通过");
    }

    @Test
    @Order(2)
    @Transactional
    @Rollback
    @DisplayName("TC071: 批量数据操作测试")
    void testBatchDataOperations() {
        // Arrange - 创建数据库和表
        DatabaseDto dbDto = new DatabaseDto();
        dbDto.setName("批量操作测试DB_" + System.currentTimeMillis());
        DbInfo database = databaseService.create(dbDto);

        DbTableDto tableDto = new DbTableDto();
        tableDto.setDbId(database.getId());
        tableDto.setName("批量操作表");
        tableDto.setFields(Arrays.asList(
                createField("name", "string", "姓名", true),
                createField("age", "integer", "年龄", false)));
        databaseService.createDbTable(tableDto);

        DbTable table = dbTableMapper.selectList(null)
                .stream()
                .filter(t -> t.getName().equals("批量操作表"))
                .findFirst()
                .orElseThrow();

        // 准备批量操作数据
        DbTableOperateDto operateDto = new DbTableOperateDto();
        operateDto.setTbId(table.getId());
        operateDto.setExecDev(1);

        List<DbTableDataDto> batchData = new ArrayList<>();

        // 批量插入10条数据
        for (int i = 1; i <= 10; i++) {
            DbTableDataDto dataDto = new DbTableDataDto();
            dataDto.setOperateType(DBOperateEnum.INSERT.getCode());

            Map<String, Object> tableData = new HashMap<>();
            tableData.put("name", "用户" + i);
            tableData.put("age", 20 + i);
            dataDto.setTableData(tableData);

            batchData.add(dataDto);
        }

        operateDto.setData(batchData);

        // Act
        assertDoesNotThrow(() -> {
            databaseService.operateTableData(operateDto);
        });

        // Assert - 验证批量DML执行
        verify(coreSystemService, times(10))
                .execDML(contains("INSERT INTO"), eq(TEST_USER_ID), eq(TEST_SPACE_ID),
                        eq(MOCK_DB_ID), eq(DBOperateEnum.UPDATE.getCode()), eq(1));

        log.info("✅ 批量数据操作测试通过，处理了 {} 条数据", batchData.size());
    }

    @Test
    @Order(3)
    @Transactional
    @Rollback
    @DisplayName("TC084: 导入数据部分失败处理测试")
    void testImportDataPartialFailure() {
        // Arrange - 创建数据库和表
        DatabaseDto dbDto = new DatabaseDto();
        dbDto.setName("导入失败测试DB_" + System.currentTimeMillis());
        DbInfo database = databaseService.create(dbDto);

        DbTableDto tableDto = new DbTableDto();
        tableDto.setDbId(database.getId());
        tableDto.setName("导入测试表");
        tableDto.setFields(Arrays.asList(createField("name", "string", "姓名", true)));
        databaseService.createDbTable(tableDto);

        DbTable table = dbTableMapper.selectList(null)
                .stream()
                .filter(t -> t.getName().equals("导入测试表"))
                .findFirst()
                .orElseThrow();

        // 模拟部分导入失败的Excel文件
        String csvContent = "name\n用户1\n\n用户3"; // 第二行为空，可能导致失败
        MockMultipartFile file = new MockMultipartFile(
                "file", "import_test.csv", "text/csv", csvContent.getBytes());

        // Act & Assert - 预期可能抛出异常或处理部分失败
        try {
            databaseService.importTableData(table.getId(), 1, file);
            log.info("✅ 导入数据测试执行完成");
        } catch (Exception e) {
            // 预期可能因为数据问题或Excel格式问题失败
            log.info("✅ 导入数据测试捕获到预期异常: {}", e.getMessage());
            assertTrue(e instanceof CustomException || e.getCause() != null);
        }
    }

    // ==================== 边界条件测试 ====================

    @Test
    @Order(10)
    @Transactional
    @Rollback
    @DisplayName("TC040: 字段数量边界测试")
    void testFieldCountBoundary() {
        // Arrange - 创建数据库
        DatabaseDto dbDto = new DatabaseDto();
        dbDto.setName("字段边界测试DB_" + System.currentTimeMillis());
        DbInfo database = databaseService.create(dbDto);

        // 测试创建20个字段的表（边界值）
        DbTableDto tableDto = new DbTableDto();
        tableDto.setDbId(database.getId());
        tableDto.setName("边界测试表");

        List<DbTableFieldDto> fields = new ArrayList<>();
        for (int i = 1; i <= 20; i++) {
            fields.add(createField("field" + i, "string", "字段" + i, false));
        }
        tableDto.setFields(fields);

        // Act & Assert - 20个字段应该成功
        assertDoesNotThrow(() -> {
            databaseService.createDbTable(tableDto);
        });

        // 测试创建21个字段的表（超过限制）
        DbTableDto overLimitTable = new DbTableDto();
        overLimitTable.setDbId(database.getId());
        overLimitTable.setName("超限测试表");

        List<DbTableFieldDto> overLimitFields = new ArrayList<>();
        for (int i = 1; i <= 21; i++) {
            overLimitFields.add(createField("field" + i, "string", "字段" + i, false));
        }
        overLimitTable.setFields(overLimitFields);

        // Act & Assert - 21个字段应该失败
        CustomException exception = assertThrows(CustomException.class, () -> {
            databaseService.createDbTable(overLimitTable);
        });

        assertNotNull(exception);
        log.info("✅ 字段数量边界测试通过，异常信息: {}", exception.getMessage());
    }

    @Test
    @Order(11)
    @Transactional
    @Rollback
    @DisplayName("TC082: 分页参数边界测试")
    void testPaginationBoundary() {
        // Arrange - 创建数据库和表
        DatabaseDto dbDto = new DatabaseDto();
        dbDto.setName("分页边界测试DB_" + System.currentTimeMillis());
        DbInfo database = databaseService.create(dbDto);

        DbTableDto tableDto = new DbTableDto();
        tableDto.setDbId(database.getId());
        tableDto.setName("分页测试表");
        tableDto.setFields(Arrays.asList(createField("name", "string", "姓名", true)));
        databaseService.createDbTable(tableDto);

        DbTable table = dbTableMapper.selectList(null)
                .stream()
                .filter(t -> t.getName().equals("分页测试表"))
                .findFirst()
                .orElseThrow();

        // 测试边界分页参数
        DbTableSelectDataDto selectDto = new DbTableSelectDataDto();
        selectDto.setTbId(table.getId());
        selectDto.setPageNum(1L);
        selectDto.setPageSize(1000L); // 最大页面大小
        selectDto.setExecDev(1);

        // Mock数据返回
        when(coreSystemService.execDML(contains("SELECT * FROM"), anyString(), anyLong(),
                anyLong(), eq(DBOperateEnum.SELECT.getCode()), anyInt()))
                .thenReturn(new ArrayList<>());
        when(coreSystemService.execDML(contains("SELECT COUNT(*)"), anyString(), anyLong(),
                anyLong(), eq(DBOperateEnum.SELECT_TOTAL_COUNT.getCode()), anyInt()))
                .thenReturn(0L);

        // Act & Assert - 最大页面大小应该被限制
        assertDoesNotThrow(() -> {
            Page<JSONObject> result = databaseService.selectTableData(selectDto);
            assertTrue(result.getSize() <= 1000); // 应该被限制在MAX_PAGE_SIZE内
        });

        // 测试超大页面大小
        selectDto.setPageSize(2000L);
        assertDoesNotThrow(() -> {
            Page<JSONObject> result = databaseService.selectTableData(selectDto);
            assertTrue(result.getSize() <= 1000); // 应该被限制
        });

        log.info("✅ 分页参数边界测试通过");
    }

    // ==================== 异常处理测试 ====================

    @Test
    @Order(20)
    @Transactional
    @Rollback
    @DisplayName("TC073: 非法字段校验测试")
    void testIllegalFieldValidation() {
        // Arrange - 创建数据库和表
        DatabaseDto dbDto = new DatabaseDto();
        dbDto.setName("非法字段测试DB_" + System.currentTimeMillis());
        DbInfo database = databaseService.create(dbDto);

        DbTableDto tableDto = new DbTableDto();
        tableDto.setDbId(database.getId());
        tableDto.setName("字段校验表");
        tableDto.setFields(Arrays.asList(createField("valid_field", "string", "有效字段", true)));
        databaseService.createDbTable(tableDto);

        DbTable table = dbTableMapper.selectList(null)
                .stream()
                .filter(t -> t.getName().equals("字段校验表"))
                .findFirst()
                .orElseThrow();

        // 准备包含非法字段的数据操作
        DbTableOperateDto operateDto = new DbTableOperateDto();
        operateDto.setTbId(table.getId());
        operateDto.setExecDev(1);

        List<DbTableDataDto> dataList = new ArrayList<>();
        DbTableDataDto dataDto = new DbTableDataDto();
        dataDto.setOperateType(DBOperateEnum.INSERT.getCode());

        Map<String, Object> tableData = new HashMap<>();
        tableData.put("valid_field", "有效值");
        tableData.put("invalid_field", "非法字段值"); // 不存在的字段
        dataDto.setTableData(tableData);
        dataList.add(dataDto);

        operateDto.setData(dataList);

        // Act & Assert
        CustomException exception = assertThrows(CustomException.class, () -> {
            databaseService.operateTableData(operateDto);
        });

        assertNotNull(exception);
        log.info("✅ 非法字段校验测试通过，异常信息: {}", exception.getMessage());
    }

    @Test
    @Order(21)
    @Transactional
    @Rollback
    @DisplayName("TC074: 必填字段缺失校验测试")
    void testRequiredFieldValidation() {
        // Arrange - 创建数据库和表
        DatabaseDto dbDto = new DatabaseDto();
        dbDto.setName("必填字段测试DB_" + System.currentTimeMillis());
        DbInfo database = databaseService.create(dbDto);

        DbTableDto tableDto = new DbTableDto();
        tableDto.setDbId(database.getId());
        tableDto.setName("必填字段表");
        tableDto.setFields(Arrays.asList(
                createField("required_field", "string", "必填字段", true), // 必填
                createField("optional_field", "string", "可选字段", false) // 可选
        ));
        databaseService.createDbTable(tableDto);

        DbTable table = dbTableMapper.selectList(null)
                .stream()
                .filter(t -> t.getName().equals("必填字段表"))
                .findFirst()
                .orElseThrow();

        // 准备缺少必填字段的数据操作
        DbTableOperateDto operateDto = new DbTableOperateDto();
        operateDto.setTbId(table.getId());
        operateDto.setExecDev(1);

        List<DbTableDataDto> dataList = new ArrayList<>();
        DbTableDataDto dataDto = new DbTableDataDto();
        dataDto.setOperateType(DBOperateEnum.INSERT.getCode());

        Map<String, Object> tableData = new HashMap<>();
        tableData.put("optional_field", "可选字段值");
        // 缺少 required_field
        dataDto.setTableData(tableData);
        dataList.add(dataDto);

        operateDto.setData(dataList);

        // Act & Assert
        CustomException exception = assertThrows(CustomException.class, () -> {
            databaseService.operateTableData(operateDto);
        });

        assertNotNull(exception);
        log.info("✅ 必填字段校验测试通过，异常信息: {}", exception.getMessage());
    }

    // ==================== 并发测试 ====================

    @Test
    @Order(30)
    @DisplayName("TC_CONCURRENT: 并发创建数据库测试")
    void testConcurrentDatabaseCreation() throws InterruptedException {
        final int threadCount = 5;
        final CountDownLatch latch = new CountDownLatch(threadCount);
        final ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        final List<Exception> exceptions = Collections.synchronizedList(new ArrayList<>());
        final List<String> createdDatabases = Collections.synchronizedList(new ArrayList<>());

        // 启动多个线程同时创建数据库
        for (int i = 0; i < threadCount; i++) {
            final int threadId = i;
            executor.submit(() -> {
                try {
                    DatabaseDto dbDto = new DatabaseDto();
                    dbDto.setName("并发测试DB_" + threadId + "_" + System.currentTimeMillis());
                    dbDto.setDescription("并发测试数据库_" + threadId);

                    DbInfo result = databaseService.create(dbDto);
                    createdDatabases.add(result.getName());
                    log.info("线程 {} 成功创建数据库: {}", threadId, result.getName());
                } catch (Exception e) {
                    exceptions.add(e);
                    log.error("线程 {} 创建数据库失败: {}", threadId, e.getMessage());
                } finally {
                    latch.countDown();
                }
            });
        }

        // 等待所有线程完成
        assertTrue(latch.await(30, TimeUnit.SECONDS));
        executor.shutdown();

        // Assert - 验证结果
        assertTrue(exceptions.isEmpty(), "并发创建过程中出现异常: " + exceptions);
        assertEquals(threadCount, createdDatabases.size());

        // 验证所有数据库名称都不重复
        Set<String> uniqueNames = new HashSet<>(createdDatabases);
        assertEquals(threadCount, uniqueNames.size());

        log.info("✅ 并发创建数据库测试通过，成功创建 {} 个数据库", createdDatabases.size());
    }

    // ==================== 辅助方法 ====================

    private DbTableFieldDto createField(String name, String type, String description, boolean required) {
        DbTableFieldDto field = new DbTableFieldDto();
        field.setName(name);
        field.setType(type);
        field.setDescription(description);
        field.setIsRequired(required);
        field.setDefaultValue(getDefaultValueByType(type));
        return field;
    }

    private String getDefaultValueByType(String type) {
        switch (type.toLowerCase()) {
            case "string":
                return "";
            case "integer":
                return "0";
            case "number":
                return "0.0";
            case "boolean":
                return "false";
            default:
                return "";
        }
    }

}
