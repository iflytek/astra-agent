package com.iflytek.astron.console.toolkit.service.database;

import com.alibaba.excel.context.AnalysisContext;
import com.alibaba.excel.event.AnalysisEventListener;
import com.iflytek.astron.console.commons.constant.ResponseEnum;
import com.iflytek.astron.console.commons.exception.BusinessException;
import com.iflytek.astron.console.toolkit.common.constant.CommonConst;
import com.iflytek.astron.console.toolkit.entity.table.database.DbTableField;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Read Excel -> Generate structured row data (each row Map<column name, value>), avoid SQL
 * concatenation. - Validate headers and required fields - Null values fall back to field default
 * values/type default values - Can set maximum row limit
 */
public class DBExcelReadListener extends AnalysisEventListener<Map<Integer, String>> {

    private static final String[] SYSTEM_FIELDS = {"id", "uid", "create_time"};
    private static final DateTimeFormatter TS = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final List<DbTableField> tableFields;
    private final List<Map<String, Object>> rowsSink; // Output container
    private final String uid; // Automatically add uid to each row
    private final int maxRows; // Read limit (prevent explosion)

    private List<String> expectedHeaders;
    private List<String> notNullFieldsList;

    private int accepted = 0;
    private boolean headerValidated = false;

    /** Recommended usage: load into rowsSink at once */
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

        // Here requires consistent order: maintain consistency with your original logic
        if (!CollectionUtils.isEqualCollection(expectedHeaders, actualHeaders)) {
            throw new IllegalArgumentException("Header mismatch! Expected headers: " + expectedHeaders + ", Actual headers: " + actualHeaders);
        } else {
            expectedHeaders = actualHeaders;
        }
        headerValidated = true;
    }

    @Override
    public void invoke(Map<Integer, String> row, AnalysisContext context) {
        if (!headerValidated) {
            throw new BusinessException(ResponseEnum.RESPONSE_FAILED, "Headers not yet validated, please check Excel file.");
        }
        if (accepted >= maxRows) {
            return; // Exceed limit, directly ignore subsequent rows to ensure availability
        }

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("uid", uid);

        for (int i = 0; i < expectedHeaders.size(); i++) {
            String header = expectedHeaders.get(i);
            String raw = row.get(i); // Cell raw value (may be null)
            DbTableField meta = tableFields.stream()
                    .filter(f -> f.getName().equals(header))
                    .findFirst()
                    .orElseThrow(() -> new BusinessException(ResponseEnum.RESPONSE_FAILED, "Field " + header + " does not exist!"));

            Object v;
            if (StringUtils.isBlank(raw)) {
                // Null value: required -> use field default value; not required -> type default value (or null)
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
            throw new IllegalArgumentException("No valid data in file, please check if excel data is correct!");
        }
    }

    // —— Helper: Parse and default values —— //

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
                // Require standard format to avoid ambiguity in smart parsing
                return LocalDateTime.parse(s.trim(), TS);
            default:
                return s; // String as is
        }
    }

    private Object chooseDefault(DbTableField f, boolean required) {
        String t = StringUtils.lowerCase(f.getType());
        String def = f.getDefaultValue();

        if (StringUtils.isNotBlank(def)) {
            // User has configured default value: try to parse by field type
            try {
                return parseByType(def, t);
            } catch (Exception ignore) {
                // Fallback: as string
                return def;
            }
        }

        // No default value configured
        if (required) {
            // Required but empty: give type default value
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
                    return ""; // String gives empty string
            }
        } else {
            // Not required: can be null (determined by write layer whether to allow)
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
                    return ""; // String gives empty string more friendly
            }
        }
    }

    private Boolean parseBoolean(String s) {
        String x = s.trim().toLowerCase(Locale.ROOT);
        if (x.equals("1") || x.equals("true") || x.equals("t") || x.equals("yes") || x.equals("y"))
            return Boolean.TRUE;
        if (x.equals("0") || x.equals("false") || x.equals("f") || x.equals("no") || x.equals("n"))
            return Boolean.FALSE;
        throw new IllegalArgumentException("Unable to parse boolean value: " + s);
    }
}
