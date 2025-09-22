package com.iflytek.astra.console.toolkit.entity.dto;


import com.iflytek.astra.console.toolkit.common.constant.CommonConst;
import lombok.*;

/**
 * Pass parameters as needed in different types of authorized resources
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class ResourceParameter {

    String key;

    String orderId;

    Long count;

    Long qpsCount;

    Long expireTime;

    String uid;

    @Setter(AccessLevel.NONE)
    String sid;

    String businessId;

    String appid;

    Integer packageId;

    Object model;

    public void setSid(String sid) {
        this.sid = CommonConst.SID_PREFIX + sid;
    }
}
