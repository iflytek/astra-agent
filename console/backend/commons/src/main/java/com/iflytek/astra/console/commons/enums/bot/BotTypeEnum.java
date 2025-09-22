package com.iflytek.astra.console.commons.enums.bot;

/**
 * @author yingpeng
 */
public enum BotTypeEnum {

    SYSTEM_BOT(1, "Command Bot"),
    WORKFLOW_BOT(3, "Workflow Bot");

    private final Integer type;

    private final String desc;

    BotTypeEnum(Integer type, String desc) {
        this.type = type;
        this.desc = desc;
    }

    public Integer getType() {
        return type;
    }
}
