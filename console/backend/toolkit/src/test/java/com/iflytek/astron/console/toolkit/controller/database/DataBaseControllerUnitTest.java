package com.iflytek.astron.console.toolkit.controller.database;

import com.alibaba.fastjson2.JSONObject;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.iflytek.astron.console.toolkit.common.CustomExceptionCode;
import com.iflytek.astron.console.toolkit.config.exception.CustomException;
import com.iflytek.astron.console.toolkit.entity.dto.database.*;
import com.iflytek.astron.console.toolkit.entity.table.database.DbInfo;
import com.iflytek.astron.console.toolkit.entity.table.database.DbTableField;
import com.iflytek.astron.console.toolkit.entity.vo.database.*;
import com.iflytek.astron.console.toolkit.service.database.DatabaseService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.mock.web.MockMultipartFile;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("数据库控制器单元测试 - 仅验证业务逻辑")
class DataBaseControllerUnitTest {

    @Mock
    private DatabaseService databaseService;

    @InjectMocks
    private DataBaseController dataBaseController;

    @BeforeEach
    void setUp() {
        // 不需要Spring上下文，仅测试控制器业务逻辑
    }

    // ========== 服务调用验证测试 ==========

    @Test
    @DisplayName("验证创建数据库服务调用")
    void testCreateDatabase_ServiceCall() {
        // Arrange
        DatabaseDto databaseDto = new DatabaseDto();
        databaseDto.setName("测试数据库");
        databaseDto.setDescription("测试数据库描述");

        DbInfo mockDbInfo = createMockDbInfo(1L, "测试数据库", "测试数据库描述");
        when(databaseService.create(any(DatabaseDto.class))).thenReturn(mockDbInfo);

        // Act & Assert - 验证服务被正确调用
        assertDoesNotThrow(() -> {
            dataBaseController.createDatabase(databaseDto);
        });
        verify(databaseService, times(1)).create(databaseDto);
    }

    @Test
    @DisplayName("验证查询数据库详情服务调用")
    void testGetDatabaseInfo_ServiceCall() {
        // Arrange
        Long dbId = 1L;
        DbInfo mockDbInfo = createMockDbInfo(dbId, "测试数据库", "测试数据库描述");
        when(databaseService.getDatabaseInfo(dbId)).thenReturn(mockDbInfo);

        // Act & Assert
        assertDoesNotThrow(() -> {
            dataBaseController.getDatabaseInfo(dbId);
        });
        verify(databaseService, times(1)).getDatabaseInfo(dbId);
    }

    @Test
    @DisplayName("验证更新数据库服务调用")
    void testUpdateDatabase_ServiceCall() {
        // Arrange
        DatabaseDto databaseDto = new DatabaseDto();
        databaseDto.setId(1L);
        databaseDto.setName("更新后的数据库");
        databaseDto.setDescription("更新后的描述");

        doNothing().when(databaseService).updateDateBase(any(DatabaseDto.class));

        // Act & Assert
        assertDoesNotThrow(() -> {
            dataBaseController.updateDatabase(databaseDto);
        });
        verify(databaseService, times(1)).updateDateBase(databaseDto);
    }

    @Test
    @DisplayName("验证删除数据库服务调用")
    void testDeleteDatabase_ServiceCall() {
        // Arrange
        Long dbId = 1L;
        doNothing().when(databaseService).delete(dbId);

        // Act & Assert
        assertDoesNotThrow(() -> {
            dataBaseController.deleteDatabase(dbId);
        });
        verify(databaseService, times(1)).delete(dbId);
    }

    @Test
    @DisplayName("验证复制数据库服务调用")
    void testCopyDatabase_ServiceCall() {
        // Arrange
        Long dbId = 1L;
        doNothing().when(databaseService).copyDatabase(dbId);

        // Act & Assert
        assertDoesNotThrow(() -> {
            dataBaseController.copyDatabase(dbId);
        });
        verify(databaseService, times(1)).copyDatabase(dbId);
    }

    @Test
    @DisplayName("验证分页查询数据库列表服务调用")
    void testSelectDatabase_ServiceCall() {
        // Arrange
        DataBaseSearchVo searchVo = new DataBaseSearchVo();
        searchVo.setPageNum(1L);
        searchVo.setPageSize(10L);
        searchVo.setSearch("测试");

        Page<DbInfo> mockPage = new Page<>(1, 10);
        List<DbInfo> dbInfoList = new ArrayList<>();
        dbInfoList.add(createMockDbInfo(1L, "测试数据库1", "描述1"));
        mockPage.setRecords(dbInfoList);
        mockPage.setTotal(1);

        when(databaseService.selectPage(any(DataBaseSearchVo.class))).thenReturn(mockPage);

        // Act & Assert
        assertDoesNotThrow(() -> {
            dataBaseController.selectDatabase(searchVo);
        });
        verify(databaseService, times(1)).selectPage(searchVo);
    }

    @Test
    @DisplayName("验证创建表服务调用")
    void testCreateDbTable_ServiceCall() {
        // Arrange
        DbTableDto dbTableDto = new DbTableDto();
        dbTableDto.setDbId(1L);
        dbTableDto.setName("用户表");
        dbTableDto.setDescription("用户信息表");

        doNothing().when(databaseService).createDbTable(any(DbTableDto.class));

        // Act & Assert
        assertDoesNotThrow(() -> {
            dataBaseController.createDbTable(dbTableDto);
        });
        verify(databaseService, times(1)).createDbTable(dbTableDto);
    }

    @Test
    @DisplayName("验证获取表列表服务调用")
    void testGetDbTableList_ServiceCall() {
        // Arrange
        Long dbId = 1L;
        List<DbTableVo> mockTableList = new ArrayList<>();
        when(databaseService.getDbTableList(dbId)).thenReturn(mockTableList);

        // Act & Assert
        assertDoesNotThrow(() -> {
            dataBaseController.getDbTableList(dbId);
        });
        verify(databaseService, times(1)).getDbTableList(dbId);
    }

    @Test
    @DisplayName("验证获取数据库表信息服务调用")
    void testGetDbTableInfoList_ServiceCall() {
        // Arrange
        List<DbTableInfoVo> mockTableInfoList = new ArrayList<>();
        when(databaseService.getDbTableInfoList()).thenReturn(mockTableInfoList);

        // Act & Assert
        assertDoesNotThrow(() -> {
            dataBaseController.getDbTableInfoList();
        });
        verify(databaseService, times(1)).getDbTableInfoList();
    }

    @Test
    @DisplayName("验证更新表字段服务调用")
    void testUpdateTable_ServiceCall() {
        // Arrange
        DbTableDto dbTableDto = new DbTableDto();
        dbTableDto.setId(1L);
        dbTableDto.setName("更新后的表");

        doNothing().when(databaseService).updateTable(any(DbTableDto.class));

        // Act & Assert
        assertDoesNotThrow(() -> {
            dataBaseController.updateTable(dbTableDto);
        });
        verify(databaseService, times(1)).updateTable(dbTableDto);
    }

    @Test
    @DisplayName("验证删除表服务调用")
    void testDeleteTable_ServiceCall() {
        // Arrange
        Long tableId = 1L;
        doNothing().when(databaseService).deleteTable(tableId);

        // Act & Assert
        assertDoesNotThrow(() -> {
            dataBaseController.deleteTable(tableId);
        });
        verify(databaseService, times(1)).deleteTable(tableId);
    }

    @Test
    @DisplayName("验证复制表服务调用")
    void testCopyTable_ServiceCall() {
        // Arrange
        Long tableId = 1L;
        doNothing().when(databaseService).copyTable(tableId);

        // Act & Assert
        assertDoesNotThrow(() -> {
            dataBaseController.copyTable(tableId);
        });
        verify(databaseService, times(1)).copyTable(tableId);
    }

    @Test
    @DisplayName("验证导入表字段服务调用")
    void testImportDbTableField_ServiceCall() {
        // Arrange
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "fields.xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                "test content".getBytes());

        List<DbTableFieldDto> mockFields = new ArrayList<>();
        when(databaseService.importDbTableField(any())).thenReturn(mockFields);

        // Act & Assert
        assertDoesNotThrow(() -> {
            dataBaseController.importDbTableField(file);
        });
        verify(databaseService, times(1)).importDbTableField(file);
    }

    @Test
    @DisplayName("验证获取表字段列表服务调用")
    void testGetDbTableFieldList_ServiceCall() {
        // Arrange
        DataBaseSearchVo searchVo = new DataBaseSearchVo();
        searchVo.setPageNum(1L);
        searchVo.setPageSize(10L);

        Page<DbTableField> mockPage = new Page<>(1, 10);
        when(databaseService.getDbTableFieldList(any(DataBaseSearchVo.class))).thenReturn(mockPage);

        // Act & Assert
        assertDoesNotThrow(() -> {
            dataBaseController.getDbTableFieldList(searchVo);
        });
        verify(databaseService, times(1)).getDbTableFieldList(searchVo);
    }

    @Test
    @DisplayName("验证操作表数据服务调用")
    void testOperateTableData_ServiceCall() {
        // Arrange
        DbTableOperateDto operateDto = new DbTableOperateDto();
        operateDto.setTbId(1L);
        operateDto.setExecDev(1);

        doNothing().when(databaseService).operateTableData(any(DbTableOperateDto.class));

        // Act & Assert
        assertDoesNotThrow(() -> {
            dataBaseController.operateTableData(operateDto);
        });
        verify(databaseService, times(1)).operateTableData(operateDto);
    }

    @Test
    @DisplayName("验证查询表数据服务调用")
    void testSelectTableData_ServiceCall() {
        // Arrange
        DbTableSelectDataDto selectDto = new DbTableSelectDataDto();
        selectDto.setTbId(1L);
        selectDto.setPageNum(1L);
        selectDto.setPageSize(10L);

        Page<JSONObject> mockPage = new Page<>(1, 10);
        when(databaseService.selectTableData(any(DbTableSelectDataDto.class))).thenReturn(mockPage);

        // Act & Assert
        assertDoesNotThrow(() -> {
            dataBaseController.selectTableData(selectDto);
        });
        verify(databaseService, times(1)).selectTableData(selectDto);
    }

    @Test
    @DisplayName("验证导入表数据服务调用")
    void testImportTableData_ServiceCall() {
        // Arrange
        Long tableId = 1L;
        Integer execDev = 1;
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "data.xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                "test data content".getBytes());

        doNothing().when(databaseService).importTableData(tableId, execDev, file);

        // Act & Assert
        assertDoesNotThrow(() -> {
            dataBaseController.importTableData(tableId, file, execDev);
        });
        verify(databaseService, times(1)).importTableData(tableId, execDev, file);
    }

    @Test
    @DisplayName("验证导出表数据服务调用")
    void testExportTableData_ServiceCall() {
        // Arrange
        DatabaseExportDto exportDto = new DatabaseExportDto();
        exportDto.setTbId(1L);
        exportDto.setExecDev(1);

        MockHttpServletResponse response = new MockHttpServletResponse();
        doNothing().when(databaseService).exportTableData(any(DatabaseExportDto.class), any());

        // Act & Assert
        assertDoesNotThrow(() -> {
            dataBaseController.exportTableData(exportDto, response);
        });
        verify(databaseService, times(1)).exportTableData(exportDto, response);
    }

    @Test
    @DisplayName("验证获取表模板文件服务调用")
    void testGetTableTemplateFile_ServiceCall() {
        // Arrange
        Long tableId = 1L;
        MockHttpServletResponse response = new MockHttpServletResponse();
        doNothing().when(databaseService).getTableTemplateFile(any(), eq(tableId));

        // Act & Assert
        assertDoesNotThrow(() -> {
            dataBaseController.getTableTemplateFile(response, tableId);
        });
        verify(databaseService, times(1)).getTableTemplateFile(response, tableId);
    }

    // ========== 异常传播测试 ==========

    @Test
    @DisplayName("验证创建数据库异常传播")
    void testCreateDatabase_ExceptionPropagation() {
        // Arrange
        DatabaseDto databaseDto = new DatabaseDto();
        databaseDto.setName("");

        when(databaseService.create(any(DatabaseDto.class)))
                .thenThrow(new CustomException(CustomExceptionCode.DATABASE_NAME_NOT_EMPTY));

        // Act & Assert
        assertThrows(CustomException.class, () -> {
            dataBaseController.createDatabase(databaseDto);
        });
    }

    @Test
    @DisplayName("验证查询数据库异常传播")
    void testGetDatabaseInfo_ExceptionPropagation() {
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
    @DisplayName("验证删除数据库异常传播")
    void testDeleteDatabase_ExceptionPropagation() {
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
    @DisplayName("验证导入文件异常传播")
    void testImportDbTableField_ExceptionPropagation() {
        // Arrange
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "invalid.txt",
                "text/plain",
                "invalid content".getBytes());

        when(databaseService.importDbTableField(any()))
                .thenThrow(new CustomException(CustomExceptionCode.REPO_FILE_UPLOAD_FAILED));

        // Act & Assert
        assertThrows(CustomException.class, () -> {
            dataBaseController.importDbTableField(file);
        });
    }

    // ========== 参数验证测试 ==========

    @Test
    @DisplayName("验证空参数处理")
    void testNullParameterHandling() {
        // 这些测试验证控制器如何处理null参数
        // 在实际应用中，这些可能会被Spring的参数验证拦截

        // 验证服务层会被调用（即使参数为null）
        assertDoesNotThrow(() -> {
            dataBaseController.getDatabaseInfo(null);
        });
        verify(databaseService, times(1)).getDatabaseInfo(null);
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
