package com.iflytek.astron.console.hub.enums;

import lombok.Getter;

@Getter
public enum NotificationType {
    PERSONAL("personal", "Personal message"),
    BROADCAST("broadcast", "Broadcast message"),
    SYSTEM("system", "System notification"),
    PROMOTION("promotion", "Promotion message");

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
