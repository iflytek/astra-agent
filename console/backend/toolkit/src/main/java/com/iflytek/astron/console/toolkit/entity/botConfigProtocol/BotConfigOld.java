package com.iflytek.astron.console.toolkit.entity.botConfigProtocol;

import com.alibaba.fastjson2.annotation.JSONField;
import lombok.Data;

import java.util.List;

@Data
public class BotConfigOld {
    @JSONField(name = "app_id")
    String appId;
    @JSONField(name = "bot_id")
    String botId;
    String llm;
    // String prompt;

    // Large model parameters
    Double temperature;
    @JSONField(name = "max_tokens")
    Integer maxTokens;
    @JSONField(name = "top_p")
    Integer topP;

    // Knowledge base parameters
    @JSONField(name = "top_k")
    Integer topK;
    Double score;

    @JSONField(name = "is_correlation")
    Integer isCorrelation;
    @JSONField(name = "is_location")
    Integer isLocation;
    List<String> tools;
    List<String> flows;

    @JSONField(name = "patch_id")
    List<String> patchId;

    String domain;
    Object auditing;
    Object history;

    @JSONField(name = "api_url")
    String apiUrl;
}
