package com.iflytek.astra.console.toolkit.entity.enumVo;


/**
 * Database operation enum
 */
public enum DBOperateEnum {

    INSERT(1),
    UPDATE(2),
    SELECT(3),
    DELETE(4),
    COPY(5),
    SELECT_TOTAL_COUNT(6),;

    private Integer code;

    DBOperateEnum(Integer code) {
        this.code = code;
    }

    public Integer getCode() {
        return code;
    }

}
