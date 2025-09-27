package com.iflytek.astron.console.toolkit.entity.biz;

import com.alibaba.fastjson2.annotation.JSONField;
import lombok.Data;

import java.util.List;

@Data
public class FeedbackRequest {
    @JSONField(name = "app_id")
    String appId;

    @JSONField(name = "request_id")
    String requestId;

    String uid;

    @JSONField(name = "chat_id")
    String chatId;

    @JSONField(name = "bot_id")
    String botId;

    String sid;

    String action;

    List<String> reason;

    String remark;
}
