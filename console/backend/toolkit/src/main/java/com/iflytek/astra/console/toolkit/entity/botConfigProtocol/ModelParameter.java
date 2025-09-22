package com.iflytek.astra.console.toolkit.entity.botConfigProtocol;

import com.alibaba.fastjson2.annotation.JSONField;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;

@Data
public class ModelParameter implements Serializable {
    // Large model parameters
    BigDecimal temperature;
    @JSONField(name = "max_tokens")
    @JsonProperty("max_tokens")
    Integer maxTokens;
    @JSONField(name = "top_k")
    @JsonProperty("top_k")
    Integer topK;
    @JSONField(name = "question_type")
    @JsonProperty("question_type")
    String questionType = "not_knowledge";
}
