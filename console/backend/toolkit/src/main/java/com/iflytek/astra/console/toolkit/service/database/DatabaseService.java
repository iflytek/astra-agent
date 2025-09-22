package com.iflytek.astra.console.toolkit.service.database;

import cn.hutool.poi.excel.ExcelUtil;
import com.alibaba.excel.EasyExcel;
import com.alibaba.fastjson2.JSONObject;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;

import com.iflytek.astra.console.commons.util.space.SpaceInfoUtil;
import com.iflytek.astra.console.commons.constant.ResponseEnum;
import com.iflytek.astra.console.commons.exception.BusinessException;
import com.iflytek.astra.console.toolkit.common.constant.CommonConst;
import com.iflytek.astra.console.toolkit.config.jooq.JooqBatchExecutor;
import com.iflytek.astra.console.toolkit.config.properties.CommonConfig;
import com.iflytek.astra.console.toolkit.entity.dto.database.*;
import com.iflytek.astra.console.toolkit.entity.enumVo.DBOperateEnum;
import com.iflytek.astra.console.toolkit.entity.table.database.*;
import com.iflytek.astra.console.toolkit.entity.table.relation.FlowDbRel;
import com.iflytek.astra.console.toolkit.entity.vo.database.*;
import com.iflytek.astra.console.toolkit.handler.UserInfoManagerHandler;
import com.iflytek.astra.console.toolkit.mapper.ConfigInfoMapper;
import com.iflytek.astra.console.toolkit.mapper.database.*;
import com.iflytek.astra.console.toolkit.mapper.relation.FlowDbRelMapper;
import com.iflytek.astra.console.toolkit.service.extra.CoreSystemService;
import com.iflytek.astra.console.toolkit.tool.DataPermissionCheckTool;
import com.iflytek.astra.console.toolkit.util.S3Util;
import com.iflytek.astra.console.toolkit.util.database.NamePolicy;
import com.iflytek.astra.console.toolkit.util.database.SqlRenderer;
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
 * 服务实现类
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
    // DatabaseService 内新增
    private static final int MAX_PAGE_SIZE = 1000; // 防炸
    private static final int MAX_EXPORT_IDS = 1000; // IN 上限

    @Transactional
    public DbInfo create(DatabaseDto databaseDto) {
        try {
            // 必填项校验
            if (!StringUtils.isNotBlank(databaseDto.getName())) {
                throw new BusinessException(ResponseEnum.DATABASE_NAME_NOT_EMPTY);
            }
            String userId = Objects.requireNonNull(UserInfoManagerHandler.getUserId()).toString();
            Long spaceId = SpaceInfoUtil.getSpaceId();
            // 同名校验
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
            // 调用核心系统创建数据库
            Long dbId = coreSystemService.createDatabase(databaseDto.getName(), userId, spaceId, databaseDto.getDescription());
            // 保存记录
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
            log.info("创建数据库失败,params:{}", databaseDto.toString(), ex);
            throw new BusinessException(ResponseEnum.DATABASE_CREATE_FAILED);
        }
    }

    @Transactional
    public void updateDateBase(DatabaseDto databaseDto) {
        try {
            dataPermissionCheckTool.checkDbUpdateBelong(databaseDto.getId());
            // 名称校验
            DbInfo dbInfo = dbInfoMapper.selectById(databaseDto.getId());
            if (StringUtils.isNotBlank(databaseDto.getDescription())) {
                if (!databaseDto.getDescription().equals(dbInfo.getDescription())) {
                    coreSystemService.modifyDataBase(dbInfo.getDbId(), UserInfoManagerHandler.getUserId(), databaseDto.getDescription());
                }
                dbInfo.setDescription(databaseDto.getDescription());
            }
            dbInfoMapper.updateById(dbInfo);
        } catch (Exception ex) {
            log.error("更新数据库失败,params={}", JSONObject.toJSONString(databaseDto), ex);
            throw new BusinessException(ResponseEnum.DATABASE_UPDATE_FAILED);
        }
    }

    public void delete(Long id) {
        try {
            // 判断该数据库是否被引用
            dataPermissionCheckTool.checkDbUpdateBelong(id);
            DbInfo dbInfo = dbInfoMapper.selectById(id);
            Long count = flowDbRelMapper.selectCount(new QueryWrapper<FlowDbRel>().lambda()
                            .eq(FlowDbRel::getDbId, dbInfo.getDbId()));
            if (count > 0) {
                throw new BusinessException(ResponseEnum.DATABASE_DELETE_FAILED_CITED);
            }
            // 核心系统侧删除
            coreSystemService.dropDataBase(dbInfo.getDbId(), UserInfoManagerHandler.getUserId());
            dbInfo.setDeleted(true);
            dbInfoMapper.updateById(dbInfo);
        } catch (Exception ex) {
            log.error("删除数据库失败,dbId={}", id, ex);
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
            // 构建ddl
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
                                // 表字段创建
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
            // 调用核心系统创建数据库
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
            log.error("查询数据库列表失败,params={}", JSONObject.toJSONString(databaseDto), ex);
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
            // 表数量限制
            Long tableCount = dbTableMapper.selectCount(new QueryWrapper<DbTable>().lambda()
                            .eq(DbTable::getDbId, dbInfo.getDbId())
                            .eq(DbTable::getDeleted, false));
            if (tableCount > 20) {
                throw new BusinessException(ResponseEnum.DATABASE_COUNT_LIMITED);
            }
            // 表名重复校验
            Long count = dbTableMapper.selectCount(new QueryWrapper<DbTable>().lambda()
                            .eq(DbTable::getName, dbTableDto.getName())
                            .eq(DbTable::getDbId, dbInfo.getDbId())
                            .eq(DbTable::getDeleted, false));
            if (count > 0) {
                throw new BusinessException(ResponseEnum.DATABASE_TABLE_NAME_EXIST);
            }
            // 构建建表ddl语句系统字段必填校验
            if (dbTableDto.getFields() == null || dbTableDto.getFields().isEmpty()) {
                throw new BusinessException(ResponseEnum.DATABASE_TABLE_FIELD_CANNOT_EMPTY);
            }
            // 表字段不超过20个
            if (dbTableDto.getFields().size() > 20) {
                throw new BusinessException(ResponseEnum.DATABASE_FIELD_CANNOT_BEYOND_20);
            }
            // 信息保存
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
            // 核心侧保存
            String ddl = buildDDL(dbTableDto, DBOperateEnum.INSERT.getCode(), null);
            // 调用核心系统创建表
            for (String stmt : safeSplitStatements(ddl)) {
                SqlRenderer.denyMultiStmtOrComment(stmt); // 这时每一条都不包含分号
                coreSystemService.execDDL(stmt, UserInfoManagerHandler.getUserId(), SpaceInfoUtil.getSpaceId(), dbInfo.getDbId());
            }
        } catch (Exception ex) {
            log.error("创建表失败,params={}", dbTableDto, ex);
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
            log.error("获取表列表失败,dbId={}", dbId, ex);
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
            log.error("获取表字段列表失败,params={}", JSONObject.toJSONString(dataBaseSearchVo), ex);
            throw new BusinessException(ResponseEnum.DATABASE_TABLE_QUERY_FIELD_FAILED);
        }

    }

    @Transactional
    public void updateTable(DbTableDto dbTableDto) {
        try {
            dataPermissionCheckTool.checkTbBelong(dbTableDto.getId());
            // 更新表结构
            DbTable dbTable = dbTableMapper.selectById(dbTableDto.getId());
            String originName = dbTable.getName();
            // 过滤系统字段id,uid,create_time
            List<String> allowedNames = Arrays.asList(SYSTEM_FIELDS);
            if (dbTableDto.getFields() != null && !dbTableDto.getFields().isEmpty()) {
                // 过滤掉系统字段
                dbTableDto.setFields(dbTableDto.getFields()
                                .stream()
                                .filter(field -> !allowedNames.contains(field.getName()))
                                .peek(field -> {
                                    // 设置默认值
                                    if (StringUtils.isBlank(field.getDefaultValue())) {
                                        field.setDefaultValue(transFormDefaultValue(field.getType()).toString());
                                    }
                                })
                                .collect(Collectors.toList()));
            }
            // 更新核心系统侧
            String ddl = buildDDL(dbTableDto, DBOperateEnum.UPDATE.getCode(), originName);
            if (!dbTable.getName().equals(dbTableDto.getName())) {
                // 判断表名是否已存在
                Long count = dbTableMapper.selectCount(new QueryWrapper<DbTable>().lambda()
                                .eq(DbTable::getName, dbTableDto.getName())
                                .eq(DbTable::getDbId, dbTable.getDbId())
                                .ne(DbTable::getId, dbTableDto.getId())
                                .eq(DbTable::getDeleted, false));
                if (count > 0) {
                    throw new BusinessException(ResponseEnum.DATABASE_TABLE_NAME_EXIST);
                }
            }
            // 查询表字段数量
            Long fieldCount = dbTableFieldMapper.selectCount(new QueryWrapper<DbTableField>().lambda()
                            .eq(DbTableField::getTbId, dbTable.getId()));
            // 统计新增字段数量，删除数量
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
            log.info("更新表失败,params={}", dbTableDto.toString(), ex);
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
                // 默认值
                if (StringUtils.isNotBlank(field.getDefaultValue())) {
                    ddl.append(" DEFAULT ").append(SqlRenderer.renderValue(adaptDefault(field)));
                }
            }
            ddl.append("\n);");

            // 表/列注释
            if (StringUtils.isNotBlank(dbTableDto.getDescription())) {
                ddl.append("\nCOMMENT ON TABLE ")
                                .append(table)
                                .append(" IS ")
                                .append(SqlRenderer.quoteLiteral(dbTableDto.getDescription()))
                                .append(";");
            }
            ddl.append("\nCOMMENT ON COLUMN ").append(table).append(".").append(SqlRenderer.quoteIdent("id")).append(" IS '主键id';");
            ddl.append("\nCOMMENT ON COLUMN ").append(table).append(".").append(SqlRenderer.quoteIdent("uid")).append(" IS 'uid';");
            ddl.append("\nCOMMENT ON COLUMN ").append(table).append(".").append(SqlRenderer.quoteIdent("create_time")).append(" IS '创建时间';");

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

            // 操作按类型排序（DELETE -> UPDATE -> INSERT），避免依赖问题
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



    // 新增字段
    // 新增字段
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

    // 删除字段
    public static String buildDropColumnSql(String tableName, String columnName) {
        String table = SqlRenderer.quoteIdent(tableName);
        String col = SqlRenderer.quoteIdent(columnName);
        String sql = "ALTER TABLE " + table + " DROP COLUMN IF EXISTS " + col + ";";
        SqlRenderer.denyMultiStmtOrComment(sql);
        return sql;
    }

    // 编辑字段
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

        // 注释
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

    // 删除字段
    // public static String buildDropColumnSql(String tableName, String columnName) {
    // return "ALTER TABLE " + tableName + " DROP COLUMN IF EXISTS " + columnName + ";";
    // }

    /** 结合字段类型把 defaultValue 转成更“正确”的 Java 类型，随后交给 renderValue 渲染 */
    private Object adaptDefault(DbTableFieldDto field) {
        String t = StringUtils.lowerCase(field.getType());
        String v = field.getDefaultValue();
        if (v == null)
            return null;
        switch (t) {
            case CommonConst.DBFieldType.TIME: // "yyyy-MM-dd HH:mm:ss"
                return v; // 按字符串字面量处理，渲染为 '...'
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
                return v; // 其他当字符串
        }
    }

    // 编辑字段
    public String buildModifyColumnSqlOld(String tableName, DbTableFieldDto field) {

        List<String> alterClauses = new ArrayList<>();
        String renameClause = null;
        StringBuilder commentSql = new StringBuilder();

        // 判断是否修改名称
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

        // 设置默认值，只有不同才设置
        if (!field.getDefaultValue().equals(dbTableField.getDefaultValue())) {
            if (CommonConst.DBFieldType.STRING.equalsIgnoreCase(field.getType()) || CommonConst.DBFieldType.TIME.equalsIgnoreCase(field.getType())) {
                alterClauses.add(String.format("ALTER COLUMN %s SET DEFAULT '%s'", colNameToUse, field.getDefaultValue()));
            } else {
                alterClauses.add(String.format(String.format("ALTER COLUMN %s SET DEFAULT %s", colNameToUse, field.getDefaultValue())));
            }
        }
        // 拼接 ALTER TABLE 语句
        StringBuilder sql = new StringBuilder();
        if (renameClause != null) {
            sql.append(String.format("ALTER TABLE %s ", tableName));
            sql.append(renameClause).append("; ");
        }
        sql.append(String.format("ALTER TABLE %s ", tableName));
        sql.append(String.join(", ", alterClauses));
        sql.append(";");


        // 判断注释是否变化，拼接 COMMENT 语句
        if (StringUtils.isNotBlank(field.getDescription())) {
            // 设置或修改注释，只有变化才执行
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
            // 核心系统侧删除
            for (String stmt : safeSplitStatements(ddl)) {
                SqlRenderer.denyMultiStmtOrComment(stmt); // 这时每一条都不包含分号
                coreSystemService.execDDL(stmt, UserInfoManagerHandler.getUserId(), SpaceInfoUtil.getSpaceId(), dbInfo.getDbId());
            }
            dbTableMapper.update(new UpdateWrapper<DbTable>().lambda()
                            .eq(DbTable::getId, tbId)
                            .set(DbTable::getDeleted, true));
            // 删除表字段
            dbTableFieldMapper.delete(new UpdateWrapper<DbTableField>().lambda()
                            .eq(DbTableField::getTbId, tbId));
        } catch (Exception ex) {
            log.error("删除表失败,tbId={}", tbId, ex);
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

            // 逐条校验 + 逐条执行（可分批，以提高可用性）
            final int BATCH = 100; // 可调
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

                // 可在这里做简单的批次让步（如每 BATCH 条 sleep 1ms），防止压垮核心系统
                if ((i + 1) % BATCH == 0) {
                    // Thread.yield(); // 可选
                }
            }
        } catch (Exception ex) {
            log.error("表操作失败,params={}", JSONObject.toJSONString(dbTableOperateDto), ex);
            throw new BusinessException(ResponseEnum.DATABASE_TABLE_OPERATION_FAILED);
        }
    }

    private void validateParams(Map<String, Object> params, List<DbTableField> fields, Integer operateType) {
        // 1. 获取所有表字段名称
        Set<String> fieldNames = fields.stream().map(DbTableField::getName).collect(Collectors.toSet());

        // 2. 校验非法字段
        for (String paramKey : params.keySet()) {
            if (!fieldNames.contains(paramKey)) {
                log.error("非法字段: " + paramKey);
                throw new BusinessException(ResponseEnum.DATABASE_TABLE_FIELD_ILLEGAL);
            }
        }

        // 3. 校验必填字段
        for (DbTableField field : fields) {
            // 跳过系统字段校验（新增操作时）
            if (operateType.equals(DBOperateEnum.INSERT.getCode()) && Arrays.asList(SYSTEM_FIELDS).contains(field.getName())) {
                continue;
            }
            if (operateType.equals(DBOperateEnum.DELETE.getCode()) || operateType.equals(DBOperateEnum.UPDATE.getCode())) {
                // 对于删除和更新操作，uuid和create_time不校验
                if (Arrays.asList("uuid", "create_time").contains(field.getName())) {
                    continue;
                }
            }
            // 校验必填且没有默认值的
            if (Boolean.TRUE.equals(field.getIsRequired()) && field.getDefaultValue() == null && !params.containsKey(field.getName())) {
                log.error("缺少必填字段: " + field.getName());
                throw new BusinessException(ResponseEnum.DATABASE_TABLE_FIELD_LACK);
            }
        }
    }

    private String buildDml(String tableName, Map<String, Object> params, Integer operateType) {
        StringBuilder sql = new StringBuilder();
        String table = SqlRenderer.quoteIdent(tableName);

        if (DBOperateEnum.INSERT.getCode().equals(operateType)) {
            // 过滤 null
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
            // 系统字段uuid填充
            // 过滤为空的值
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
            // 构建update语句
            String condition = "id = " + params.get("id");
            String updates = params.entrySet()
                            .stream()
                            .filter(entry -> !"id".equals(entry.getKey())) // 过滤掉 key 为 "id" 的 entry
                            .map(entry -> entry.getKey() + " = " +
                                            (entry.getValue() instanceof String ? "'" + entry.getValue() + "'" : entry.getValue()))
                            .collect(Collectors.joining(", "));
            sql.append("UPDATE ").append(tableName).append(" SET ").append(updates).append(" WHERE ").append(condition).append("; ");
        } else if (DBOperateEnum.DELETE.getCode().equals(operateType)) {
            // 构建delete语句
            String condition = "id = " + params.get("id");
            sql.append("DELETE FROM ").append(tableName).append(" WHERE ").append(condition).append("; ");
        }
        return sql.toString();
    }

    public void getTableTemplateFile(HttpServletResponse response, Long tbId) {
        dataPermissionCheckTool.checkTbBelong(tbId);
        try {
            // 构建模版excel文件
            DbTable dbTable = dbTableMapper.selectById(tbId);
            List<DbTableField> fields = dbTableFieldMapper.selectList(new QueryWrapper<DbTableField>().lambda()
                            .eq(DbTableField::getTbId, tbId)
                            .orderByAsc(DbTableField::getCreateTime));
            // 设置响应头，支持文件下载
            response.setContentType("application/vnd.ms-excel");
            response.setCharacterEncoding("utf-8");
            String fileName = URLEncoder.encode(dbTable.getName(), "UTF-8");
            response.setHeader("Content-Disposition", "attachment; filename=" + fileName + ".xlsx");

            // 模拟数据
            List<List<String>> head = new ArrayList<>();
            for (DbTableField field : fields) {
                // 表头为字段名称
                if (Arrays.asList(SYSTEM_FIELDS).contains(field.getName())) {
                    continue;
                }
                head.add(Collections.singletonList(field.getName()));
            }

            // 使用 EasyExcel 生成文件流，仅写入表头
            EasyExcel.write(response.getOutputStream())
                            .head(head)
                            .sheet("模版")
                            .doWrite(new ArrayList<>()); // 空数据

        } catch (Exception ex) {
            log.error("模版生成失败, tbId={}", tbId, ex);
            throw new BusinessException(ResponseEnum.DATABASE_TEMPLATE_GENERATE_FAILED);
        }

    }

    public Page<JSONObject> selectTableData(DbTableSelectDataDto dto) {
        dataPermissionCheckTool.checkTbBelong(dto.getTbId());
        try {
            Page<JSONObject> page = new Page<>(dto.getPageNum(), dto.getPageSize());
            page.setSize(Math.min(page.getSize(), MAX_PAGE_SIZE)); // 上限

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
            log.error("查询表数据失败,params={}", JSONObject.toJSONString(dto), ex);
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

            // 1) 读 Excel -> rows
            List<Map<String, Object>> rows = new ArrayList<>();
            DBExcelReadListener listener = new DBExcelReadListener(
                            dbTableFields,
                            rows,
                            UserInfoManagerHandler.getUserId(),
                            10_000);
            EasyExcel.read(file.getInputStream(), listener).sheet().doRead();

            // 2) 构建 INSERT（绑定参数），分片执行 + 重试 + 错误收集
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
                            // ★ 这里收到的 sql 已经是 INLINED 的完整语句
                            (sql, paramsIgnored) -> {
                                // 单条语句安全检查（允许末尾 ;，但拒绝内部多条）
                                SqlRenderer.denyMultiStmtOrComment(sql);
                                coreSystemService.execDML(
                                                sql,
                                                UserInfoManagerHandler.getUserId(),
                                                SpaceInfoUtil.getSpaceId(),
                                                dbInfo.getDbId(),
                                                DBOperateEnum.INSERT.getCode(),
                                                execDev);
                            });

            // 3) 汇总
            if (!summary.errors.isEmpty()) {
                // 记录前 10 条失败样例
                StringBuilder sb = new StringBuilder();
                sb.append("导入部分失败：success=")
                                .append(summary.success)
                                .append(", failed=")
                                .append(summary.failed)
                                .append(". 失败样例：");
                summary.errors.stream().limit(10).forEach(err -> sb.append("\n#").append(err.index).append(" : ").append(err.message));
                log.warn("importTableData partial failures: {}", sb);
                // 业务策略：若允许“部分成功”，这里可不抛；若要求“全成功”，这里抛出
                throw new BusinessException(ResponseEnum.DATABASE_IMPORT_FAILED);
            }
        } catch (Exception ex) {
            log.error("导入数据失败, tbId={}, execDev={}, fileName={}", tbId, execDev, file.getOriginalFilename(), ex);
            throw new BusinessException(ResponseEnum.DATABASE_IMPORT_FAILED);
        }
    }

    @Transactional
    public void copyTable(Long tbId) {
        try {
            DbTable dbTable = dbTableMapper.selectById(tbId);
            // 统一规范副本名称，避免非法字符
            String tableName = NamePolicy.copyName(dbTable.getName());

            // 构建 DDL：CREATE TABLE new AS SELECT * FROM old;
            DbTableDto dbTableDto = new DbTableDto();
            dbTableDto.setName(tableName);
            String ddl = buildDDL(dbTableDto, DBOperateEnum.COPY.getCode(), dbTable.getName());

            DbInfo dbInfo = dbInfoMapper.selectById(dbTable.getDbId());

            for (String stmt : safeSplitStatements(ddl)) {
                SqlRenderer.denyMultiStmtOrComment(stmt); // 这时每一条都不包含分号
                coreSystemService.execDDL(stmt,
                                UserInfoManagerHandler.getUserId(),
                                SpaceInfoUtil.getSpaceId(),
                                dbInfo.getDbId());
            }
            // 本地元数据保存
            DbTable copyTable = new DbTable();
            copyTable.setName(tableName);
            copyTable.setDbId(dbTable.getDbId());
            copyTable.setDescription(dbTable.getDescription());
            copyTable.setCreateTime(new Date());
            copyTable.setUpdateTime(new Date());
            dbTableMapper.insert(copyTable);

            // 复制表字段
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
            log.error("复制表失败, tbId={}", tbId, ex);
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
                // 全部做数字白名单校验
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

            // 组装表头与数据（与原逻辑一致）
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
            log.error("导出表数据失败, params:{}", dto, ex);
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
            // 读取文件内容
            List<DbTableFieldDto> fields = new ArrayList<>();
            DBTableExcelReadListener listener = new DBTableExcelReadListener(fields);

            // 读取 Excel
            EasyExcel.read(file.getInputStream(), listener)
                            .sheet()
                            .doRead();
            // 调用核心系统接口
            return fields;
        } catch (Exception ex) {
            log.error("数据库表字段导入失败", ex);
            throw new BusinessException(ResponseEnum.DATABASE_IMPORT_FAILED);
        }
    }

    public static List<String> safeSplitStatements(String sql) {
        List<String> out = new ArrayList<>();
        StringBuilder cur = new StringBuilder();
        boolean inSingle = false; // 在 '...' 中
        for (int i = 0; i < sql.length(); i++) {
            char c = sql.charAt(i);
            if (c == '\'') {
                // 处理转义 '' -> 字面上的一个 '
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
