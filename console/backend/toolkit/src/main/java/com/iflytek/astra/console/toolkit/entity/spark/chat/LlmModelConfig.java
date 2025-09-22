package com.iflytek.astra.console.toolkit.entity.spark.chat;


import com.alibaba.fastjson2.annotation.JSONField;
import com.iflytek.astra.console.toolkit.common.constant.LLMConstant;
import lombok.Data;

import java.util.List;

@Data
public class LlmModelConfig {
    String api = "wss://spark-api.xf-yun.com/v1.1/chat";

    @JSONField(name = "api_key")
    String apiKey;
    @JSONField(name = "api_secret")
    String apiSecret;

    @JSONField(name = "patch_id")
    List<String> patchId;

    String domain = LLMConstant.DOMAIN_SPARK_1_5;

    @JSONField(name = "function_call")
    Boolean functionCall = true;
    String instruct;
    ModelCallParameter parameter;
}
