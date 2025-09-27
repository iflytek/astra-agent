package com.iflytek.astron.console.commons.enums.bot;

import java.util.Arrays;
import java.util.List;

public enum BotStatusEnum {

    // bot status, 0 removed, 2 published
    REMOVED(0),
    PUBLISHED(2),
    MARKET_NOT_EXIST(-9);

    private int code;

    BotStatusEnum(int code) {
        this.code = code;
    }

    public int getCode() {
        return code;
    }

    public static List<Integer> shelves() {
        return Arrays.asList(
                PUBLISHED.ordinal());
    }

    public static BotStatusEnum getByCode(Integer status) {
        for (BotStatusEnum value : BotStatusEnum.values()) {
            if (value.ordinal() == status) {
                return value;
            }
        }
        throw new EnumConstantNotPresentException(BotStatusEnum.class, "Related enum class not found");
    }
}
