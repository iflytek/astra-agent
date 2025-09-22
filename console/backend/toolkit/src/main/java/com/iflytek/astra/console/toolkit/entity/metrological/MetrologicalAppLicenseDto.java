package com.iflytek.astra.console.toolkit.entity.metrological;


import com.alibaba.fastjson2.annotation.JSONField;
import lombok.Data;
import lombok.experimental.Accessors;

@Data
@Accessors(chain = true)
public class MetrologicalAppLicenseDto {
    @JSONField(name = "order_id")
    String orderId;
    @JSONField(name = "order_time")
    String orderTime;
    @JSONField(name = "app_id")
    String appId;
    String channel;
    String function;
    String limit;
    @JSONField(name = "begin_time")
    String orderBeginTime;
    @JSONField(name = "end_time")
    String orderEndTime;
    @JSONField(name = "ext_info")
    String extInfo;
    @JSONField(name = "is_del")
    String isDel;
    @JSONField(name = "lic_state")
    String licState;
    @JSONField(name = "order_desc")
    String orderDesc;
    @JSONField(name = "time_expired")
    String timeExpired;
}
