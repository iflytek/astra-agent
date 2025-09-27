package com.iflytek.astron.console.toolkit.entity.biz.external.app;

import com.alibaba.fastjson2.annotation.JSONField;
import lombok.Data;
import lombok.EqualsAndHashCode;

@EqualsAndHashCode(callSuper = true)
@Data
public class App3Ele extends AkSk {
    @JSONField(name = "app_id")
    String appId;
}
