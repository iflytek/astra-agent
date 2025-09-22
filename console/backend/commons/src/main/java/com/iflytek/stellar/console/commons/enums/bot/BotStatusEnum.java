package com.iflytek.astra.console.commons.enums.bot;

import java.util.Arrays;
import java.util.List;

public enum BotStatusEnum {

    // bot状态，0下架，2已上架
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
        throw new EnumConstantNotPresentException(BotStatusEnum.class, "未找到相关枚举类");
    }
}
