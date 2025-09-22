package com.iflytek.astra.console.toolkit.entity.enumVo;

import java.util.Objects;

/**
 * Model deployment status enum class
 *
 * @Author clliu19
 * @Date: 2025/9/13 15:52
 */
public enum ModelStatusEnum {
    RUNNING(1, "running", "Running"),
    PENDING(2, "pending", "Pending"),
    FAILED(3, "failed", "Failed"),
    INITIALIZING(4, "initializing", "Initializing"),
    NOTEXIST(5, "notExist", "Not Exist"),
    TERMINATING(6, "terminating", "Terminating");

    private Integer code;
    private String value;
    private String valueCn;

    ModelStatusEnum(Integer code, String value, String valueCn) {
        this.code = code;
        this.value = value;
        this.valueCn = valueCn;
    }

    public Integer getCode() {
        return code;
    }

    public String getValue() {
        return value;
    }

    public static String getValueByCode(Integer code) {
        for (DBTableEnvEnum value : DBTableEnvEnum.values()) {
            if (Objects.equals(value.getCode(), code)) {
                return value.getValue();
            }
        }
        return null;
    }

    public static Integer getCodeByValue(String value) {
        for (DBTableEnvEnum item : DBTableEnvEnum.values()) {
            if (Objects.equals(item.getValue(), value)) {
                return item.getCode();
            }
        }
        return RUNNING.code;
    }

}
