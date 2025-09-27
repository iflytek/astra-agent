package com.iflytek.astron.console.toolkit.entity.spark.chat;

import com.alibaba.fastjson2.JSONObject;
import com.alibaba.fastjson2.annotation.JSONField;
import lombok.Data;

@Data
public class ExtraInfo {
    @JSONField(name = "time_cost")
    Integer timeCost;
    @JSONField(name = "knowledge_origin")
    JSONObject knowledgeOrigin;
}
