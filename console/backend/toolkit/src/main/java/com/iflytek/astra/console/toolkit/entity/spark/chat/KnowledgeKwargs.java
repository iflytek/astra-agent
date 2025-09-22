package com.iflytek.astra.console.toolkit.entity.spark.chat;

import com.alibaba.fastjson2.annotation.JSONField;
import lombok.Data;

@Data
public class KnowledgeKwargs {
    @JSONField(name = "top_k")
    Integer topK;
    @JSONField(name = "score_threshold")
    Double scoreThreshold;
}
