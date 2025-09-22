package com.iflytek.astra.console.toolkit.entity.biz;

import com.alibaba.fastjson2.annotation.JSONField;
import lombok.Data;

@Data
public class BizChatRequest {
    String question;

    @JSONField(name = "chat_id")
    String chatId;

    @JSONField(name = "bot_id")
    String botId;

    @JSONField(name = "sub_chat_flag")
    Boolean subChatFlag;
}
