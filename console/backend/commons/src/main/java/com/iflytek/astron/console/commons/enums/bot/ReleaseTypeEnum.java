package com.iflytek.astron.console.commons.enums.bot;

public enum ReleaseTypeEnum {

    MARKET(1, "Bot Market"),

    BOT_API(2, "Bot API"),

    WECHAT(3, "WeChat Official Account"),

    MCP(4, "MCP"),
    ;

    private Integer code;

    private String desc;

    public Integer getCode() {
        return code;
    }

    ReleaseTypeEnum(Integer code, String desc) {
        this.code = code;
        this.desc = desc;
    }

}
