package com.iflytek.astra.console.toolkit.service.database;

import com.alibaba.excel.context.AnalysisContext;
import com.alibaba.excel.event.AnalysisEventListener;
import com.iflytek.astra.console.commons.constant.ResponseEnum;
import com.iflytek.astra.console.commons.exception.BusinessException;
import com.iflytek.astra.console.toolkit.common.constant.CommonConst;
import com.iflytek.astra.console.toolkit.entity.table.database.DbTableField;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 读取 Excel -> 产出结构化行数据（每行 Map<列名, 值>），避免 SQL 拼接。 - 校验表头与必填 - 空值回落到字段默认值/类型默认值 - 可设置最大行数上限
 */
public class DBExcelReadListener extends AnalysisEventListener<Map<Integer, String>> {

    private static final String[] SYSTEM_FIELDS = {"id", "uid", "create_time"};
    private static final DateTimeFormatter TS = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final List<DbTableField> tableFields;
    private final List<Map<String, Object>> rowsSink; // 产出容器
    private final String uid; // 每行自动补 uid
    private final int maxRows; // 读取上限（防炸）

    private List<String> expectedHeaders;
    private List<String> notNullFieldsList;

    private int accepted = 0;
    private boolean headerValidated = false;

    /** 推荐使用：一次性装入 rowsSink */
    public DBExcelReadListener(List<DbTableField> tableFields,
            List<Map<String, Object>> rowsSink,
            String uid,
            int maxRows) {
        this.tableFields = Objects.requireNonNull(tableFields);
        this.rowsSink = Objects.requireNonNull(rowsSink);
        this.uid = uid;
        this.maxRows = Math.max(1, maxRows);
    }

    @Override
    public void invokeHeadMap(Map<Integer, String> headMap, AnalysisContext context) {
        List<String> actualHeaders = new ArrayList<>(headMap.values());

        expectedHeaders = tableFields.stream()
                .map(DbTableField::getName)
                .filter(n -> !Arrays.asList(SYSTEM_FIELDS).contains(n))
                .collect(Collectors.toList());

        notNullFieldsList = tableFields.stream()
                .filter(f -> !Arrays.asList(SYSTEM_FIELDS).contains(f.getName()))
                .filter(DbTableField::getIsRequired)
                .map(DbTableField::getName)
                .collect(Collectors.toList());

        // 这里要求顺序一致：和你原逻辑保持一致
        if (!CollectionUtils.isEqualCollection(expectedHeaders, actualHeaders)) {
            throw new IllegalArgumentException("表头不匹配！预期表头：" + expectedHeaders + "，实际表头：" + actualHeaders);
        } else {
            expectedHeaders = actualHeaders;
        }
        headerValidated = true;
    }

    @Override
    public void invoke(Map<Integer, String> row, AnalysisContext context) {
        if (!headerValidated) {
            throw new BusinessException(ResponseEnum.RESPONSE_FAILED, "表头尚未校验，请检查 Excel 文件。");
        }
        if (accepted >= maxRows) {
            return; // 超过上限直接忽略后续行，保证可用性
        }

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("uid", uid);

        for (int i = 0; i < expectedHeaders.size(); i++) {
            String header = expectedHeaders.get(i);
            String raw = row.get(i); // 单元格原始值（可能为 null）
            DbTableField meta = tableFields.stream()
                    .filter(f -> f.getName().equals(header))
                    .findFirst()
                    .orElseThrow(() -> new BusinessException(ResponseEnum.RESPONSE_FAILED, "字段 " + header + " 不存在！"));

            Object v;
            if (StringUtils.isBlank(raw)) {
                // 空值：必填 -> 用字段默认值；非必填 -> 类型默认值（或 null）
                v = chooseDefault(meta, notNullFieldsList.contains(header));
            } else {
                v = parseByType(raw, meta.getType());
            }
            out.put(header, v);
        }

        rowsSink.add(out);
        accepted++;
    }

    @Override
    public void doAfterAllAnalysed(AnalysisContext analysisContext) {
        if (accepted == 0) {
            throw new IllegalArgumentException("文件中没有任何有效数据，请检查excel数据是否正确！");
        }
    }

    // —— 辅助：解析与默认值 —— //

    private Object parseByType(String s, String type) {
        String t = StringUtils.lowerCase(type);
        switch (t) {
            case CommonConst.DBFieldType.INTEGER:
                return Long.parseLong(s.trim());
            case CommonConst.DBFieldType.NUMBER:
                return new BigDecimal(s.trim());
            case CommonConst.DBFieldType.BOOLEAN:
                return parseBoolean(s);
            case CommonConst.DBFieldType.TIME:
                // 要求标准格式，避免智能解析的歧义
                return LocalDateTime.parse(s.trim(), TS);
            default:
                return s; // 字符串原样
        }
    }

    private Object chooseDefault(DbTableField f, boolean required) {
        String t = StringUtils.lowerCase(f.getType());
        String def = f.getDefaultValue();

        if (StringUtils.isNotBlank(def)) {
            // 用户有配置默认值：按字段类型尝试解析
            try {
                return parseByType(def, t);
            } catch (Exception ignore) {
                // 兜底：作为字符串
                return def;
            }
        }

        // 没配默认值
        if (required) {
            // 必填但空：给出类型默认值
            switch (t) {
                case CommonConst.DBFieldType.INTEGER:
                    return 0L;
                case CommonConst.DBFieldType.NUMBER:
                    return BigDecimal.ZERO;
                case CommonConst.DBFieldType.BOOLEAN:
                    return Boolean.FALSE;
                case CommonConst.DBFieldType.TIME:
                    return LocalDateTime.now();
                default:
                    return ""; // 字符串给空串
            }
        } else {
            // 非必填：可以为 null（由写入层决定是否允许）
            switch (t) {
                case CommonConst.DBFieldType.INTEGER:
                    return null;
                case CommonConst.DBFieldType.NUMBER:
                    return null;
                case CommonConst.DBFieldType.BOOLEAN:
                    return null;
                case CommonConst.DBFieldType.TIME:
                    return null;
                default:
                    return ""; // 字符串给空串更友好
            }
        }
    }

    private Boolean parseBoolean(String s) {
        String x = s.trim().toLowerCase(Locale.ROOT);
        if (x.equals("1") || x.equals("true") || x.equals("t") || x.equals("yes") || x.equals("y"))
            return Boolean.TRUE;
        if (x.equals("0") || x.equals("false") || x.equals("f") || x.equals("no") || x.equals("n"))
            return Boolean.FALSE;
        throw new IllegalArgumentException("无法解析布尔值: " + s);
    }
}
