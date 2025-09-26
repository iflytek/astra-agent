package com.iflytek.astron.console.commons.enums;

import lombok.Getter;

/**
 * 智能体与微信公众号的绑定状态枚举
 *
 * @author Omuigix
 */
@Getter
public enum BotOffiaccountStatusEnum {

    /**
     * 已绑定
     */
    BOUND(1, "已绑定"),

    /**
     * 已解绑
     */
    UNBOUND(2, "已解绑");

    /**
     * 状态码
     */
    private final Integer status;

    /**
     * 状态描述
     */
    private final String desc;

    BotOffiaccountStatusEnum(Integer status, String desc) {
        this.status = status;
        this.desc = desc;
    }
}
