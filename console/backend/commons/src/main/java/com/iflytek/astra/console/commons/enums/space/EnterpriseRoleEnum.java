package com.iflytek.astra.console.commons.enums.space;

public enum EnterpriseRoleEnum {

    OFFICER(1, "Super Admin"),
    GOVERNOR(2, "Admin"),
    STAFF(3, "Member");

    private Integer code;

    private String desc;

    EnterpriseRoleEnum(Integer code, String desc) {
        this.code = code;
        this.desc = desc;
    }

    public static EnterpriseRoleEnum getByCode(Integer code) {
        for (EnterpriseRoleEnum value : EnterpriseRoleEnum.values()) {
            if (value.getCode().equals(code)) {
                return value;
            }
        }
        return null;
    }

    public Integer getCode() {
        return code;
    }

    public String getDesc() {
        return desc;
    }
}
