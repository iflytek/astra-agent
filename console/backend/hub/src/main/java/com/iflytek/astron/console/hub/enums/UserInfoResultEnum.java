package com.iflytek.astron.console.hub.enums;

import lombok.Getter;

public enum UserInfoResultEnum {

    NORMAL(0, "Normal"),
    NOT_EXIST(1, "Not Exist"),
    JOINED(2, "Joined"),
    INVITING(3, "Inviting"),
    INVALID_MOBILE(4, "Invalid Mobile"),
    ;

    private final Integer code;

    @Getter
    private final String desc;

    UserInfoResultEnum(Integer code, String desc) {
        this.code = code;
        this.desc = desc;
    }

}
