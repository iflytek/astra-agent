package com.iflytek.astron.console.toolkit.entity.spark.chat;

import com.alibaba.fastjson2.JSONObject;
import com.alibaba.fastjson2.annotation.JSONField;
import lombok.Data;

@Data
public class ToolUpstreamKwargs {
    @JSONField(name = "tool_id")
    String toolId;

    @JSONField(name = "tool_upstream_kwargs")
    JSONObject toolUpstreamKwargs = new JSONObject().fluentPut("userAccount", "mingduan");
}
