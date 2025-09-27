package com.iflytek.astron.console.toolkit.entity.metrological;


import lombok.Data;
import lombok.experimental.Accessors;

@Data
@Accessors(chain = true)
public class MetrologicalV2AuthDto {
    String operType;
    String orderId;
    String appId;
    String channel;
    String function;
    String orderEndTime;
    String limit;
    String type;
}
