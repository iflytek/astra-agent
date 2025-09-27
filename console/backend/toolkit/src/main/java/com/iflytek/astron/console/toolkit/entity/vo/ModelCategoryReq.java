package com.iflytek.astron.console.toolkit.entity.vo;

import lombok.Data;

import java.util.List;

@Data
public class ModelCategoryReq {
    private Long modelId;

    // Multiple choice: Model category (official ID + custom name)
    private List<Long> categorySystemIds;
    private CustomItem categoryCustom;

    // Multiple choice: Model scenario
    private List<Long> sceneSystemIds;
    private CustomItem sceneCustom;

    // Single choice: Language support (official ID only)
    private Long languageSystemId;

    // Single choice: Context length (official ID only)
    private Long contextLengthSystemId;

    // Required context for custom items
    private String ownerUid;

    @Data
    public static class CustomItem {
        Long pid;
        String customName;
    }
}
