package com.iflytek.astra.console.toolkit.entity;

import lombok.*;
import lombok.experimental.Accessors;

/**
 * Unified entity for FlyCloud and Open Platform users
 */
@Data
@Accessors(chain = true)
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class UserInfo {
    /**
     * User ID
     */
    String uid;

    /**
     * Username
     */
    String login;

    /**
     * User real name
     */
    String nickname;

    /**
     * Email
     */
    String email;

    /**
     * Mobile phone number
     */
    String mobile;

    Integer certificateStatus;

    Object certificateType;

    Object balance;

    Long registrationTime;

    String accountName;

    Integer authType;

    Object department;

    Integer isPublic;

    Long operator;

    Object needLogin;

    Integer source;

    String cloudId;

    String appId;
}
