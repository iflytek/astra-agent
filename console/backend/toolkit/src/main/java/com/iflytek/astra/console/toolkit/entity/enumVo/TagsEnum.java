package com.iflytek.astra.console.toolkit.entity.enumVo;

/**
 * @Author clliu19
 * @Date: 2024/6/7 09:20 bot&tool list query tag enumeration
 */
public enum TagsEnum {
    // Recommended
    RECOMMENDED("Recommended"),
    // Recent
    RECENT("Recent");

    private final String tages;

    public String getTages() {
        return tages;
    }

    TagsEnum(String tages) {
        this.tages = tages;
    }
}
