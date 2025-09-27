package com.iflytek.astron.console.toolkit.entity.spark.chat;

import com.alibaba.fastjson2.annotation.JSONField;
import lombok.Data;

@Data
public class Payload {
    @JSONField(name = "chat_id")
    String chatId;

    Message message;

    Object extra;

}
