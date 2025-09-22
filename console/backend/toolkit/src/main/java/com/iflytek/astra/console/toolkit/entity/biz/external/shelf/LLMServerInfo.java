package com.iflytek.astra.console.toolkit.entity.biz.external.shelf;

import lombok.Data;

@Data
public class LLMServerInfo {
    Long id;
    String name;
    String serviceId;
    String serverId;
    String domain;
    String patchId;
    Integer type;
    Object config;
    Integer source;
    String url;
    String appId;

    // Billing authorization channel, currently only data from shelf has this
    String licChannel;
}
