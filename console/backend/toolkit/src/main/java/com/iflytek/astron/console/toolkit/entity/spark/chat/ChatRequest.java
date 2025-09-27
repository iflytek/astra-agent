package com.iflytek.astron.console.toolkit.entity.spark.chat;

import com.alibaba.fastjson2.annotation.JSONField;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class ChatRequest {
    // for chat
    String question;

    Boolean debug;

    @JSONField(name = "chat_history")
    List<ChatRecord> chatHistory = new ArrayList<>();

    @JSONField(name = "bot_config")
    Object botConfig;

    @JSONField
    String uid;

    @JSONField(name = "chat_id")
    String chatId;

}
