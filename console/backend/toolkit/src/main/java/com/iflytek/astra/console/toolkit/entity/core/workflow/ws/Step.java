package com.iflytek.astra.console.toolkit.entity.core.workflow.ws;

import com.alibaba.fastjson2.annotation.JSONField;
import lombok.Data;

@Data
public class Step {
    String name;
    String type;
    @JSONField(name = "chat_id")
    String aliasName;
    @JSONField(name = "chat_id")
    String nodeType;
}
