package com.iflytek.astra.console.hub.enums;

import java.util.Arrays;

public enum WordsTypeEnum {

    LIKE_MESSAGE(1, "First Like Message"),
    HOT_MESSAGE(2, "Hot Message");

    private final int code;
    private final String description;

    WordsTypeEnum(int code, String description) {
        this.code = code;
        this.description = description;
    }

    /**
     * Get the code of the enum
     *
     * @return code
     */
    public int getCode() {
        return code;
    }

    /**
     * Get the description of the enum
     *
     * @return description
     */
    public String getDescription() {
        return description;
    }

    /**
     * Get the corresponding enum instance by code
     *
     * @param code enum code
     * @return corresponding WordsTypeEnum instance, returns null if no match
     */
    public static WordsTypeEnum getByCode(int code) {
        return Arrays.stream(values())
                .filter(e -> e.code == code)
                .findFirst()
                .orElse(null);
    }

}
