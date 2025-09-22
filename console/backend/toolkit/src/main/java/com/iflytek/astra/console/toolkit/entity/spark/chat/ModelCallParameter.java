package com.iflytek.astra.console.toolkit.entity.spark.chat;

import com.alibaba.fastjson2.annotation.JSONField;
import lombok.Data;

@Data
public class ModelCallParameter {
    Double temperature;
    @JSONField(name = "max_tokens")
    Integer maxTokens;
    @JSONField(name = "top_k")
    Integer topK;

    @JSONField(name = "question_type")
    String questionType = "not_knowledge";
}
