package com.iflytek.astra.console.toolkit.entity.biz.workflow;

import com.alibaba.fastjson2.JSONObject;
import lombok.Data;

@Data
public class BizChatInput {
    JSONObject inputs;
    String chatId;
    Boolean debugger;
    Boolean close;
    Boolean regen;
}
