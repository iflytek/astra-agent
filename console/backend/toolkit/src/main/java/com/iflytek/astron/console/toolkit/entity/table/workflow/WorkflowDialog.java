package com.iflytek.astron.console.toolkit.entity.table.workflow;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import lombok.Data;

import java.util.Date;

@Data
public class WorkflowDialog {
    @TableId(type = IdType.AUTO)
    Long id;
    String uid;
    Long workflowId;
    String question;
    String answer;
    String data;
    Date createTime;
    Boolean deleted;
    String sid;
    Integer type;
    String questionItem;
    String answerItem;
    String chatId;
}
