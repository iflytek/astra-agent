package com.iflytek.astra.console.commons.enums.bot;

import com.alibaba.fastjson2.JSONObject;

public enum BotUploadEnum {

    NONE("", "", "none", 0, -1, 0),
    DOC("document", "pdf", ".pdf", 1, 0, 1),
    IMG("image", "Image", ".png,.jpg,.jpeg", 2, 3, 1),
    // Doc（DOC、DOCX）、PPT（PPT、PPTX）、Excel（XLS、XLSX、CSV）、Txt
    DOC2("doc", "doc", ".doc,.docx", 3, 0, 1),
    PPT("ppt", "ppt", ".ppt,.pptx", 4, 0, 1),
    EXCEL("excel", "excel", ".xls,.xlsx,.csv", 5, 0, 1),
    TXT("txt", "txt", ".txt", 6, 0, 1),
    AUDIO("audio", "Audio", ".wav,.mp3,.flac,.m4a,.aac,.ogg,.wma,.midi", 7, 0, 1),

    DOC_ARRAY("document", "pdf", ".pdf", 21, 0, 10),
    IMG_ARRAY("image", "Image", ".png,.jpg,.jpeg", 22, 3, 10),
    DOC2_ARRAY("doc", "doc", ".doc,.docx", 23, 0, 10),
    PPT_ARRAY("ppt", "ppt", ".ppt,.pptx", 24, 0, 10),
    EXCEL_ARRAY("excel", "excel", ".xls,.xlsx,.csv", 25, 0, 10),
    TXT_ARRAY("txt", "txt", ".txt", 26, 0, 10),
    AUDIO_ARRAY("audio", "Audio", ".wav,.mp3,.flac,.m4a,.aac,.ogg,.wma,.midi", 27, 0, 10),
    ;

    public final String icon;
    public final String tip;
    public final String accept;
    public final int value;
    public final int businessType;
    public final int limit;

    BotUploadEnum(String icon, String tip, String accept, int value, int businessType, int limit) {
        this.icon = icon;
        this.tip = tip;
        this.accept = accept;
        this.value = value;
        this.businessType = businessType;
        this.limit = limit;
    }

    public int getValue() {
        return value;
    }

    public String getAccept() {
        return accept;
    }

    // Get corresponding enum instance by value
    public static BotUploadEnum getByValue(int value) {
        for (BotUploadEnum enumValue : BotUploadEnum.values()) {
            if (enumValue.getValue() == value) {
                return enumValue;
            }
        }
        return NONE;
    }

    // Convert single enum instance to JSONObject
    public JSONObject toJSONObject() {
        JSONObject enumObj = new JSONObject();
        enumObj.put("icon", this.icon);
        enumObj.put("tip", this.tip);
        enumObj.put("accept", this.accept);
        enumObj.put("businessType", this.businessType);
        enumObj.put("value", this.value);
        enumObj.put("limit", this.limit);
        return enumObj;
    }
}
