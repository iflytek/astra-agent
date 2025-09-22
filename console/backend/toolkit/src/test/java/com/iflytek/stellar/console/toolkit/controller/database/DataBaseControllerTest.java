package com.iflytek.astra.console.toolkit.controller.database;

import com.alibaba.fastjson2.JSONObject;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.iflytek.astra.console.commons.response.ApiResult;
import com.iflytek.astra.console.commons.util.SpringContextHolder;
import com.iflytek.astra.console.toolkit.common.CustomExceptionCode;
import com.iflytek.astra.console.toolkit.config.exception.CustomException;
import com.iflytek.astra.console.toolkit.entity.dto.database.*;
import com.iflytek.astra.console.toolkit.entity.table.database.DbInfo;
import com.iflytek.astra.console.toolkit.entity.table.database.DbTableField;
import com.iflytek.astra.console.toolkit.entity.vo.database.*;
import com.iflytek.astra.console.toolkit.service.database.DatabaseService;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationContext;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import org.springframework.mock.web.MockHttpServletRequest;

import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("数据库控制器单元测试")
class DataBaseControllerTest {

    @Mock
    private DatabaseService databaseService;

    @InjectMocks
    private DataBaseController dataBaseController;

    @BeforeEach
    void setUp() {
        // 初始化Spring上下文
        ApplicationContext ctx = mock(ApplicationContext.class, RETURNS_DEFAULTS);
        new SpringContextHolder().setApplicationContext(ctx);

        // 设置请求上下文
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("space-id", "1001");
        request.addHeader("user-id", "testUser123");
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
    }

    // ========== 数据库管理接口测试 ==========

    @Test
    @DisplayName("TC001: 创建数据库成功")
    void testCreateDatabase_Success() {
        // Arrange
        DatabaseDto databaseDto = new DatabaseDto();
        databaseDto.setName("测试数据库");
        databaseDto.setDescription("测试数据库描述");

        DbInfo mockDbInfo = createMockDbInfo(1L, "测试数据库", "测试数据库描述");
        when(databaseService.create(any(DatabaseDto.class))).thenReturn(mockDbInfo);

        // Act
        ApiResult<Void> result = dataBaseController.createDatabase(databaseDto);

        // Assert
        assertNotNull(result);
        assertEquals(0, result.code());
        verify(databaseService, times(1)).create(databaseDto);
    }

    @Test
    @DisplayName("TC004: 数据库名称为空 - 返回参数校验错误")
    void testCreateDatabase_EmptyName() {
        // Arrange
        DatabaseDto databaseDto = new DatabaseDto();
        databaseDto.setName("");
        databaseDto.setDescription("测试数据库描述");

        when(databaseService.create(any(DatabaseDto.class)))
                        .thenThrow(new CustomException(CustomExceptionCode.DATABASE_NAME_NOT_EMPTY));

        // Act & Assert
        assertThrows(CustomException.class, () -> {
            dataBaseController.createDatabase(databaseDto);
        });
        verify(databaseService, times(1)).create(databaseDto);
    }

    @Test
    @DisplayName("TC005: 数据库名称重复 - 返回名称已存在错误")
    void testCreateDatabase_DuplicateName() {
        // Arrange
        DatabaseDto databaseDto = new DatabaseDto();
        databaseDto.setName("重复数据库");
        databaseDto.setDescription("测试数据库描述");

        when(databaseService.create(any(DatabaseDto.class)))
                        .thenThrow(new CustomException(CustomExceptionCode.DATABASE_NAME_EXIST));

        // Act & Assert
        assertThrows(CustomException.class, () -> {
            dataBaseController.createDatabase(databaseDto);
        });
    }

    @Test
    @DisplayName("TC008: 查询存在的数据库详情成功")
    void testGetDatabaseInfo_Success() {
        // Arrange
        Long dbId = 1L;
        DbInfo mockDbInfo = createMockDbInfo(dbId, "测试数据库", "测试数据库描述");
        when(databaseService.getDatabaseInfo(dbId)).thenReturn(mockDbInfo);

        // Act
        ApiResult<DbInfo> result = dataBaseController.getDatabaseInfo(dbId);

        // Assert
        assertNotNull(result);
        assertEquals(0, result.code());
        assertEquals(mockDbInfo, result.data());
        verify(databaseService, times(1)).getDatabaseInfo(dbId);
    }

    @Test
    @DisplayName("TC011: 查询不存在的数据库 - 返回数据不存在错误")
    void testGetDatabaseInfo_NotFound() {
        // Arrange
        Long dbId = 999L;
        when(databaseService.getDatabaseInfo(dbId))
                        .thenThrow(new CustomException(CustomExceptionCode.DATABASE_NOT_EXIST));

        // Act & Assert
        assertThrows(CustomException.class, () -> {
            dataBaseController.getDatabaseInfo(dbId);
        });
    }

    @Test
    @DisplayName("TC014: 更新数据库基本信息成功")
    void testUpdateDatabase_Success() {
        // Arrange
        DatabaseDto databaseDto = new DatabaseDto();
        databaseDto.setId(1L);
        databaseDto.setName("更新后的数据库");
        databaseDto.setDescription("更新后的描述");

        doNothing().when(databaseService).updateDateBase(any(DatabaseDto.class));

        // Act
        ApiResult<Void> result = dataBaseController.updateDatabase(databaseDto);

        // Assert
        assertNotNull(result);
        assertEquals(0, result.code());
        verify(databaseService, times(1)).updateDateBase(databaseDto);
    }

    @Test
    @DisplayName("TC019: 删除数据库成功")
    void testDeleteDatabase_Success() {
        // Arrange
        Long dbId = 1L;
        doNothing().when(databaseService).delete(dbId);

        // Act
        ApiResult<Void> result = dataBaseController.deleteDatabase(dbId);

        // Assert
        assertNotNull(result);
        assertEquals(0, result.code());
        verify(databaseService, times(1)).delete(dbId);
    }

    @Test
    @DisplayName("TC022: 删除不存在的数据库")
    void testDeleteDatabase_NotFound() {
        // Arrange
        Long dbId = 999L;
        doThrow(new CustomException(CustomExceptionCode.DATABASE_NOT_EXIST))
                        .when(databaseService)
                        .delete(dbId);

        // Act & Assert
        assertThrows(CustomException.class, () -> {
            dataBaseController.deleteDatabase(dbId);
        });
    }

    @Test
    @DisplayName("TC025: 复制数据库成功")
    void testCopyDatabase_Success() {
        // Arrange
        Long dbId = 1L;
        doNothing().when(databaseService).copyDatabase(dbId);

        // Act
        ApiResult<Void> result = dataBaseController.copyDatabase(dbId);

        // Assert
        assertNotNull(result);
        assertEquals(0, result.code());
        verify(databaseService, times(1)).copyDatabase(dbId);
    }

    @Test
    @DisplayName("TC029: 分页查询数据库列表成功")
    void testSelectDatabase_Success() {
        // Arrange
        DataBaseSearchVo searchVo = new DataBaseSearchVo();
        searchVo.setPageNum(1L);
        searchVo.setPageSize(10L);
        searchVo.setSearch("测试");

        Page<DbInfo> mockPage = new Page<>(1, 10);
        List<DbInfo> dbInfoList = new ArrayList<>();
        dbInfoList.add(createMockDbInfo(1L, "测试数据库1", "描述1"));
        dbInfoList.add(createMockDbInfo(2L, "测试数据库2", "描述2"));
        mockPage.setRecords(dbInfoList);
        mockPage.setTotal(2);

        when(databaseService.selectPage(any(DataBaseSearchVo.class))).thenReturn(mockPage);

        // Act
        ApiResult<Page<DbInfo>> result = dataBaseController.selectDatabase(searchVo);

        // Assert
        assertNotNull(result);
        assertEquals(0, result.code());
        assertEquals(mockPage, result.data());
        assertEquals(2, result.data().getRecords().size());
        verify(databaseService, times(1)).selectPage(searchVo);
    }

    @Test
    @DisplayName("TC030: 按名称搜索数据库")
    void testSelectDatabase_SearchByName() {
        // Arrange
        DataBaseSearchVo searchVo = new DataBaseSearchVo();
        searchVo.setPageNum(1L);
        searchVo.setPageSize(10L);
        searchVo.setSearch("AI数据库");

        Page<DbInfo> mockPage = new Page<>(1, 10);
        List<DbInfo> dbInfoList = new ArrayList<>();
        dbInfoList.add(createMockDbInfo(1L, "AI数据库", "AI相关数据"));
        mockPage.setRecords(dbInfoList);
        mockPage.setTotal(1);

        when(databaseService.selectPage(any(DataBaseSearchVo.class))).thenReturn(mockPage);

        // Act
        ApiResult<Page<DbInfo>> result = dataBaseController.selectDatabase(searchVo);

        // Assert
        assertNotNull(result);
        assertEquals(0, result.code());
        assertEquals(1, result.data().getRecords().size());
        assertEquals("AI数据库", result.data().getRecords().get(0).getName());
    }

    // ========== 数据表管理接口测试 ==========

    @Test
    @DisplayName("TC038: 创建表成功 - 包含基本字段")
    void testCreateDbTable_Success() {
        // Arrange
        DbTableDto dbTableDto = new DbTableDto();
        dbTableDto.setDbId(1L);
        dbTableDto.setName("用户表");
        dbTableDto.setDescription("用户信息表");

        List<DbTableFieldDto> fields = new ArrayList<>();
        DbTableFieldDto field = new DbTableFieldDto();
        field.setName("name");
        field.setType("VARCHAR");
        field.setDescription("用户名");
        fields.add(field);
        dbTableDto.setFields(fields);

        doNothing().when(databaseService).createDbTable(any(DbTableDto.class));

        // Act
        ApiResult<Void> result = dataBaseController.createDbTable(dbTableDto);

        // Assert
        assertNotNull(result);
        assertEquals(0, result.code());
        verify(databaseService, times(1)).createDbTable(dbTableDto);
    }

    @Test
    @DisplayName("TC041: 表名为空或无效")
    void testCreateDbTable_EmptyName() {
        // Arrange
        DbTableDto dbTableDto = new DbTableDto();
        dbTableDto.setDbId(1L);
        dbTableDto.setName("");
        dbTableDto.setDescription("测试表");

        doThrow(new CustomException(CustomExceptionCode.DATABASE_TABLE_FIELD_CANNOT_EMPTY))
                        .when(databaseService)
                        .createDbTable(any(DbTableDto.class));

        // Act & Assert
        assertThrows(CustomException.class, () -> {
            dataBaseController.createDbTable(dbTableDto);
        });
    }

    @Test
    @DisplayName("TC045: 获取指定数据库的表列表成功")
    void testGetDbTableList_Success() {
        // Arrange
        Long dbId = 1L;
        List<DbTableVo> mockTableList = new ArrayList<>();
        DbTableVo table1 = new DbTableVo();
        table1.setId(1L);
        table1.setName("用户表");
        table1.setDescription("用户信息表");
        mockTableList.add(table1);

        when(databaseService.getDbTableList(dbId)).thenReturn(mockTableList);

        // Act
        ApiResult<List<DbTableVo>> result = dataBaseController.getDbTableList(dbId);

        // Assert
        assertNotNull(result);
        assertEquals(0, result.code());
        assertEquals(mockTableList, result.data());
        assertEquals(1, result.data().size());
        verify(databaseService, times(1)).getDbTableList(dbId);
    }

    @Test
    @DisplayName("TC049: 获取用户所有数据库表信息成功")
    void testGetDbTableInfoList_Success() {
        // Arrange
        List<DbTableInfoVo> mockTableInfoList = new ArrayList<>();
        DbTableInfoVo tableInfo = new DbTableInfoVo();
        tableInfo.setLabel("测试数据库");
        tableInfo.setValue("1");

        List<DbTableInfoVo> children = new ArrayList<>();
        DbTableInfoVo childInfo = new DbTableInfoVo();
        childInfo.setLabel("用户表");
        childInfo.setValue("1");
        children.add(childInfo);
        tableInfo.setChildren(children);

        mockTableInfoList.add(tableInfo);

        when(databaseService.getDbTableInfoList()).thenReturn(mockTableInfoList);

        // Act
        ApiResult<List<DbTableInfoVo>> result = dataBaseController.getDbTableInfoList();

        // Assert
        assertNotNull(result);
        assertEquals(0, result.code());
        assertEquals(mockTableInfoList, result.data());
        verify(databaseService, times(1)).getDbTableInfoList();
    }

    @Test
    @DisplayName("TC051: 更新表字段成功")
    void testUpdateTable_Success() {
        // Arrange
        DbTableDto dbTableDto = new DbTableDto();
        dbTableDto.setId(1L);
        dbTableDto.setName("更新后的表");
        dbTableDto.setDescription("更新后的描述");

        doNothing().when(databaseService).updateTable(any(DbTableDto.class));

        // Act
        ApiResult<Void> result = dataBaseController.updateTable(dbTableDto);

        // Assert
        assertNotNull(result);
        assertEquals(0, result.code());
        verify(databaseService, times(1)).updateTable(dbTableDto);
    }

    @Test
    @DisplayName("TC057: 删除表成功")
    void testDeleteTable_Success() {
        // Arrange
        Long tableId = 1L;
        doNothing().when(databaseService).deleteTable(tableId);

        // Act
        ApiResult<Void> result = dataBaseController.deleteTable(tableId);

        // Assert
        assertNotNull(result);
        assertEquals(0, result.code());
        verify(databaseService, times(1)).deleteTable(tableId);
    }

    @Test
    @DisplayName("TC061: 复制表结构成功")
    void testCopyTable_Success() {
        // Arrange
        Long tableId = 1L;
        doNothing().when(databaseService).copyTable(tableId);

        // Act
        ApiResult<Void> result = dataBaseController.copyTable(tableId);

        // Assert
        assertNotNull(result);
        assertEquals(0, result.code());
        verify(databaseService, times(1)).copyTable(tableId);
    }

    // ========== 表字段管理接口测试 ==========

    @Test
    @DisplayName("TC065: Excel文件导入字段成功")
    void testImportDbTableField_Success() {
        // Arrange
        MockMultipartFile file = new MockMultipartFile(
                        "file",
                        "fields.xlsx",
                        "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                        "test content".getBytes());

        List<DbTableFieldDto> mockFields = new ArrayList<>();
        DbTableFieldDto field = new DbTableFieldDto();
        field.setName("test_field");
        field.setType("VARCHAR");
        field.setDescription("测试字段");
        mockFields.add(field);

        when(databaseService.importDbTableField(any())).thenReturn(mockFields);

        // Act
        ApiResult<List<DbTableFieldDto>> result = dataBaseController.importDbTableField(file);

        // Assert
        assertNotNull(result);
        assertEquals(0, result.code());
        assertEquals(mockFields, result.data());
        assertEquals(1, result.data().size());
        verify(databaseService, times(1)).importDbTableField(file);
    }

    @Test
    @DisplayName("TC068: 文件格式不支持")
    void testImportDbTableField_UnsupportedFormat() {
        // Arrange
        MockMultipartFile file = new MockMultipartFile(
                        "file",
                        "fields.txt",
                        "text/plain",
                        "test content".getBytes());

        when(databaseService.importDbTableField(any()))
                        .thenThrow(new CustomException(CustomExceptionCode.REPO_FILE_UPLOAD_FAILED));

        // Act & Assert
        assertThrows(CustomException.class, () -> {
            dataBaseController.importDbTableField(file);
        });
    }

    @Test
    @DisplayName("TC072: 分页获取表字段列表成功")
    void testGetDbTableFieldList_Success() {
        // Arrange
        DataBaseSearchVo searchVo = new DataBaseSearchVo();
        searchVo.setPageNum(1L);
        searchVo.setPageSize(10L);

        Page<DbTableField> mockPage = new Page<>(1, 10);
        List<DbTableField> fieldList = new ArrayList<>();
        DbTableField field = new DbTableField();
        field.setId(1L);
        field.setName("test_field");
        field.setType("VARCHAR");
        field.setDescription("测试字段");
        fieldList.add(field);
        mockPage.setRecords(fieldList);
        mockPage.setTotal(1);

        when(databaseService.getDbTableFieldList(any(DataBaseSearchVo.class))).thenReturn(mockPage);

        // Act
        ApiResult<Page<DbTableField>> result = dataBaseController.getDbTableFieldList(searchVo);

        // Assert
        assertNotNull(result);
        assertEquals(0, result.code());
        assertEquals(mockPage, result.data());
        assertEquals(1, result.data().getRecords().size());
    }

    // ========== 表数据操作接口测试 ==========

    @Test
    @DisplayName("TC077: 插入数据成功")
    void testOperateTableData_Insert_Success() {
        // Arrange
        DbTableOperateDto operateDto = new DbTableOperateDto();
        operateDto.setTbId(1L);
        operateDto.setExecDev(1);

        List<DbTableDataDto> dataList = new ArrayList<>();
        DbTableDataDto dataDto = new DbTableDataDto();
        Map<String, Object> tableData = new HashMap<>();
        tableData.put("name", "张三");
        tableData.put("age", 25);
        dataDto.setTableData(tableData);
        dataDto.setOperateType(1); // INSERT
        dataList.add(dataDto);
        operateDto.setData(dataList);

        doNothing().when(databaseService).operateTableData(any(DbTableOperateDto.class));

        // Act
        ApiResult<Void> result = dataBaseController.operateTableData(operateDto);

        // Assert
        assertNotNull(result);
        assertEquals(0, result.code());
        verify(databaseService, times(1)).operateTableData(operateDto);
    }

    @Test
    @DisplayName("TC078: 更新数据成功")
    void testOperateTableData_Update_Success() {
        // Arrange
        DbTableOperateDto operateDto = new DbTableOperateDto();
        operateDto.setTbId(1L);
        operateDto.setExecDev(2);

        List<DbTableDataDto> dataList = new ArrayList<>();
        DbTableDataDto dataDto = new DbTableDataDto();
        Map<String, Object> tableData = new HashMap<>();
        tableData.put("id", 1);
        tableData.put("name", "李四");
        tableData.put("age", 30);
        dataDto.setTableData(tableData);
        dataDto.setOperateType(2); // UPDATE
        dataList.add(dataDto);
        operateDto.setData(dataList);

        doNothing().when(databaseService).operateTableData(any(DbTableOperateDto.class));

        // Act
        ApiResult<Void> result = dataBaseController.operateTableData(operateDto);

        // Assert
        assertNotNull(result);
        assertEquals(0, result.code());
        verify(databaseService, times(1)).operateTableData(operateDto);
    }

    @Test
    @DisplayName("TC085: 分页查询表数据成功")
    void testSelectTableData_Success() {
        // Arrange
        DbTableSelectDataDto selectDto = new DbTableSelectDataDto();
        selectDto.setTbId(1L);
        selectDto.setPageNum(1L);
        selectDto.setPageSize(10L);

        Page<JSONObject> mockPage = new Page<>(1, 10);
        List<JSONObject> dataList = new ArrayList<>();
        JSONObject data = new JSONObject();
        data.put("id", 1);
        data.put("name", "张三");
        data.put("age", 25);
        dataList.add(data);
        mockPage.setRecords(dataList);
        mockPage.setTotal(1);

        when(databaseService.selectTableData(any(DbTableSelectDataDto.class))).thenReturn(mockPage);

        // Act
        ApiResult<Page<JSONObject>> result = dataBaseController.selectTableData(selectDto);

        // Assert
        assertNotNull(result);
        assertEquals(0, result.code());
        assertEquals(mockPage, result.data());
        assertEquals(1, result.data().getRecords().size());
    }

    @Test
    @DisplayName("TC091: Excel文件导入数据成功")
    void testImportTableData_Success() {
        // Arrange
        Long tableId = 1L;
        Integer execDev = 1; // 增量导入
        MockMultipartFile file = new MockMultipartFile(
                        "file",
                        "data.xlsx",
                        "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                        "test data content".getBytes());

        doNothing().when(databaseService).importTableData(tableId, execDev, file);

        // Act
        ApiResult<Void> result = dataBaseController.importTableData(tableId, file, execDev);

        // Assert
        assertNotNull(result);
        assertEquals(0, result.code());
        verify(databaseService, times(1)).importTableData(tableId, execDev, file);
    }

    @Test
    @DisplayName("TC098: 导出全部数据成功")
    void testExportTableData_Success() {
        // Arrange
        DatabaseExportDto exportDto = new DatabaseExportDto();
        exportDto.setTbId(1L);
        exportDto.setExecDev(1);

        MockHttpServletResponse response = new MockHttpServletResponse();
        doNothing().when(databaseService).exportTableData(any(DatabaseExportDto.class), any(HttpServletResponse.class));

        // Act
        dataBaseController.exportTableData(exportDto, response);

        // Assert
        verify(databaseService, times(1)).exportTableData(exportDto, response);
    }

    @Test
    @DisplayName("TC103: 获取表导入模板成功")
    void testGetTableTemplateFile_Success() {
        // Arrange
        Long tableId = 1L;
        MockHttpServletResponse response = new MockHttpServletResponse();
        doNothing().when(databaseService).getTableTemplateFile(any(HttpServletResponse.class), eq(tableId));

        // Act
        dataBaseController.getTableTemplateFile(response, tableId);

        // Assert
        verify(databaseService, times(1)).getTableTemplateFile(response, tableId);
    }

    // ========== 边界和异常测试 ==========

    @Test
    @DisplayName("TC115: 必填参数校验 - 数据库ID为null")
    void testParameterValidation_NullDatabaseId() {
        // Act & Assert
        assertThrows(Exception.class, () -> {
            dataBaseController.getDatabaseInfo(null);
        });
    }

    @Test
    @DisplayName("TC121: 并发创建同名数据库")
    void testConcurrentCreateDatabase_DuplicateName() {
        // Arrange
        DatabaseDto databaseDto1 = new DatabaseDto();
        databaseDto1.setName("并发测试数据库");
        databaseDto1.setDescription("第一个请求");

        DatabaseDto databaseDto2 = new DatabaseDto();
        databaseDto2.setName("并发测试数据库");
        databaseDto2.setDescription("第二个请求");

        // 第一次调用成功
        when(databaseService.create(databaseDto1))
                        .thenReturn(createMockDbInfo(1L, "并发测试数据库", "第一个请求"));

        // 第二次调用失败（名称重复）
        when(databaseService.create(databaseDto2))
                        .thenThrow(new CustomException(CustomExceptionCode.DATABASE_NAME_EXIST));

        // Act
        ApiResult<Void> result1 = dataBaseController.createDatabase(databaseDto1);

        // Assert
        assertEquals(0, result1.code());
        assertThrows(CustomException.class, () -> {
            dataBaseController.createDatabase(databaseDto2);
        });
    }

    // ========== 更多测试用例 ==========

    @Test
    @DisplayName("TC079: 删除数据成功")
    void testOperateTableData_Delete_Success() {
        // Arrange
        DbTableOperateDto operateDto = new DbTableOperateDto();
        operateDto.setTbId(1L);
        operateDto.setExecDev(3);

        List<DbTableDataDto> dataList = new ArrayList<>();
        DbTableDataDto dataDto = new DbTableDataDto();
        Map<String, Object> tableData = new HashMap<>();
        tableData.put("id", 1);
        dataDto.setTableData(tableData);
        dataDto.setOperateType(3); // DELETE
        dataList.add(dataDto);
        operateDto.setData(dataList);

        doNothing().when(databaseService).operateTableData(any(DbTableOperateDto.class));

        // Act
        ApiResult<Void> result = dataBaseController.operateTableData(operateDto);

        // Assert
        assertNotNull(result);
        assertEquals(0, result.code());
        verify(databaseService, times(1)).operateTableData(operateDto);
    }

    @Test
    @DisplayName("TC081: 插入数据类型不匹配")
    void testOperateTableData_TypeMismatch() {
        // Arrange
        DbTableOperateDto operateDto = new DbTableOperateDto();
        operateDto.setTbId(1L);
        operateDto.setExecDev(1);

        List<DbTableDataDto> dataList = new ArrayList<>();
        DbTableDataDto dataDto = new DbTableDataDto();
        Map<String, Object> tableData = new HashMap<>();
        tableData.put("age", "非数字类型");
        dataDto.setTableData(tableData);
        dataDto.setOperateType(1);
        dataList.add(dataDto);
        operateDto.setData(dataList);

        doThrow(new CustomException(CustomExceptionCode.DATABASE_TYPE_ILLEGAL))
                        .when(databaseService)
                        .operateTableData(any(DbTableOperateDto.class));

        // Act & Assert
        assertThrows(CustomException.class, () -> {
            dataBaseController.operateTableData(operateDto);
        });
    }

    @Test
    @DisplayName("TC086: 条件查询数据")
    void testSelectTableData_WithConditions() {
        // Arrange
        DbTableSelectDataDto selectDto = new DbTableSelectDataDto();
        selectDto.setTbId(1L);
        selectDto.setPageNum(1L);
        selectDto.setPageSize(10L);
        selectDto.setExecDev(1);

        Page<JSONObject> mockPage = new Page<>(1, 10);
        List<JSONObject> dataList = new ArrayList<>();
        JSONObject data = new JSONObject();
        data.put("id", 1);
        data.put("name", "张三");
        data.put("age", 25);
        dataList.add(data);
        mockPage.setRecords(dataList);
        mockPage.setTotal(1);

        when(databaseService.selectTableData(any(DbTableSelectDataDto.class))).thenReturn(mockPage);

        // Act
        ApiResult<Page<JSONObject>> result = dataBaseController.selectTableData(selectDto);

        // Assert
        assertNotNull(result);
        assertEquals(0, result.code());
        assertEquals(1, result.data().getRecords().size());
        assertEquals("张三", result.data().getRecords().get(0).getString("name"));
    }

    @Test
    @DisplayName("TC093: 增量导入模式")
    void testImportTableData_IncrementalMode() {
        // Arrange
        Long tableId = 1L;
        Integer execDev = 1; // 增量导入
        MockMultipartFile file = new MockMultipartFile(
                        "file",
                        "incremental_data.xlsx",
                        "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                        "incremental test data content".getBytes());

        doNothing().when(databaseService).importTableData(tableId, execDev, file);

        // Act
        ApiResult<Void> result = dataBaseController.importTableData(tableId, file, execDev);

        // Assert
        assertNotNull(result);
        assertEquals(0, result.code());
        verify(databaseService, times(1)).importTableData(tableId, execDev, file);
    }

    @Test
    @DisplayName("TC094: 覆盖导入模式")
    void testImportTableData_OverwriteMode() {
        // Arrange
        Long tableId = 1L;
        Integer execDev = 2; // 覆盖导入
        MockMultipartFile file = new MockMultipartFile(
                        "file",
                        "overwrite_data.xlsx",
                        "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                        "overwrite test data content".getBytes());

        doNothing().when(databaseService).importTableData(tableId, execDev, file);

        // Act
        ApiResult<Void> result = dataBaseController.importTableData(tableId, file, execDev);

        // Assert
        assertNotNull(result);
        assertEquals(0, result.code());
        verify(databaseService, times(1)).importTableData(tableId, execDev, file);
    }

    @Test
    @DisplayName("TC095: 文件格式不匹配表结构")
    void testImportTableData_FormatMismatch() {
        // Arrange
        Long tableId = 1L;
        Integer execDev = 1;
        MockMultipartFile file = new MockMultipartFile(
                        "file",
                        "wrong_format.xlsx",
                        "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                        "wrong format content".getBytes());

        doThrow(new CustomException(CustomExceptionCode.DATABASE_IMPORT_FAILED))
                        .when(databaseService)
                        .importTableData(tableId, execDev, file);

        // Act & Assert
        assertThrows(CustomException.class, () -> {
            dataBaseController.importTableData(tableId, file, execDev);
        });
    }

    @Test
    @DisplayName("TC099: 按条件导出数据")
    void testExportTableData_WithConditions() {
        // Arrange
        DatabaseExportDto exportDto = new DatabaseExportDto();
        exportDto.setTbId(1L);
        exportDto.setExecDev(1);

        List<String> dataIds = new ArrayList<>();
        dataIds.add("1");
        dataIds.add("2");
        exportDto.setDataIds(dataIds);

        MockHttpServletResponse response = new MockHttpServletResponse();
        doNothing().when(databaseService).exportTableData(any(DatabaseExportDto.class), any(HttpServletResponse.class));

        // Act
        dataBaseController.exportTableData(exportDto, response);

        // Assert
        verify(databaseService, times(1)).exportTableData(exportDto, response);
    }

    @Test
    @DisplayName("TC101: 大数据量导出测试")
    void testExportTableData_LargeDataset() {
        // Arrange
        DatabaseExportDto exportDto = new DatabaseExportDto();
        exportDto.setTbId(1L);
        exportDto.setExecDev(1);

        MockHttpServletResponse response = new MockHttpServletResponse();
        doNothing().when(databaseService).exportTableData(any(DatabaseExportDto.class), any(HttpServletResponse.class));

        // Act
        dataBaseController.exportTableData(exportDto, response);

        // Assert
        verify(databaseService, times(1)).exportTableData(exportDto, response);
        // 可以进一步验证响应头设置等
    }

    @Test
    @DisplayName("TC105: 表ID不存在")
    void testGetTableTemplateFile_TableNotFound() {
        // Arrange
        Long tableId = 999L;
        MockHttpServletResponse response = new MockHttpServletResponse();
        doThrow(new CustomException(CustomExceptionCode.DATABASE_NOT_EXIST))
                        .when(databaseService)
                        .getTableTemplateFile(any(HttpServletResponse.class), eq(tableId));

        // Act & Assert
        assertThrows(CustomException.class, () -> {
            dataBaseController.getTableTemplateFile(response, tableId);
        });
    }

    @Test
    @DisplayName("TC035: 分页参数边界测试")
    void testSelectDatabase_BoundaryPagination() {
        // Arrange
        DataBaseSearchVo searchVo = new DataBaseSearchVo();
        searchVo.setPageNum(1L);
        searchVo.setPageSize(1L);

        Page<DbInfo> mockPage = new Page<>(1, 1);
        List<DbInfo> dbInfoList = new ArrayList<>();
        dbInfoList.add(createMockDbInfo(1L, "边界测试数据库", "边界测试"));
        mockPage.setRecords(dbInfoList);
        mockPage.setTotal(1);

        when(databaseService.selectPage(any(DataBaseSearchVo.class))).thenReturn(mockPage);

        // Act
        ApiResult<Page<DbInfo>> result = dataBaseController.selectDatabase(searchVo);

        // Assert
        assertNotNull(result);
        assertEquals(0, result.code());
        assertEquals(1, result.data().getRecords().size());
    }

    @Test
    @DisplayName("TC036: 大分页参数测试")
    void testSelectDatabase_LargePagination() {
        // Arrange
        DataBaseSearchVo searchVo = new DataBaseSearchVo();
        searchVo.setPageNum(1L);
        searchVo.setPageSize(1000L);

        Page<DbInfo> mockPage = new Page<>(1, 1000);
        List<DbInfo> dbInfoList = new ArrayList<>();
        for (int i = 1; i <= 50; i++) {
            dbInfoList.add(createMockDbInfo((long) i, "数据库" + i, "描述" + i));
        }
        mockPage.setRecords(dbInfoList);
        mockPage.setTotal(50);

        when(databaseService.selectPage(any(DataBaseSearchVo.class))).thenReturn(mockPage);

        // Act
        ApiResult<Page<DbInfo>> result = dataBaseController.selectDatabase(searchVo);

        // Assert
        assertNotNull(result);
        assertEquals(0, result.code());
        assertEquals(50, result.data().getRecords().size());
    }

    @Test
    @DisplayName("TC046: 获取空数据库的表列表")
    void testGetDbTableList_EmptyDatabase() {
        // Arrange
        Long dbId = 1L;
        List<DbTableVo> emptyTableList = new ArrayList<>();

        when(databaseService.getDbTableList(dbId)).thenReturn(emptyTableList);

        // Act
        ApiResult<List<DbTableVo>> result = dataBaseController.getDbTableList(dbId);

        // Assert
        assertNotNull(result);
        assertEquals(0, result.code());
        assertEquals(0, result.data().size());
    }

    @Test
    @DisplayName("TC066: CSV文件导入字段成功")
    void testImportDbTableField_CSV_Success() {
        // Arrange
        MockMultipartFile file = new MockMultipartFile(
                        "file",
                        "fields.csv",
                        "text/csv",
                        "field_name,field_type,description\ntest_field,VARCHAR,测试字段".getBytes());

        List<DbTableFieldDto> mockFields = new ArrayList<>();
        DbTableFieldDto field = new DbTableFieldDto();
        field.setName("test_field");
        field.setType("VARCHAR");
        field.setDescription("测试字段");
        mockFields.add(field);

        when(databaseService.importDbTableField(any())).thenReturn(mockFields);

        // Act
        ApiResult<List<DbTableFieldDto>> result = dataBaseController.importDbTableField(file);

        // Assert
        assertNotNull(result);
        assertEquals(0, result.code());
        assertEquals(1, result.data().size());
        assertEquals("test_field", result.data().get(0).getName());
    }

    @Test
    @DisplayName("TC069: 文件内容格式错误")
    void testImportDbTableField_InvalidContent() {
        // Arrange
        MockMultipartFile file = new MockMultipartFile(
                        "file",
                        "invalid_fields.xlsx",
                        "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                        "invalid content".getBytes());

        when(databaseService.importDbTableField(any()))
                        .thenThrow(new CustomException(CustomExceptionCode.DATABASE_TABLE_FIELD_IMPORT_DEFAULT));

        // Act & Assert
        assertThrows(CustomException.class, () -> {
            dataBaseController.importDbTableField(file);
        });
    }

    // ========== 辅助方法 ==========

    private DbInfo createMockDbInfo(Long id, String name, String description) {
        DbInfo dbInfo = new DbInfo();
        dbInfo.setId(id);
        dbInfo.setName(name);
        dbInfo.setDescription(description);
        dbInfo.setUid("testUser123");
        dbInfo.setSpaceId(1001L);
        dbInfo.setDbId(id * 100L);
        dbInfo.setDeleted(false);
        dbInfo.setCreateTime(new Date());
        dbInfo.setUpdateTime(new Date());
        return dbInfo;
    }
}
