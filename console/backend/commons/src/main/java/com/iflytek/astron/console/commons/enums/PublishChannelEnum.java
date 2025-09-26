package com.iflytek.astron.console.commons.enums;

import lombok.Getter;

/**
 * Publishing Channel Enumeration
 *
 * @author Omuigix
 */
@Getter
public enum PublishChannelEnum {

    /**
     * Market publishing
     */
    MARKET("MARKET", "Market publishing"),

    /**
     * API interface publishing
     */
    API("API", "API interface publishing"),

    /**
     * WeChat Official Account publishing
     */
    WECHAT("WECHAT", "WeChat Official Account publishing"),

    /**
     * MCP service publishing
     */
    MCP("MCP", "MCP service publishing");

    /**
     * Channel code
     */
    private final String code;

    /**
     * Channel description
     */
    private final String description;

    PublishChannelEnum(String code, String description) {
        this.code = code;
        this.description = description;
    }

    /**
     * Get enum by code
     *
     * @param code Channel code
     * @return Publishing channel enum
     */
    public static PublishChannelEnum fromCode(String code) {
        if (code == null) {
            return null;
        }
        for (PublishChannelEnum channel : values()) {
            if (channel.getCode().equals(code)) {
                return channel;
            }
        }
        return null;
    }

    /**
     * Validate if channel code is valid
     *
     * @param code Channel code
     * @return Whether valid
     */
    public static boolean isValidCode(String code) {
        return fromCode(code) != null;
    }
}
