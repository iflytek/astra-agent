package com.iflytek.astron.console.toolkit.entity.biz.apply;

import lombok.Data;


@Data
public class AuthApplyInfo {
    private String uid;
    /**
     * Account
     */
    private String account;
    /**
     * Chinese name
     */
    private String accountName;

    private String appId;

    private String llmServiceId;

    /**
     * Large model domain
     */
    private String domain;

    private Integer conc;

    private Integer qps;

    private String patchId;

    private Long tokensTotal;

    private Long tokensPreDay;

    private String expireTs;


    private String email;
    // Not used

    /**
     * Department information
     */
    private String departmentInfo;
    /**
     * Superior information
     */
    private String superiorInfo;
    /**
     * Description information
     */
    private String describe;
    /**
     * Proof material upload path
     */
    private String uploadPath;

    private String cloudId;
    private Integer modelType;
}
