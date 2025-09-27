package com.iflytek.astron.console.toolkit.entity.table.trace;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.util.Date;

@Data
@TableName("feedback_info")
public class FeedbackInfo {
    Long id;
    String appId;
    String sub;
    String uid;
    String chatId;
    String sid;
    String botId;
    String flowId;
    String question;
    String answer;
    String action;
    String reason;
    String remark;
    Date createTime;
}
