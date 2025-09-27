package com.iflytek.astron.console.hub.enums;

import lombok.Getter;

@Getter
public enum NotificationType {
    PERSONAL("PERSONAL", "Personal message"),
    BROADCAST("BROADCAST", "Broadcast message"),
    SYSTEM("SYSTEM", "System notification"),
    PROMOTION("PROMOTION", "Promotion message");

    private final String code;
    private final String description;

    NotificationType(String code, String description) {
        this.code = code;
        this.description = description;
    }

    public static NotificationType fromCode(String code) {
        for (NotificationType type : NotificationType.values()) {
            if (type.code.equals(code)) {
                return type;
            }
        }
        return null;
    }
}
