package com.iflytek.astra.console.toolkit.entity.biz.external.app;

import com.alibaba.fastjson2.annotation.JSONField;
import lombok.*;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class AkSk {
    @JSONField(name = "api_key")
    String apiKey;
    @JSONField(name = "api_secret")
    String apiSecret;
}
