package com.iflytek.astra.console.hub.enums;

import java.util.Arrays;
import java.util.List;

/**
 * @author yingpeng
 */
public enum ChatFileLimitEnum {
    AGENT(16, "Astra Application Platform", 100, 200, "upload_agent_count_", 104857600L, 1, null,
            Arrays.asList("pdf", "jpg", "jpeg", "png", "bmp", "webp", "doc", "docx", "ppt", "pptx", "xls", "xlsx", "csv", "txt",
                    "wav", "mp3", "flac", "m4a", "aac", "ogg", "wma", "midi"));

    private final Integer value;

    private final String description;

    // Maximum daily upload count limit
    private final Integer dailyUploadNum;

    // Maximum document limit bound to each chatId
    private final Integer chatBindNum;

    // Redis prefix
    private final String redisPrefix;

    // Document size limit
    private final Long maxSize;

    // Whether to display: 0-display, 1-no display
    private final Integer display;

    // Engineering academy type
    private final String fileBizType;

    // Supported file extensions
    private final List<String> extensionList;

    ChatFileLimitEnum(int value, String description, Integer dailyUploadNum, Integer chatBindNum, String redisPrefix, Long maxSize, Integer display, String fileBizType, List<String> extensionList) {
        this.value = value;
        this.description = description;
        this.dailyUploadNum = dailyUploadNum;
        this.chatBindNum = chatBindNum;
        this.redisPrefix = redisPrefix;
        this.maxSize = maxSize;
        this.display = display;
        this.fileBizType = fileBizType;
        this.extensionList = extensionList;
    }

    public Integer getValue() {
        return value;
    }

    public String getType() {
        return description;
    }

    public Integer getDailyUploadNum() {
        return dailyUploadNum;
    }

    public Integer getChatBindNum() {
        return chatBindNum;
    }

    public String getRedisPrefix() {
        return redisPrefix;
    }

    public Long getMaxSize() {
        return maxSize;
    }

    public static ChatFileLimitEnum getByValue(Integer value) {
        for (ChatFileLimitEnum modelEnum : values()) {
            if (modelEnum.value.equals(value)) {
                return modelEnum;
            }
        }
        return null;
    }

    public boolean checkFileByType(String filename) {
        String extension = getFileExtension(filename);
        for (ChatFileLimitEnum limitEnum : ChatFileLimitEnum.values()) {
            if (limitEnum.getExtensionList().contains(extension)) {
                return true;
            }
        }
        return false;
    }

    public static boolean checkFileByBusinessType(String filename, Integer businessType) {
        ChatFileLimitEnum fileLimitEnum = getByValue(businessType);
        if (fileLimitEnum == null) {
            return false;
        }
        String fileExtension = getFileExtension(filename);
        return fileLimitEnum.getExtensionList().contains(fileExtension);
    }

    public Integer getDisplay() {
        return display;
    }

    public String getFileBizType() {
        return fileBizType;
    }

    public List<String> getExtensionList() {
        return extensionList;
    }

    private static String getFileExtension(String filename) {
        if (filename == null || !filename.contains(".")) {
            return "";
        }
        return filename.substring(filename.lastIndexOf(".") + 1).toLowerCase();
    }
}
