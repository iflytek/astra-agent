package com.iflytek.astra.console.commons.enums.space;

public enum SpaceTypeEnum {

    FREE(1, "Free"), PRO(2, "Pro"), TEAM(3, "Team"), ENTERPRISE(4, "Enterprise");

    private Integer code;

    private String desc;

    SpaceTypeEnum(Integer code, String desc) {
        this.code = code;
        this.desc = desc;
    }

    public static SpaceTypeEnum getByCode(Integer code) {
        if (code == null) {
            return null;
        }
        for (SpaceTypeEnum value : values()) {
            if (value.getCode().equals(code)) {
                return value;
            }
        }
        return null;
    }


    public boolean isTeam() {
        return this.code.equals(TEAM.code) || this.code.equals(ENTERPRISE.code);
    }


    public Integer getCode() {
        return code;
    }

    public String getDesc() {
        return desc;
    }
}
