package com.iflytek.astron.console.toolkit.entity.botConfigProtocol;

import com.alibaba.fastjson2.annotation.JSONField;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;

@Data
public class KnowledgeConfig implements Serializable {
    // Knowledge base parameters
    @JSONField(name = "top_k")
    @JsonProperty("top_k")
    Integer topK;

    @JSONField(name = "score_threshold")
    @JsonProperty("score_threshold")
    BigDecimal scoreThreshold;
}
