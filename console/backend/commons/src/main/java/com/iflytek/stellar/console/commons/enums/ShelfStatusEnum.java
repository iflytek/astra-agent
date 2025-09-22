package com.iflytek.astra.console.commons.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * Shelf status enumeration for listing/delisting
 */
@Getter
@AllArgsConstructor
public enum ShelfStatusEnum {

    /**
     * Off shelf
     */
    OFF_SHELF(0, "Off Shelf"),

    /**
     * On shelf
     */
    ON_SHELF(1, "On Shelf");

    private final Integer code;
    private final String desc;

    /**
     * Get enum by code
     *
     * @param code status code
     * @return corresponding enum, returns null if not exists
     */
    public static ShelfStatusEnum getByCode(Integer code) {
        if (code == null) {
            return null;
        }
        for (ShelfStatusEnum status : values()) {
            if (status.getCode().equals(code)) {
                return status;
            }
        }
        return null;
    }

    /**
     * Check if it is on shelf status
     *
     * @param code status code
     * @return whether it is on shelf status
     */
    public static boolean isOnShelf(Integer code) {
        return ON_SHELF.getCode().equals(code);
    }

    /**
     * Check if it is off shelf status
     *
     * @param code status code
     * @return whether it is off shelf status
     */
    public static boolean isOffShelf(Integer code) {
        return OFF_SHELF.getCode().equals(code);
    }
}
