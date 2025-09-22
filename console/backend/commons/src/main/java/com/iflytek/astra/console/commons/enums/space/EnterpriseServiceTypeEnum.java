package com.iflytek.astra.console.commons.enums.space;

public enum EnterpriseServiceTypeEnum {

    TEAM(1, "Team"),
    ENTERPRISE(2, "Enterprise");

    private Integer code;

    private String desc;

    EnterpriseServiceTypeEnum(Integer code, String desc) {
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
