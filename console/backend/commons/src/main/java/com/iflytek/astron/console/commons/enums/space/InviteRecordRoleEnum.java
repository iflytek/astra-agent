package com.iflytek.astron.console.commons.enums.space;

public enum InviteRecordRoleEnum {

    ADMIN(2, "Admin"),
    MEMBER(3, "Member");

    private Integer code;

    private String desc;

    InviteRecordRoleEnum(Integer code, String desc) {
        this.code = code;
        this.desc = desc;
    }

    public static InviteRecordRoleEnum getByCode(Integer code) {
        for (InviteRecordRoleEnum value : InviteRecordRoleEnum.values()) {
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
