package com.iflytek.astra.console.toolkit.entity.biz.workflow;

import lombok.Data;

@Data
public class FlowReleaseReq {
    String flowId;

    /**
     * Release channel
     */
    String channel;

    /**
     * Release operation
     */
    Integer operate; // 1=online, 2=offline, 3=update, enum values are also defined in ReleaseService
    /**
     * Release information, different channels have different structures. String type is used here for
     * unified reception, then parsed into different entities based on specific channels
     */
    String info;
    String mcpInfo;
    /**
     * Released version
     */
    String version;
}
