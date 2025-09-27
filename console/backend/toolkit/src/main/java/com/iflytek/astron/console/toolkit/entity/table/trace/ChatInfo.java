package com.iflytek.astron.console.toolkit.entity.table.trace;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.util.Date;

@Data
@TableName("chat_info")
public class ChatInfo {
    @TableId(type = IdType.AUTO)
    Long id;
    String appId;
    String botId;
    String flowId;
    String sub;
    String caller;
    String uid;
    String sid;
    String question;
    String answer;
    Integer statusCode;
    Integer totalCostTime;
    Integer firstCostTime;
    Integer token;
    Date createTime;
}
