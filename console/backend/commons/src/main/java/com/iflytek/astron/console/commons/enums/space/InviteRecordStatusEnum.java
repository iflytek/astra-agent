package com.iflytek.astron.console.commons.enums.space;

public enum InviteRecordStatusEnum {

    INIT(1, "Initial"),
    REFUSE(2, "Refused"),
    ACCEPT(3, "Joined"),
    WITHDRAW(4, "Withdrawn"),
    EXPIRED(5, "Expired");

    private Integer code;

    private String desc;


    InviteRecordStatusEnum(Integer code, String desc) {
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
