package com.iflytek.astron.console.hub.enums;

/**
 * Error related
 *
 * @author yun-zhi-ztl
 */
public enum LongContextStatusEnum {
    FINALLY(-7, "Fallback Error", "File parsing error"),
    OUT_FILE_SIZE(-2, "File size exceeds limit", "File too large"),
    DELETED(-1, "Deleted", "File upload cancelled"),
    UNPROCESSED(0, "Unprocessed", null),
    PROCESSING(1, "Processing", null),
    PROCESSED(2, "Processing completed", null);

    private final int value;
    private final String description;

    private final String errorMsg;

    LongContextStatusEnum(int value, String description, String errorMsg) {
        this.value = value;
        this.description = description;
        this.errorMsg = errorMsg;
    }

    public int getValue() {
        return value;
    }

    public String getDescription() {
        return description;
    }

    public String getErrorMsg() {
        return errorMsg;
    }

    public static String getErrorMsgByValue(Integer value) {
        for (LongContextStatusEnum status : LongContextStatusEnum.values()) {
            if (status.getValue() == value) {
                return status.getErrorMsg();
            }
        }
        return LongContextStatusEnum.FINALLY.getErrorMsg();
    }
}
