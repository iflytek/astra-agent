package com.iflytek.astron.console.toolkit.service.database;

import com.alibaba.excel.context.AnalysisContext;
import com.alibaba.excel.event.AnalysisEventListener;
import com.iflytek.astron.console.commons.constant.ResponseEnum;
import com.iflytek.astron.console.commons.exception.BusinessException;
import com.iflytek.astron.console.toolkit.entity.dto.database.DbTableFieldDto;
import com.iflytek.astron.console.toolkit.handler.language.LanguageContext;
import org.apache.commons.lang3.StringUtils;

import java.util.*;

public class DBTableExcelReadListener extends AnalysisEventListener<Map<Integer, String>> {

    private static final List<String> expectedHeaders = Arrays.asList(
            "字段名*", "数据类型*", "描述*", "默认值", "是否必填*");
    private static final List<String> expectedHeadersEn = Arrays.asList(
            "Field Name*", "Data Type*", "Description*", "Default Value", "Required*");

    private static final List<String> fieldType = Arrays.asList(
            "String", "Integer", "Time", "Number", "Boolean");

    private List<DbTableFieldDto> tableFields;

    public DBTableExcelReadListener(List<DbTableFieldDto> tableFields) {
        this.tableFields = tableFields;
    }

    @Override
    public void invokeHeadMap(Map<Integer, String> headMap, AnalysisContext context) {
        List<String> actualHeaders = new ArrayList<>(headMap.values());
        List<String> expectedHeadersFormat = new ArrayList<>();
        if (LanguageContext.isEn()) {
            expectedHeadersFormat = expectedHeadersEn;
        } else {
            expectedHeadersFormat = expectedHeaders;
        }
        if (!expectedHeadersFormat.equals(actualHeaders)) {
            throw new BusinessException(ResponseEnum.DATABASE_TABLE_FIELD_IMPORT_DEFAULT);
        }
    }


    @Override
    public void invoke(Map<Integer, String> row, AnalysisContext context) {
        // 校验必填项是否为空
        DbTableFieldDto dbTableFieldDto = new DbTableFieldDto();
        if (row.get(0) == null || row.get(1) == null || row.get(2) == null || row.get(4) == null) {
            throw new BusinessException(ResponseEnum.DATABASE_CANNOT_EMPTY);
        }
        dbTableFieldDto.setName(row.get(0));
        if (!fieldType.contains(row.get(1))) {
            throw new BusinessException(ResponseEnum.DATABASE_TYPE_ILLEGAL);
        }
        dbTableFieldDto.setType(row.get(1));
        dbTableFieldDto.setDescription(row.get(2));
        if (StringUtils.isNotBlank(row.get(3))) {
            if ("Integer".equalsIgnoreCase(row.get(1))) {
                try {
                    Long.parseLong(row.get(3));
                } catch (NumberFormatException e) {
                    throw new BusinessException(ResponseEnum.DATABASE_TABLE_ILLEGAL_DEFAULT);
                }
            } else if ("Boolean".equalsIgnoreCase(row.get(1))) {
                if (!"true".equalsIgnoreCase(row.get(3)) && !"false".equalsIgnoreCase(row.get(3))) {
                    throw new BusinessException(ResponseEnum.DATABASE_TABLE_ILLEGAL_DEFAULT);
                }
            } else if ("Number".equalsIgnoreCase(row.get(1))) {
                try {
                    Double.parseDouble(row.get(3));
                } catch (NumberFormatException e) {
                    throw new BusinessException(ResponseEnum.DATABASE_TABLE_ILLEGAL_DEFAULT);
                }
            }
        }
        dbTableFieldDto.setDefaultValue(row.get(3));
        dbTableFieldDto.setIsRequired("是".equals(row.get(4)));
        tableFields.add(dbTableFieldDto);
    }

    @Override
    public void doAfterAllAnalysed(AnalysisContext analysisContext) {
        if (tableFields.isEmpty()) {
            throw new IllegalArgumentException("没有字段相关信息，请检查数据是否正确！");
        }
    }
}
