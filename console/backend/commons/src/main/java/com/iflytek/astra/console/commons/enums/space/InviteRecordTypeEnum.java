package com.iflytek.astra.console.commons.enums.space;

// Invitation type: 1 space, 2 team
public enum InviteRecordTypeEnum {


    SPACE(1, "Space"), ENTERPRISE(2, "Enterprise");

    private Integer code;

    private String desc;


    InviteRecordTypeEnum(Integer code, String desc) {
        this.code = code;
        this.desc = desc;
    }

    public Integer getCode() {
        return code;
    }

    public String getDesc() {
        return desc;
    }
}
