package com.iflytek.astra.console.commons.enums.user;

import java.util.Arrays;

public enum WordsTypeEnum {

    LIKE_MESSAGE(1, "First-like message"),
    HOT_MESSAGE(2, "Hot message");

    private final int code;
    private final String description;

    WordsTypeEnum(int code, String description) {
        this.code = code;
        this.description = description;
    }

    /**
     * Get enum code
     *
     * @return code
     */
    public int getCode() {
        return code;
    }

    /**
     * Get enum description
     *
     * @return description
     */
    public String getDescription() {
        return description;
    }

    /**
     * Get enum by code
     *
     * @param code enum code
     * @return WordsTypeEnum instance, or null if not matched
     */
    public static WordsTypeEnum getByCode(int code) {
        return Arrays.stream(values())
                .filter(e -> e.code == code)
                .findFirst()
                .orElse(null);
    }

}
