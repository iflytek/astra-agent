package com.iflytek.astra.console.toolkit.entity.biz.external.app;

import lombok.Data;

/**
 * Console application entity
 */
@Data
public class PlatformApp {
    private Long id;
    private String appId;
    private String name;
    private String cloudId;
    private String category;
    private String description;
    private String userId;
    private Integer star;
    private Integer isGrayscale;
    private Integer supportXrtc;
    private String createTime;
    private String updateTime;
    private Integer isLocalAuth;
    private String remark;
}
