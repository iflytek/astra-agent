package com.iflytek.astron.console.toolkit.entity.biz.external.shelf;

import lombok.Data;

import java.util.Date;

@Data
public class LLMExpeDto {
    Long id;
    Long modelId;
    String name;
    String desc;
    Integer source;
    Integer type;
    Integer subType;
    String imageUrl;
    String docUrl;
    String doc;
    String content;
    Boolean isDeleted;
    Date createTime;
    Date updateTime;
    Boolean isHot;
    String domain;
    String serviceId;
    String serverId;
    Integer openExperience;
    Integer useScene;
    String patchId;
    String url;

    // Billing authorization channel
    String licChannel;
}
