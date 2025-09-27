package com.iflytek.astron.console.toolkit.service.database;

import cn.hutool.poi.excel.ExcelUtil;
import com.alibaba.excel.EasyExcel;
import com.alibaba.fastjson2.JSONObject;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;

import com.iflytek.astron.console.commons.util.space.SpaceInfoUtil;
import com.iflytek.astron.console.commons.constant.ResponseEnum;
import com.iflytek.astron.console.commons.exception.BusinessException;
import com.iflytek.astron.console.toolkit.common.constant.CommonConst;
import com.iflytek.astron.console.toolkit.config.jooq.JooqBatchExecutor;
import com.iflytek.astron.console.toolkit.config.properties.CommonConfig;
import com.iflytek.astron.console.toolkit.entity.dto.database.*;
import com.iflytek.astron.console.toolkit.entity.enumVo.DBOperateEnum;
import com.iflytek.astron.console.toolkit.entity.table.database.*;
import com.iflytek.astron.console.toolkit.entity.table.relation.FlowDbRel;
import com.iflytek.astron.console.toolkit.entity.vo.database.*;
import com.iflytek.astron.console.toolkit.handler.UserInfoManagerHandler;
import com.iflytek.astron.console.toolkit.mapper.ConfigInfoMapper;
import com.iflytek.astron.console.toolkit.mapper.database.*;
import com.iflytek.astron.console.toolkit.mapper.relation.FlowDbRelMapper;
import com.iflytek.astron.console.toolkit.service.extra.CoreSystemService;
import com.iflytek.astron.console.toolkit.tool.DataPermissionCheckTool;
import com.iflytek.astron.console.toolkit.util.S3Util;
import com.iflytek.astron.console.toolkit.util.database.NamePolicy;
import com.iflytek.astron.console.toolkit.util.database.SqlRenderer;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.jooq.*;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.net.URLEncoder;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.Comparator;
import java.util.stream.Collectors;

import static org.jooq.impl.DSL.*;


/**
 * <p>
 * Database Service Implementation
 * </p>
 *
 * @author jinggu2
 * @since 2025-05-19
 */
@Service
@Slf4j
public class DatabaseService extends ServiceImpl<DbInfoMapper, DbInfo> {

    @Autowired
    DbInfoMapper dbInfoMapper;

    @Autowired
    DbTableMapper dbTableMapper;

    @Autowired
    DbTableFieldMapper dbTableFieldMapper;

    @Autowired
    DataPermissionCheckTool dataPermissionCheckTool;

    @Autowired
    private S3Util s3Util;

    @Autowired
    private CoreSystemService coreSystemService;

    @Autowired
    private FlowDbRelMapper flowDbRelMapper;

    @Autowired
    private ConfigInfoMapper configInfoMapper;

    @Autowired
    private DSLContext dslCon;
    @Autowired
    private CommonConfig commonConfig;

    private static final String[] SYSTEM_FIELDS = {"id", "uid", "create_time"};
    // New additions in DatabaseService
    private static final int MAX_PAGE_SIZE = 1000; // Prevent explosion
    private static final int MAX_EXPORT_IDS = 1000; // IN clause limit

    @Transactional
    public DbInfo create(DatabaseDto databaseDto) {
        try {
            // Required field validation
            if (!StringUtils.isNotBlank(databaseDto.getName())) {
                throw new BusinessException(ResponseEnum.DATABASE_NAME_NOT_EMPTY);
            }
            String userId = Objects.requireNonNull(UserInfoManagerHandler.getUserId()).toString();
            Long spaceId = SpaceInfoUtil.getSpaceId();
            // Duplicate name validation
            Long count = 0L;
            if (spaceId == null) {
                count = dbInfoMapper.selectCount(new QueryWrapper<DbInfo>().lambda()
                        .eq(DbInfo::getName, databaseDto.getName())
                        .eq(DbInfo::getSpaceId, null)
                        .eq(DbInfo::getUid, userId)
                        .eq(DbInfo::getDeleted, false));
            } else {
                count = dbInfoMapper.selectCount(new QueryWrapper<DbInfo>().lambda()
                        .eq(DbInfo::getName, databaseDto.getName())
                        .eq(DbInfo::getUid, userId)
                        .eq(DbInfo::getSpaceId, spaceId)
                        .eq(DbInfo::getDeleted, false));
            }
            if (count > 0) {
                throw new BusinessException(ResponseEnum.DATABASE_NAME_EXIST);
            }
            // Call core system to create database
            Long dbId = coreSystemService.createDatabase(databaseDto.getName(), userId, spaceId, databaseDto.getDescription());
            // Save record
            DbInfo database = new DbInfo();
            BeanUtils.copyProperties(databaseDto, database);
            database.setUid(userId);
            database.setAppId(commonConfig.getAppId());
            database.setDbId(dbId);
            database.setCreateTime(new Date());
            database.setUpdateTime(new Date());
            database.setSpaceId(spaceId);
            dbInfoMapper.insert(database);
            return database;
        } catch (Exception ex) {
            log.info("Failed to create database, params:{}", databaseDto.toString(), ex);
            throw new BusinessException(ResponseEnum.DATABASE_CREATE_FAILED);
        }
    }

    @Transactional
    public void updateDateBase(DatabaseDto databaseDto) {
        try {
            dataPermissionCheckTool.checkDbUpdateBelong(databaseDto.getId());
            // Name validation
            DbInfo dbInfo = dbInfoMapper.selectById(databaseDto.getId());
            if (StringUtils.isNotBlank(databaseDto.getDescription())) {
                if (!databaseDto.getDescription().equals(dbInfo.getDescription())) {
                    coreSystemService.modifyDataBase(dbInfo.getDbId(), UserInfoManagerHandler.getUserId(), databaseDto.getDescription());
                }
                dbInfo.setDescription(databaseDto.getDescription());
            }
            dbInfoMapper.updateById(dbInfo);
        } catch (Exception ex) {
            log.error("Failed to update database, params={}", JSONObject.toJSONString(databaseDto), ex);
            throw new BusinessException(ResponseEnum.DATABASE_UPDATE_FAILED);
        }
    }

    public void delete(Long id) {
        try {
            // Check if the database is being referenced
            dataPermissionCheckTool.checkDbUpdateBelong(id);
            DbInfo dbInfo = dbInfoMapper.selectById(id);
            Long count = flowDbRelMapper.selectCount(new QueryWrapper<FlowDbRel>().lambda()
                    .eq(FlowDbRel::getDbId, dbInfo.getDbId()));
            if (count > 0) {
                throw new BusinessException(ResponseEnum.DATABASE_DELETE_FAILED_CITED);
            }
            // Delete from core system
            coreSystemService.dropDataBase(dbInfo.getDbId(), UserInfoManagerHandler.getUserId());
            dbInfo.setDeleted(true);
            dbInfoMapper.updateById(dbInfo);
        } catch (Exception ex) {
            log.error("Failed to delete database, dbId={}", id, ex);
            throw ex;
        }
    }

    @Transactional
    public void copyDatabase(Long id) {
        try {
            DbInfo dbInfo = dbInfoMapper.selectById(id);
            DbInfo newDbInfo = new DbInfo();
            newDbInfo.setName(dbInfo.getName() + "_副本");
            newDbInfo.setDescription(dbInfo.getDescription());
            newDbInfo.setUid(dbInfo.getUid());
            newDbInfo.setAppId(dbInfo.getAppId());
            newDbInfo.setCreateTime(new Date());
            newDbInfo.setUpdateTime(new Date());
            dbInfoMapper.insert(newDbInfo);
            // Build DDL
            dbTableMapper.selectList(new QueryWrapper<DbTable>().lambda()
                    .eq(DbTable::getDbId, dbInfo.getId())
                    .eq(DbTable::getDeleted, false))
                    .forEach(dbTable -> {
                        DbTable newDbTable = new DbTable();
                        newDbTable.setDbId(newDbInfo.getId());
                        newDbTable.setName(dbTable.getName());
                        newDbTable.setDescription(dbTable.getDescription());
                        newDbTable.setCreateTime(new Date());
                        newDbTable.setCreateTime(new Date());
                        dbTableMapper.insert(newDbTable);
                        // Create table fields
                        List<DbTableField> fields = new ArrayList<>();
                        dbTableFieldMapper.selectList(new QueryWrapper<DbTableField>().lambda()
                                .eq(DbTableField::getTbId, dbTable.getId())).forEach(dbTableField -> {
                                    DbTableField newDbTableField = new DbTableField();
                                    BeanUtils.copyProperties(dbTableField, newDbTableField);
                                    newDbTableField.setTbId(newDbTable.getId());
                                    newDbTableField.setId(null);
                                    newDbTableField.setCreateTime(new Date());
                                    newDbTableField.setUpdateTime(new Date());
                                    fields.add(newDbTableField);
                                });
                        dbTableFieldMapper.insertBatch(fields);
                    });
            // Call core system to create database
            Long dbId = coreSystemService.cloneDataBase(dbInfo.getDbId(), newDbInfo.getName(), UserInfoManagerHandler.getUserId());
            newDbInfo.setDbId(dbId);
            dbInfoMapper.updateById(newDbInfo);
        } catch (Exception ex) {
            log.error("copy database failed,dbId={}", id, ex);
            throw new BusinessException(ResponseEnum.DATABASE_COPY_FAILED);
        }

    }


    public void addFlowRel(String dbId, String tbName, String flowId) {
        DbTable dbTable = dbTableMapper.selectByDbId(dbId, tbName);
        FlowDbRel flowDbRel = new FlowDbRel();
        flowDbRel.setFlowId(flowId);
        flowDbRel.setDbId(dbId);
        flowDbRel.setTbId(dbTable.getId());
        flowDbRel.setCreateTime(new Date());
        flowDbRelMapper.insert(flowDbRel);
    }

    public Page<DbInfo> selectPage(DataBaseSearchVo databaseDto) {
        try {
            Long spaceId = SpaceInfoUtil.getSpaceId();
            Page<DbInfo> page = new Page<>(databaseDto.getPageNum(), databaseDto.getPageSize());
            LambdaQueryWrapper<DbInfo> lqw = new QueryWrapper<DbInfo>().lambda()
                    .eq(DbInfo::getDeleted, false)
                    .and(StringUtils.isNotBlank(databaseDto.getSearch()),
                            wrapper -> wrapper.like(DbInfo::getName, databaseDto.getSearch())
                                    .or()
                                    .like(DbInfo::getDescription, databaseDto.getSearch()));
            if (spaceId != null) {
                lqw.eq(DbInfo::getSpaceId, spaceId);
            } else {
                lqw.isNull(DbInfo::getSpaceId);
                lqw.eq(DbInfo::getUid, UserInfoManagerHandler.getUserId());
            }
            lqw.orderByDesc(DbInfo::getCreateTime);
            page = dbInfoMapper.selectPage(page, lqw);
            return page;
        } catch (Exception ex) {
            log.error("Failed to query database list, params={}", JSONObject.toJSONString(databaseDto), ex);
            throw new BusinessException(ResponseEnum.DATABASE_QUERY_FAILED);
        }
    }

    @Transactional
    public void createDbTable(DbTableDto dbTableDto) {
        dataPermissionCheckTool.checkDbBelong(dbTableDto.getDbId());
        try {
            DbInfo dbInfo = dbInfoMapper.selectById(dbTableDto.getDbId());
            if (dbInfo == null) {
                throw new BusinessException(ResponseEnum.DATABASE_NOT_EXIST);
            }
            // Table count limit
            Long tableCount = dbTableMapper.selectCount(new QueryWrapper<DbTable>().lambda()
                    .eq(DbTable::getDbId, dbInfo.getDbId())
                    .eq(DbTable::getDeleted, false));
            if (tableCount > 20) {
                throw new BusinessException(ResponseEnum.DATABASE_COUNT_LIMITED);
            }
            // Duplicate table name validation
            Long count = dbTableMapper.selectCount(new QueryWrapper<DbTable>().lambda()
                    .eq(DbTable::getName, dbTableDto.getName())
                    .eq(DbTable::getDbId, dbInfo.getDbId())
                    .eq(DbTable::getDeleted, false));
            if (count > 0) {
                throw new BusinessException(ResponseEnum.DATABASE_TABLE_NAME_EXIST);
            }
            // Build DDL statement and validate required system fields
            if (dbTableDto.getFields() == null || dbTableDto.getFields().isEmpty()) {
                throw new BusinessException(ResponseEnum.DATABASE_TABLE_FIELD_CANNOT_EMPTY);
            }
            // Table fields cannot exceed 20
            if (dbTableDto.getFields().size() > 20) {
                throw new BusinessException(ResponseEnum.DATABASE_FIELD_CANNOT_BEYOND_20);
            }
            // Save information
            DbTable dbTable = new DbTable();
            BeanUtils.copyProperties(dbTableDto, dbTable);
            dbTable.setCreateTime(new Date());
            dbTable.setUpdateTime(new Date());
            dbTableMapper.insert(dbTable);
            List<String> systemFields = Arrays.asList(SYSTEM_FIELDS);
            List<DbTableField> fields = new ArrayList<>();
            for (DbTableFieldDto field : dbTableDto.getFields()) {
                DbTableField dbTableField = new DbTableField();
                BeanUtils.copyProperties(field, dbTableField);
                if (systemFields.contains(field.getName())) {
                    dbTableField.setIsSystem(true);
                } else {
                    if (StringUtils.isBlank(dbTableField.getDefaultValue())) {
                        dbTableField.setDefaultValue(transFormDefaultValue(field.getType()).toString());
                        field.setDefaultValue(transFormDefaultValue(field.getType()).toString());
                    }
                    dbTableField.setIsSystem(false);
                }
                dbTableField.setTbId(dbTable.getId());
                dbTableField.setCreateTime(new Date());
                dbTableField.setUpdateTime(new Date());
                fields.add(dbTableField);
            }
            dbTableFieldMapper.insertBatch(fields);
            // Save to core system
            String ddl = buildDDL(dbTableDto, DBOperateEnum.INSERT.getCode(), null);
            // Call core system to create table
            for (String stmt : safeSplitStatements(ddl)) {
                SqlRenderer.denyMultiStmtOrComment(stmt); // At this point each statement does not contain semicolon
                coreSystemService.execDDL(stmt, UserInfoManagerHandler.getUserId(), SpaceInfoUtil.getSpaceId(), dbInfo.getDbId());
            }
        } catch (Exception ex) {
            log.error("Failed to create table, params={}", dbTableDto, ex);
            throw new BusinessException(ResponseEnum.DATABASE_TABLE_CREATE_FAILED);
        }

    }


    public List<DbTableVo> getDbTableList(Long dbId) {
        try {
            if (dbId == null) {
                throw new BusinessException(ResponseEnum.DATABASE_ID_CANNOT_EMPTY);
            }
            dataPermissionCheckTool.checkDbBelong(dbId);
            List<DbTable> dbTables = dbTableMapper.selectList(new QueryWrapper<DbTable>().lambda()
                    .eq(DbTable::getDbId, dbId)
                    .orderByDesc(DbTable::getCreateTime)
                    .eq(DbTable::getDeleted, false));
            List<DbTableVo> dbTableVos = new ArrayList<>();
            dbTables.forEach(dbTable -> {
                DbTableVo dbTableVo = new DbTableVo();
                BeanUtils.copyProperties(dbTable, dbTableVo);
                dbTableVos.add(dbTableVo);
            });
            return dbTableVos;
        } catch (Exception ex) {
            log.error("Failed to get table list, dbId={}", dbId, ex);
            throw new BusinessException(ResponseEnum.DATABASE_TABLE_QUERY_LIST_FAILED);
        }
    }

    public Page<DbTableField> getDbTableFieldList(DataBaseSearchVo dataBaseSearchVo) {
        dataPermissionCheckTool.checkTbBelong(dataBaseSearchVo.getTbId());
        try {
            Page<DbTableField> page = new Page<>(dataBaseSearchVo.getPageNum(), dataBaseSearchVo.getPageSize());
            page = dbTableFieldMapper.selectPage(page, new QueryWrapper<DbTableField>().lambda()
                    .eq(DbTableField::getTbId, dataBaseSearchVo.getTbId()));
            return page;
        } catch (Exception ex) {
            log.error("Failed to get table field list, params={}", JSONObject.toJSONString(dataBaseSearchVo), ex);
            throw new BusinessException(ResponseEnum.DATABASE_TABLE_QUERY_FIELD_FAILED);
        }

    }

    @Transactional
    public void updateTable(DbTableDto dbTableDto) {
        try {
            dataPermissionCheckTool.checkTbBelong(dbTableDto.getId());
            // Update table structure
            DbTable dbTable = dbTableMapper.selectById(dbTableDto.getId());
            String originName = dbTable.getName();
            // Filter system fields id, uid, create_time
            List<String> allowedNames = Arrays.asList(SYSTEM_FIELDS);
            if (dbTableDto.getFields() != null && !dbTableDto.getFields().isEmpty()) {
                // Filter out system fields
                dbTableDto.setFields(dbTableDto.getFields()
                        .stream()
                        .filter(field -> !allowedNames.contains(field.getName()))
                        .peek(field -> {
                            // Set default value
                            if (StringUtils.isBlank(field.getDefaultValue())) {
                                field.setDefaultValue(transFormDefaultValue(field.getType()).toString());
                            }
                        })
                        .collect(Collectors.toList()));
            }
            // Update core system side
            String ddl = buildDDL(dbTableDto, DBOperateEnum.UPDATE.getCode(), originName);
            if (!dbTable.getName().equals(dbTableDto.getName())) {
                // Check if table name already exists
                Long count = dbTableMapper.selectCount(new QueryWrapper<DbTable>().lambda()
                        .eq(DbTable::getName, dbTableDto.getName())
                        .eq(DbTable::getDbId, dbTable.getDbId())
                        .ne(DbTable::getId, dbTableDto.getId())
                        .eq(DbTable::getDeleted, false));
                if (count > 0) {
                    throw new BusinessException(ResponseEnum.DATABASE_TABLE_NAME_EXIST);
                }
            }
            // Query table field count
            Long fieldCount = dbTableFieldMapper.selectCount(new QueryWrapper<DbTableField>().lambda()
                    .eq(DbTableField::getTbId, dbTable.getId()));
            // Count the number of new fields and deleted fields
            long insertCount = dbTableDto.getFields()
                    .stream()
                    .filter(field -> DBOperateEnum.INSERT.getCode().equals(field.getOperateType()))
                    .count();
            long deleteCount = dbTableDto.getFields()
                    .stream()
                    .filter(field -> DBOperateEnum.DELETE.getCode().equals(field.getOperateType()))
                    .count();
            if (fieldCount + insertCount - deleteCount > 20) {
                throw new BusinessException(ResponseEnum.DATABASE_FIELD_CANNOT_BEYOND_20);
            }
            DbInfo dbInfo = dbInfoMapper.selectById(dbTable.getDbId());
            if (dbTableDto.getFields() != null && !dbTableDto.getFields().isEmpty()) {
                for (DbTableFieldDto field : dbTableDto.getFields()) {
                    DbTableField dbTableField = dbTableFieldMapper.selectById(field.getId());
                    if (DBOperateEnum.INSERT.getCode().equals(field.getOperateType())) {
                        DbTableField newDbTableField = new DbTableField();
                        BeanUtils.copyProperties(field, newDbTableField);
                        newDbTableField.setTbId(dbTable.getId());
                        newDbTableField.setCreateTime(new Date());
                        newDbTableField.setUpdateTime(new Date());
                        dbTableFieldMapper.insert(newDbTableField);
                    } else if (DBOperateEnum.UPDATE.getCode().equals(field.getOperateType())) {
                        BeanUtils.copyProperties(field, dbTableField);
                        dbTableField.setUpdateTime(new Date());
                        dbTableFieldMapper.updateById(dbTableField);
                    } else if (DBOperateEnum.DELETE.getCode().equals(field.getOperateType())) {
                        dbTableFieldMapper.deleteById(field.getId());
                    }
                }
            }
            if (StringUtils.isNotBlank(dbTableDto.getName())) {
                dbTable.setName(dbTableDto.getName());
            }
            if (StringUtils.isNotBlank(dbTableDto.getDescription())) {
                dbTable.setDescription(dbTableDto.getDescription());
            }
            dbTable.setUpdateTime(new Date());
            dbTableMapper.updateById(dbTable);
            String userId = UserInfoManagerHandler.getUserId();
            for (String stmt : safeSplitStatements(ddl)) {
                coreSystemService.execDDL(stmt, userId, SpaceInfoUtil.getSpaceId(), dbInfo.getDbId());
            }

        } catch (Exception ex) {
            log.info("Failed to update table, params={}", dbTableDto.toString(), ex);
            throw new BusinessException(ResponseEnum.DATABASE_TABLE_UPDATE_FAILED);
        }
    }

    private String buildDDL(DbTableDto dbTableDto, Integer type, String originTbName) {
        StringBuilder ddl = new StringBuilder();

        if (DBOperateEnum.INSERT.getCode().equals(type)) {
            String table = SqlRenderer.quoteIdent(dbTableDto.getName());
            ddl.append("CREATE TABLE ")
                    .append(table)
                    .append(" (\n")
                    .append("  ")
                    .append(SqlRenderer.quoteIdent("id"))
                    .append(" BIGSERIAL PRIMARY KEY,\n")
                    .append("  ")
                    .append(SqlRenderer.quoteIdent("uid"))
                    .append(" VARCHAR(64) NOT NULL,\n")
                    .append("  ")
                    .append(SqlRenderer.quoteIdent("create_time"))
                    .append(" TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP");

            List<DbTableFieldDto> fields = dbTableDto.getFields()
                    .stream()
                    .filter(f -> !Arrays.asList(SYSTEM_FIELDS).contains(f.getName()))
                    .collect(Collectors.toList());

            for (DbTableFieldDto field : fields) {
                ddl.append(",\n  ")
                        .append(SqlRenderer.quoteIdent(field.getName()))
                        .append(" ")
                        .append(transFormType(field.getType()));
                if (Boolean.TRUE.equals(field.getIsRequired())) {
                    ddl.append(" NOT NULL");
                }
                // Default value
                if (StringUtils.isNotBlank(field.getDefaultValue())) {
                    ddl.append(" DEFAULT ").append(SqlRenderer.renderValue(adaptDefault(field)));
                }
            }
            ddl.append("\n);");

            // Table/column comments
            if (StringUtils.isNotBlank(dbTableDto.getDescription())) {
                ddl.append("\nCOMMENT ON TABLE ")
                        .append(table)
                        .append(" IS ")
                        .append(SqlRenderer.quoteLiteral(dbTableDto.getDescription()))
                        .append(";");
            }
            ddl.append("\nCOMMENT ON COLUMN ").append(table).append(".").append(SqlRenderer.quoteIdent("id")).append(" IS 'Primary key id';");
            ddl.append("\nCOMMENT ON COLUMN ").append(table).append(".").append(SqlRenderer.quoteIdent("uid")).append(" IS 'uid';");
            ddl.append("\nCOMMENT ON COLUMN ").append(table).append(".").append(SqlRenderer.quoteIdent("create_time")).append(" IS 'Create time';");

            for (DbTableFieldDto field : fields) {
                if (StringUtils.isNotBlank(field.getDescription())) {
                    ddl.append("\nCOMMENT ON COLUMN ")
                            .append(table)
                            .append(".")
                            .append(SqlRenderer.quoteIdent(field.getName()))
                            .append(" IS ")
                            .append(SqlRenderer.quoteLiteral(field.getDescription()))
                            .append(";");
                }
            }

        } else if (DBOperateEnum.UPDATE.getCode().equals(type)) {
            String tableNow = SqlRenderer.quoteIdent(dbTableDto.getName());
            if (StringUtils.isNotBlank(dbTableDto.getName()) && !dbTableDto.getName().equals(originTbName)) {
                String origin = SqlRenderer.quoteIdent(originTbName);
                ddl.append("ALTER TABLE ").append(origin).append(" RENAME TO ").append(tableNow).append("; ");
            }
            if (StringUtils.isNotBlank(dbTableDto.getDescription())) {
                ddl.append("COMMENT ON TABLE ")
                        .append(tableNow)
                        .append(" IS ")
                        .append(SqlRenderer.quoteLiteral(dbTableDto.getDescription()))
                        .append("; ");
            }

            // Sort operations by type (DELETE -> UPDATE -> INSERT) to avoid dependency issues
            dbTableDto.getFields().sort(Comparator.comparing(DbTableFieldDto::getOperateType).reversed());

            for (DbTableFieldDto field : dbTableDto.getFields()) {
                if (DBOperateEnum.DELETE.getCode().equals(field.getOperateType())) {
                    ddl.append(buildDropColumnSql(dbTableDto.getName(), field.getName()));
                } else if (DBOperateEnum.UPDATE.getCode().equals(field.getOperateType())) {
                    ddl.append(buildModifyColumnSql(dbTableDto.getName(), field));
                } else if (DBOperateEnum.INSERT.getCode().equals(field.getOperateType())) {
                    ddl.append(buildAddColumnSql(dbTableDto.getName(), field));
                }
            }

        } else if (DBOperateEnum.DELETE.getCode().equals(type)) {
            ddl.append("DROP TABLE IF EXISTS ").append(SqlRenderer.quoteIdent(dbTableDto.getName())).append(";");

        } else if (DBOperateEnum.COPY.getCode().equals(type)) {
            String to = SqlRenderer.quoteIdent(dbTableDto.getName());
            String from = SqlRenderer.quoteIdent(originTbName);
            ddl.append("CREATE TABLE ").append(to).append(" AS SELECT * FROM ").append(from).append(";");
        }
        return ddl.toString();
    }


    private String transFormType(String type) {
        switch (type.toLowerCase()) {
            case CommonConst.DBFieldType.STRING:
                return "VARCHAR";
            case CommonConst.DBFieldType.TIME:
                return "TIMESTAMP";
            case CommonConst.DBFieldType.NUMBER:
                return "DECIMAL";
            case CommonConst.DBFieldType.INTEGER:
                return "BIGINT";
            default:
                return type;
        }
    }

    private Object transFormDefaultValue(String type) {
        switch (type.toLowerCase()) {
            case CommonConst.DBFieldType.TIME:
                return new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date());
            case CommonConst.DBFieldType.NUMBER:
            case CommonConst.DBFieldType.INTEGER:
                return 0;
            case CommonConst.DBFieldType.BOOLEAN:
                return "false";
            default:
                return StringUtils.EMPTY;
        }
    }



    // Add field
    public String buildAddColumnSql(String tableName, DbTableFieldDto field) {
        StringBuilder sql = new StringBuilder();
        String table = SqlRenderer.quoteIdent(tableName);
        String col = SqlRenderer.quoteIdent(field.getName());

        sql.append("ALTER TABLE ")
                .append(table)
                .append(" ADD COLUMN IF NOT EXISTS ")
                .append(col)
                .append(" ")
                .append(transFormType(field.getType()));

        if (Boolean.TRUE.equals(field.getIsRequired())) {
            sql.append(" NOT NULL");
        }
        if (StringUtils.isNotBlank(field.getDefaultValue())) {
            sql.append(" DEFAULT ").append(SqlRenderer.renderValue(adaptDefault(field)));
        }
        sql.append("; ");

        if (StringUtils.isNotBlank(field.getDescription())) {
            sql.append("COMMENT ON COLUMN ")
                    .append(table)
                    .append(".")
                    .append(col)
                    .append(" IS ")
                    .append(SqlRenderer.quoteLiteral(field.getDescription()))
                    .append("; ");
        }
        return sql.toString();
    }

    // Delete field
    public static String buildDropColumnSql(String tableName, String columnName) {
        String table = SqlRenderer.quoteIdent(tableName);
        String col = SqlRenderer.quoteIdent(columnName);
        String sql = "ALTER TABLE " + table + " DROP COLUMN IF EXISTS " + col + ";";
        SqlRenderer.denyMultiStmtOrComment(sql);
        return sql;
    }

    // Edit field
    public String buildModifyColumnSql(String tableName, DbTableFieldDto field) {
        List<String> alterClauses = new ArrayList<>();
        String renameClause = null;
        StringBuilder commentSql = new StringBuilder();

        DbTableField dbTableField = dbTableFieldMapper.selectById(field.getId());
        String fromCol = SqlRenderer.quoteIdent(dbTableField.getName());
        String toCol = fromCol;
        if (StringUtils.isNotBlank(field.getName()) && !dbTableField.getName().equals(field.getName())) {
            toCol = SqlRenderer.quoteIdent(field.getName());
            renameClause = "RENAME COLUMN " + fromCol + " TO " + toCol;
        }
        if (StringUtils.isNotBlank(field.getType()) && !dbTableField.getType().equalsIgnoreCase(field.getType())) {
            alterClauses.add("ALTER COLUMN " + toCol + " SET DATA TYPE " + transFormType(field.getType()));
        }
        if (Boolean.TRUE.equals(field.getIsRequired())) {
            alterClauses.add("ALTER COLUMN " + toCol + " SET NOT NULL");
        } else {
            alterClauses.add("ALTER COLUMN " + toCol + " DROP NOT NULL");
        }
        if (!Objects.equals(field.getDefaultValue(), dbTableField.getDefaultValue())) {
            alterClauses.add("ALTER COLUMN " + toCol + " SET DEFAULT " + SqlRenderer.renderValue(adaptDefault(field)));
        }

        String table = SqlRenderer.quoteIdent(tableName);
        StringBuilder sql = new StringBuilder();
        if (renameClause != null) {
            sql.append("ALTER TABLE ").append(table).append(" ").append(renameClause).append("; ");
        }
        if (!alterClauses.isEmpty()) {
            sql.append("ALTER TABLE ").append(table).append(" ").append(String.join(", ", alterClauses)).append(";");
        }

        // Comment
        if (!StringUtils.equals(field.getDescription(), dbTableField.getDescription())) {
            if (StringUtils.isNotBlank(field.getDescription())) {
                commentSql.append(" COMMENT ON COLUMN ")
                        .append(table)
                        .append(".")
                        .append(toCol)
                        .append(" IS ")
                        .append(SqlRenderer.quoteLiteral(field.getDescription()))
                        .append("; ");
            } else {
                commentSql.append(" COMMENT ON COLUMN ")
                        .append(table)
                        .append(".")
                        .append(toCol)
                        .append(" IS NULL; ");
            }
        }

        String out = sql.append(" ").append(commentSql).toString();
        SqlRenderer.denyMultiStmtOrComment(out);
        return out;
    }

    // Delete field
    // public static String buildDropColumnSql(String tableName, String columnName) {
    // return "ALTER TABLE " + tableName + " DROP COLUMN IF EXISTS " + columnName + ";";
    // }

    /**
     * Combine field type to convert defaultValue to a more "correct" Java type, then pass it to
     * renderValue for rendering
     */
    private Object adaptDefault(DbTableFieldDto field) {
        String t = StringUtils.lowerCase(field.getType());
        String v = field.getDefaultValue();
        if (v == null)
            return null;
        switch (t) {
            case CommonConst.DBFieldType.TIME: // "yyyy-MM-dd HH:mm:ss"
                return v; // Treat as string literal, render as '...'
            case CommonConst.DBFieldType.INTEGER:
                return SqlRenderer.requireLong(v, "defaultValue");
            case CommonConst.DBFieldType.NUMBER:
                try {
                    return new java.math.BigDecimal(v);
                } catch (Exception e) {
                    return 0;
                }
            case CommonConst.DBFieldType.BOOLEAN:
                return Boolean.parseBoolean(v);
            default:
                return v; // Others as string
        }
    }

    // Edit field
    public String buildModifyColumnSqlOld(String tableName, DbTableFieldDto field) {

        List<String> alterClauses = new ArrayList<>();
        String renameClause = null;
        StringBuilder commentSql = new StringBuilder();

        // Check if name is modified
        DbTableField dbTableField = dbTableFieldMapper.selectById(field.getId());
        String colNameToUse = dbTableField.getName();
        if (field.getName() != null && !dbTableField.getName().equals(field.getName())) {
            renameClause = String.format("RENAME COLUMN %s TO %s", dbTableField.getName(), field.getName());
            colNameToUse = field.getName();
        }

        if (StringUtils.isNotBlank(field.getType()) && !dbTableField.getType().equalsIgnoreCase(field.getType())) {
            alterClauses.add(String.format("ALTER COLUMN %s SET DATA TYPE %s", colNameToUse, transFormType(field.getType())));
        }

        if (Boolean.TRUE.equals(field.getIsRequired())) {
            alterClauses.add(String.format("ALTER COLUMN %s SET NOT NULL", colNameToUse));
        } else {
            alterClauses.add(String.format("ALTER COLUMN %s DROP NOT NULL", colNameToUse));
        }

        // Set default value, only if different
        if (!field.getDefaultValue().equals(dbTableField.getDefaultValue())) {
            if (CommonConst.DBFieldType.STRING.equalsIgnoreCase(field.getType()) || CommonConst.DBFieldType.TIME.equalsIgnoreCase(field.getType())) {
                alterClauses.add(String.format("ALTER COLUMN %s SET DEFAULT '%s'", colNameToUse, field.getDefaultValue()));
            } else {
                alterClauses.add(String.format(String.format("ALTER COLUMN %s SET DEFAULT %s", colNameToUse, field.getDefaultValue())));
            }
        }
        // Concatenate ALTER TABLE statement
        StringBuilder sql = new StringBuilder();
        if (renameClause != null) {
            sql.append(String.format("ALTER TABLE %s ", tableName));
            sql.append(renameClause).append("; ");
        }
        sql.append(String.format("ALTER TABLE %s ", tableName));
        sql.append(String.join(", ", alterClauses));
        sql.append(";");


        // Check if comment changes, concatenate COMMENT statement
        if (StringUtils.isNotBlank(field.getDescription())) {
            // Set or modify comment, only execute if changed
            if (!field.getDescription().equals(dbTableField.getDescription())) {
                commentSql.append(String.format("COMMENT ON COLUMN %s.%s IS '%s'; ", tableName, colNameToUse, field.getDescription()));
            }
        } else {
            commentSql.append(String.format("COMMENT ON COLUMN %s.%s IS NULL; ", tableName, colNameToUse));
        }
        return sql.append(" ").append(commentSql).toString();
    }


    public void deleteTable(Long tbId) {
        try {
            DbTable dbTable = dbTableMapper.selectById(tbId);
            dataPermissionCheckTool.checkDbBelong(dbTable.getDbId());
            Long count = flowDbRelMapper.selectCount(new QueryWrapper<FlowDbRel>().lambda()
                    .eq(FlowDbRel::getTbId, tbId));
            if (count > 0) {
                throw new BusinessException(ResponseEnum.DATABASE_TABLE_DELETE_FAILED_CITED);
            }
            DbInfo dbInfo = dbInfoMapper.selectById(dbTable.getDbId());
            DbTableDto dbTableDto = new DbTableDto();
            dbTableDto.setName(dbTable.getName());
            String ddl = buildDDL(dbTableDto, DBOperateEnum.DELETE.getCode(), null);
            // Delete from core system
            for (String stmt : safeSplitStatements(ddl)) {
                SqlRenderer.denyMultiStmtOrComment(stmt); // At this point each statement does not contain semicolon
                coreSystemService.execDDL(stmt, UserInfoManagerHandler.getUserId(), SpaceInfoUtil.getSpaceId(), dbInfo.getDbId());
            }
            dbTableMapper.update(new UpdateWrapper<DbTable>().lambda()
                    .eq(DbTable::getId, tbId)
                    .set(DbTable::getDeleted, true));
            // Delete table fields
            dbTableFieldMapper.delete(new UpdateWrapper<DbTableField>().lambda()
                    .eq(DbTableField::getTbId, tbId));
        } catch (Exception ex) {
            log.error("Failed to delete table, tbId={}", tbId, ex);
            throw new BusinessException(ResponseEnum.DATABASE_TABLE_DELETE_FAILED);
        }
    }

    public void operateTableData(DbTableOperateDto dbTableOperateDto) {
        dataPermissionCheckTool.checkTbBelong(dbTableOperateDto.getTbId());
        try {
            DbTable dbTable = dbTableMapper.selectById(dbTableOperateDto.getTbId());
            List<DbTableField> fields = dbTableFieldMapper.selectList(new QueryWrapper<DbTableField>().lambda()
                    .eq(DbTableField::getTbId, dbTable.getId()));
            DbInfo dbInfo = dbInfoMapper.selectById(dbTable.getDbId());

            // Validate and execute one by one (can be batched to improve availability)
            final int BATCH = 100; // Adjustable
            List<DbTableDataDto> rows = dbTableOperateDto.getData();
            for (int i = 0; i < rows.size(); i++) {
                DbTableDataDto data = rows.get(i);
                validateParams(data.getTableData(), fields, data.getOperateType());

                String single = buildDml(dbTable.getName(), data.getTableData(), data.getOperateType());
                SqlRenderer.denyMultiStmtOrComment(single);

                coreSystemService.execDML(
                        single,
                        UserInfoManagerHandler.getUserId(),
                        SpaceInfoUtil.getSpaceId(),
                        dbInfo.getDbId(),
                        DBOperateEnum.UPDATE.getCode(),
                        dbTableOperateDto.getExecDev());

                // Simple batch yielding can be done here (e.g., sleep 1ms every BATCH items) to prevent
                // overwhelming the core system
                if ((i + 1) % BATCH == 0) {
                    // Thread.yield(); // Optional
                }
            }
        } catch (Exception ex) {
            log.error("Table operation failed, params={}", JSONObject.toJSONString(dbTableOperateDto), ex);
            throw new BusinessException(ResponseEnum.DATABASE_TABLE_OPERATION_FAILED);
        }
    }

    private void validateParams(Map<String, Object> params, List<DbTableField> fields, Integer operateType) {
        // 1. Get all table field names
        Set<String> fieldNames = fields.stream().map(DbTableField::getName).collect(Collectors.toSet());

        // 2. Validate illegal fields
        for (String paramKey : params.keySet()) {
            if (!fieldNames.contains(paramKey)) {
                log.error("Illegal field: " + paramKey);
                throw new BusinessException(ResponseEnum.DATABASE_TABLE_FIELD_ILLEGAL);
            }
        }

        // 3. Validate required fields
        for (DbTableField field : fields) {
            // Skip system field validation (for insert operations)
            if (operateType.equals(DBOperateEnum.INSERT.getCode()) && Arrays.asList(SYSTEM_FIELDS).contains(field.getName())) {
                continue;
            }
            if (operateType.equals(DBOperateEnum.DELETE.getCode()) || operateType.equals(DBOperateEnum.UPDATE.getCode())) {
                // For delete and update operations, uuid and create_time are not validated
                if (Arrays.asList("uuid", "create_time").contains(field.getName())) {
                    continue;
                }
            }
            // Validate required fields without default values
            if (Boolean.TRUE.equals(field.getIsRequired()) && field.getDefaultValue() == null && !params.containsKey(field.getName())) {
                log.error("Missing required field: " + field.getName());
                throw new BusinessException(ResponseEnum.DATABASE_TABLE_FIELD_LACK);
            }
        }
    }

    private String buildDml(String tableName, Map<String, Object> params, Integer operateType) {
        StringBuilder sql = new StringBuilder();
        String table = SqlRenderer.quoteIdent(tableName);

        if (DBOperateEnum.INSERT.getCode().equals(operateType)) {
            // Filter null
            Map<String, Object> nonNull = params.entrySet()
                    .stream()
                    .filter(e -> e.getValue() != null)
                    .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));

            List<String> cols = new ArrayList<>();
            List<String> vals = new ArrayList<>();
            cols.add(SqlRenderer.quoteIdent("uid"));
            vals.add(SqlRenderer.renderValue(UserInfoManagerHandler.getUserId()));

            for (Map.Entry<String, Object> e : nonNull.entrySet()) {
                cols.add(SqlRenderer.quoteIdent(e.getKey()));
                vals.add(SqlRenderer.renderValue(e.getValue()));
            }
            sql.append("INSERT INTO ")
                    .append(table)
                    .append(" (")
                    .append(String.join(", ", cols))
                    .append(")")
                    .append(" VALUES (")
                    .append(String.join(", ", vals))
                    .append(");");

        } else if (DBOperateEnum.UPDATE.getCode().equals(operateType)) {
            // where id = ?
            long id = SqlRenderer.requireLong(params.get("id"), "id");
            String where = SqlRenderer.quoteIdent("id") + " = " + id;

            String sets = params.entrySet()
                    .stream()
                    .filter(e -> !"id".equals(e.getKey()))
                    .map(e -> SqlRenderer.quoteIdent(e.getKey()) + " = " + SqlRenderer.renderValue(e.getValue()))
                    .collect(Collectors.joining(", "));

            if (StringUtils.isBlank(sets)) {
                throw new IllegalArgumentException("No update columns");
            }
            sql.append("UPDATE ")
                    .append(table)
                    .append(" SET ")
                    .append(sets)
                    .append(" WHERE ")
                    .append(where)
                    .append(";");

        } else if (DBOperateEnum.DELETE.getCode().equals(operateType)) {
            long id = SqlRenderer.requireLong(params.get("id"), "id");
            String where = SqlRenderer.quoteIdent("id") + " = " + id;
            sql.append("DELETE FROM ").append(table).append(" WHERE ").append(where).append(";");
        }

        SqlRenderer.denyMultiStmtOrComment(sql.toString());
        return sql.toString();
    }

    private String buildDmlOld(String tableName, Map<String, Object> params, Integer operateType) {
        StringBuilder sql = new StringBuilder();
        if (DBOperateEnum.INSERT.getCode().equals(operateType)) {
            // System field uuid filling
            params = params.entrySet()
                    .stream()
                    .filter(entry -> entry.getValue() != null)
                    .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
            String columns = " uid ";
            String values = "'" + UserInfoManagerHandler.getUserId() + "'";
            if (!params.isEmpty()) {
                columns = columns.concat(",").concat(String.join(", ", params.keySet()));
                values = values + "," + params.values()
                        .stream()
                        .map(value -> value instanceof String ? "'" + value + "'" : value.toString())
                        .collect(Collectors.joining(", "));
            }
            sql.append("INSERT INTO ").append(tableName).append(" (").append(columns).append(") VALUES (").append(values).append("); ");
        } else if (DBOperateEnum.UPDATE.getCode().equals(operateType)) {
            String condition = "id = " + params.get("id");
            String updates = params.entrySet()
                    .stream()
                    .filter(entry -> !"id".equals(entry.getKey())) // Filter out entries with key "id"
                    .map(entry -> entry.getKey() + " = " +
                            (entry.getValue() instanceof String ? "'" + entry.getValue() + "'" : entry.getValue()))
                    .collect(Collectors.joining(", "));
            sql.append("UPDATE ").append(tableName).append(" SET ").append(updates).append(" WHERE ").append(condition).append("; ");
        } else if (DBOperateEnum.DELETE.getCode().equals(operateType)) {
            String condition = "id = " + params.get("id");
            sql.append("DELETE FROM ").append(tableName).append(" WHERE ").append(condition).append("; ");
        }
        return sql.toString();
    }

    public void getTableTemplateFile(HttpServletResponse response, Long tbId) {
        dataPermissionCheckTool.checkTbBelong(tbId);
        try {
            // Build a template Excel file
            DbTable dbTable = dbTableMapper.selectById(tbId);
            List<DbTableField> fields = dbTableFieldMapper.selectList(new QueryWrapper<DbTableField>().lambda()
                    .eq(DbTableField::getTbId, tbId)
                    .orderByAsc(DbTableField::getCreateTime));

            response.setContentType("application/vnd.ms-excel");
            response.setCharacterEncoding("utf-8");
            String fileName = URLEncoder.encode(dbTable.getName(), "UTF-8");
            response.setHeader("Content-Disposition", "attachment; filename=" + fileName + ".xlsx");

            List<List<String>> head = new ArrayList<>();
            for (DbTableField field : fields) {
                // The header is the field name
                if (Arrays.asList(SYSTEM_FIELDS).contains(field.getName())) {
                    continue;
                }
                head.add(Collections.singletonList(field.getName()));
            }

            // Generate a file stream using EasyExcel, writing only the header row
            EasyExcel.write(response.getOutputStream())
                    .head(head)
                    .sheet("模版")
                    .doWrite(new ArrayList<>());

        } catch (Exception ex) {
            log.error("Template generation failed, tbId={}", tbId, ex);
            throw new BusinessException(ResponseEnum.DATABASE_TEMPLATE_GENERATE_FAILED);
        }

    }

    public Page<JSONObject> selectTableData(DbTableSelectDataDto dto) {
        dataPermissionCheckTool.checkTbBelong(dto.getTbId());
        try {
            Page<JSONObject> page = new Page<>(dto.getPageNum(), dto.getPageSize());
            page.setSize(Math.min(page.getSize(), MAX_PAGE_SIZE));

            DbTable dbTable = dbTableMapper.selectById(dto.getTbId());
            DbInfo dbInfo = dbInfoMapper.selectById(dbTable.getDbId());

            String table = SqlRenderer.quoteIdent(dbTable.getName());
            long limit = page.getSize();
            long offset = (page.getCurrent() - 1) * page.getSize();
            if (limit < 0 || offset < 0)
                throw new IllegalArgumentException("Bad paging");

            String dml = "SELECT * FROM " + table + " ORDER BY " +
                    SqlRenderer.quoteIdent("create_time") + " DESC, " + SqlRenderer.quoteIdent("id") + " DESC" +
                    " LIMIT " + limit + " OFFSET " + offset;
            SqlRenderer.denyMultiStmtOrComment(dml);

            List<JSONObject> maps = (List<JSONObject>) coreSystemService.execDML(
                    dml,
                    UserInfoManagerHandler.getUserId(),
                    SpaceInfoUtil.getSpaceId(),
                    dbInfo.getDbId(),
                    DBOperateEnum.SELECT.getCode(),
                    dto.getExecDev());

            String countDml = "SELECT COUNT(*) FROM " + table;
            Long total = (Long) coreSystemService.execDML(
                    countDml,
                    UserInfoManagerHandler.getUserId(),
                    SpaceInfoUtil.getSpaceId(),
                    dbInfo.getDbId(),
                    DBOperateEnum.SELECT_TOTAL_COUNT.getCode(),
                    dto.getExecDev());

            page.setTotal(total == null ? 0 : total);
            page.setRecords(maps);
            return page;
        } catch (Exception ex) {
            log.error("Failed to query table data, params={}", JSONObject.toJSONString(dto), ex);
            throw new BusinessException(ResponseEnum.DATABASE_TABLE_QUERY_DATA_FAILED);
        }
    }


    public void importTableData(Long tbId, Integer execDev, MultipartFile file) {
        dataPermissionCheckTool.checkTbBelong(tbId);
        try {
            DbTable dbTable = dbTableMapper.selectById(tbId);
            DbInfo dbInfo = dbInfoMapper.selectById(dbTable.getDbId());

            List<DbTableField> dbTableFields = dbTableFieldMapper.selectList(new QueryWrapper<DbTableField>().lambda()
                    .eq(DbTableField::getTbId, tbId)
                    .orderByDesc(DbTableField::getCreateTime));

            // 1) read Excel -> rows
            List<Map<String, Object>> rows = new ArrayList<>();
            DBExcelReadListener listener = new DBExcelReadListener(
                    dbTableFields,
                    rows,
                    UserInfoManagerHandler.getUserId(),
                    10_000);
            EasyExcel.read(file.getInputStream(), listener).sheet().doRead();

            // 2) build INSERT (Bind parameters), shard execution + retry + error collection
            final int CHUNK = 200, MAX_RETRIES = 3;
            JooqBatchExecutor.ResultSummary summary = JooqBatchExecutor.executeInChunks(
                    dslCon,
                    dbTable.getName(),
                    rows,
                    CHUNK,
                    MAX_RETRIES,
                    row -> {
                        Table<?> t = table(name(dbTable.getName()));
                        InsertSetMoreStep<?> step = dslCon.insertInto(t).set(field(name("uid")), row.get("uid"));
                        for (Map.Entry<String, Object> e : row.entrySet()) {
                            if ("uid".equals(e.getKey()))
                                continue;
                            step = ((InsertSetStep<?>) step).set(field(name(e.getKey())), e.getValue());
                        }
                        return (Query) step;
                    },

                    (sql, paramsIgnored) -> {
                        // Single statement security check (semicolons at the end are allowed, but multiple internal
                        // statements are rejected)
                        SqlRenderer.denyMultiStmtOrComment(sql);
                        coreSystemService.execDML(
                                sql,
                                UserInfoManagerHandler.getUserId(),
                                SpaceInfoUtil.getSpaceId(),
                                dbInfo.getDbId(),
                                DBOperateEnum.INSERT.getCode(),
                                execDev);
                    });

            // 3) Summary
            if (!summary.errors.isEmpty()) {
                // Record the first 10 failed examples
                StringBuilder sb = new StringBuilder();
                sb.append("导入部分失败：success=")
                        .append(summary.success)
                        .append(", failed=")
                        .append(summary.failed)
                        .append(". 失败样例：");
                summary.errors.stream().limit(10).forEach(err -> sb.append("\n#").append(err.index).append(" : ").append(err.message));
                log.warn("importTableData partial failures: {}", sb);
                throw new BusinessException(ResponseEnum.DATABASE_IMPORT_FAILED);
            }
        } catch (Exception ex) {
            log.error("import data failed, tbId={}, execDev={}, fileName={}", tbId, execDev, file.getOriginalFilename(), ex);
            throw new BusinessException(ResponseEnum.DATABASE_IMPORT_FAILED);
        }
    }

    @Transactional
    public void copyTable(Long tbId) {
        try {
            DbTable dbTable = dbTableMapper.selectById(tbId);
            // Unify and standardize the copy names, and avoid illegal characters
            String tableName = NamePolicy.copyName(dbTable.getName());

            // build DDL：CREATE TABLE new AS SELECT * FROM old;
            DbTableDto dbTableDto = new DbTableDto();
            dbTableDto.setName(tableName);
            String ddl = buildDDL(dbTableDto, DBOperateEnum.COPY.getCode(), dbTable.getName());

            DbInfo dbInfo = dbInfoMapper.selectById(dbTable.getDbId());

            for (String stmt : safeSplitStatements(ddl)) {
                SqlRenderer.denyMultiStmtOrComment(stmt); // At this point each statement does not contain semicolon
                coreSystemService.execDDL(stmt,
                        UserInfoManagerHandler.getUserId(),
                        SpaceInfoUtil.getSpaceId(),
                        dbInfo.getDbId());
            }

            DbTable copyTable = new DbTable();
            copyTable.setName(tableName);
            copyTable.setDbId(dbTable.getDbId());
            copyTable.setDescription(dbTable.getDescription());
            copyTable.setCreateTime(new Date());
            copyTable.setUpdateTime(new Date());
            dbTableMapper.insert(copyTable);

            List<DbTableField> dbTableFields = dbTableFieldMapper.selectList(new QueryWrapper<DbTableField>().lambda()
                    .eq(DbTableField::getTbId, tbId));
            List<DbTableField> copyTableFields = new ArrayList<>();
            for (DbTableField dbTableField : dbTableFields) {
                DbTableField copyTableField = new DbTableField();
                BeanUtils.copyProperties(dbTableField, copyTableField);
                copyTableField.setTbId(copyTable.getId());
                copyTableField.setCreateTime(new Date());
                copyTableField.setUpdateTime(new Date());
                copyTableFields.add(copyTableField);
            }
            dbTableFieldMapper.insertBatch(copyTableFields);
        } catch (Exception ex) {
            log.error("copy table failed, tbId={}", tbId, ex);
            throw new BusinessException(ResponseEnum.DATABASE_TABLE_COPY_FAILED);
        }
    }

    public void exportTableData(DatabaseExportDto dto, HttpServletResponse response) {
        dataPermissionCheckTool.checkTbBelong(dto.getTbId());
        try {
            DbTable dbTable = dbTableMapper.selectById(dto.getTbId());
            DbInfo dbInfo = dbInfoMapper.selectById(dbTable.getDbId());

            String table = SqlRenderer.quoteIdent(dbTable.getName());
            String dml = "SELECT * FROM " + table + " LIMIT 1000 OFFSET 0";

            if (dto.getDataIds() != null && !dto.getDataIds().isEmpty()) {
                if (dto.getDataIds().size() > MAX_EXPORT_IDS) {
                    throw new BusinessException(ResponseEnum.DATABASE_TOO_MANY_EXPORT_IDS);
                }
                // All perform digital whitelist verification
                List<Long> ids = dto.getDataIds()
                        .stream()
                        .map(x -> SqlRenderer.requireLong(x, "id"))
                        .collect(Collectors.toList());
                String in = ids.stream().map(String::valueOf).collect(Collectors.joining(","));
                dml = "SELECT * FROM " + table + " WHERE " + SqlRenderer.quoteIdent("id") + " IN (" + in + ")";
            }
            SqlRenderer.denyMultiStmtOrComment(dml);

            List<JSONObject> data = (List<JSONObject>) coreSystemService.execDML(
                    dml,
                    UserInfoManagerHandler.getUserId(),
                    SpaceInfoUtil.getSpaceId(),
                    dbInfo.getDbId(),
                    DBOperateEnum.SELECT.getCode(),
                    dto.getExecDev());

            List<List<String>> headList = new ArrayList<>();
            dbTableFieldMapper.selectList(new QueryWrapper<DbTableField>().lambda()
                    .eq(DbTableField::getTbId, dto.getTbId()))
                    .forEach(field -> headList.add(Collections.singletonList(field.getName())));

            List<List<Object>> dataList = new ArrayList<>();
            for (JSONObject row : data) {
                List<Object> line = new ArrayList<>();
                for (List<String> h : headList) {
                    Object val = row.get(h.get(0));
                    line.add(val != null ? val : "");
                }
                dataList.add(line);
            }

            response.setContentType(ExcelUtil.XLSX_CONTENT_TYPE);
            response.setCharacterEncoding("utf-8");
            String fileName = URLEncoder.encode(dbTable.getName(), "UTF-8").replaceAll("\\+", "%20");
            response.setHeader("Content-disposition", "attachment;filename=" + fileName + ".xlsx");

            EasyExcel.write(response.getOutputStream())
                    .head(headList)
                    .sheet("data")
                    .doWrite(dataList);
        } catch (Exception ex) {
            log.error("export data failed, params:{}", dto, ex);
            throw new BusinessException(ResponseEnum.DATABASE_TABLE_EXPORT_FAILED);
        }
    }

    public List<DbTableInfoVo> getDbTableInfoList() {
        List<DbTableInfoVo> result = new ArrayList<>();
        dbInfoMapper.selectList(new QueryWrapper<DbInfo>().lambda()
                .and(SpaceInfoUtil.getSpaceId() == null,
                        wrapper -> wrapper.eq(DbInfo::getUid, UserInfoManagerHandler.getUserId())
                                .isNull(DbInfo::getSpaceId))
                .eq(SpaceInfoUtil.getSpaceId() != null, DbInfo::getSpaceId, SpaceInfoUtil.getSpaceId())
                .eq(DbInfo::getDeleted, false))
                .forEach(dbInfo -> {
                    DbTableInfoVo dbTableInfoVo = new DbTableInfoVo();
                    dbTableInfoVo.setLabel(dbInfo.getName());
                    dbTableInfoVo.setValue(dbInfo.getDbId().toString());
                    List<DbTable> dbTables = dbTableMapper.selectList(new QueryWrapper<DbTable>().lambda()
                            .eq(DbTable::getDbId, dbInfo.getId())
                            .eq(DbTable::getDeleted, false));
                    List<DbTableInfoVo> children = new ArrayList<>();
                    dbTables.forEach(dbTable -> {
                        DbTableInfoVo child = new DbTableInfoVo();
                        child.setLabel(dbTable.getName());
                        child.setValue(dbTable.getId().toString());
                        children.add(child);
                    });
                    dbTableInfoVo.setChildren(children);
                    result.add(dbTableInfoVo);
                });
        return result;
    }

    public DbInfo getDatabaseInfo(Long id) {
        dataPermissionCheckTool.checkDbBelong(id);
        return dbInfoMapper.selectById(id);
    }

    public List<DbTableFieldDto> importDbTableField(MultipartFile file) {
        try {
            // read file
            List<DbTableFieldDto> fields = new ArrayList<>();
            DBTableExcelReadListener listener = new DBTableExcelReadListener(fields);

            // read Excel
            EasyExcel.read(file.getInputStream(), listener)
                    .sheet()
                    .doRead();

            return fields;
        } catch (Exception ex) {
            log.error("Failed to import database table fields", ex);
            throw new BusinessException(ResponseEnum.DATABASE_IMPORT_FAILED);
        }
    }

    public static List<String> safeSplitStatements(String sql) {
        List<String> out = new ArrayList<>();
        StringBuilder cur = new StringBuilder();
        boolean inSingle = false;
        for (int i = 0; i < sql.length(); i++) {
            char c = sql.charAt(i);
            if (c == '\'') {
                if (inSingle && i + 1 < sql.length() && sql.charAt(i + 1) == '\'') {
                    cur.append("''");
                    i++;
                    continue;
                }
                inSingle = !inSingle;
                cur.append(c);
                continue;
            }
            if (c == ';' && !inSingle) {
                String stmt = cur.toString().trim();
                if (!stmt.isEmpty())
                    out.add(stmt);
                cur.setLength(0);
            } else {
                cur.append(c);
            }
        }
        String last = cur.toString().trim();
        if (!last.isEmpty())
            out.add(last);
        return out;
    }
}
