package com.iflytek.astra.console.toolkit.entity.spark.request;

import com.alibaba.fastjson2.annotation.JSONField;
import lombok.Data;

@Data
public class Chat {
    String domain = "generalv3.5";

    Double temperature;

    @JSONField(name = "maxTokens")
    Integer max_tokens;
}
