package com.iflytek.astron.console.toolkit.entity.dto;

import lombok.Data;

import java.util.List;

@Data
public class FeedbackDto {
    String appId;
    String chatId;
    String botId;
    String sid;
    String action;
    List<String> reason;
    String remark;
}
