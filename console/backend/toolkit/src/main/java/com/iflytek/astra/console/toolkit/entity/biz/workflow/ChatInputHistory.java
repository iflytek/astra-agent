package com.iflytek.astra.console.toolkit.entity.biz.workflow;

import com.alibaba.fastjson2.annotation.JSONField;
import com.iflytek.astra.console.toolkit.entity.spark.chat.ChatRecord;
import lombok.Data;

import java.util.List;

@Data
public class ChatInputHistory {
    @JSONField(name = "nodeID")
    String nodeId;
    @JSONField(name = "chat_history")
    List<ChatRecord> chatHistory;
}
