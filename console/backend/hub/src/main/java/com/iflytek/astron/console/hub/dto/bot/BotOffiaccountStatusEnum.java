package com.iflytek.astron.console.hub.dto.bot;

import lombok.Getter;

/**
 * Binding status of bot with WeChat Official Account
 */
@Getter
public enum BotOffiaccountStatusEnum {

    BOUND(1, "Bound"),
    UNBOUND(2, "Unbound");

    /**
     * Status type
     */
    private final Integer status;
    /**
     * Description
     */
    private final String desc;

    BotOffiaccountStatusEnum(Integer status, String desc) {
        this.status = status;
        this.desc = desc;
    }

}
