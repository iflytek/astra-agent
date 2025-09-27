package com.iflytek.astron.console.toolkit.entity.table.workflow;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.util.Date;

@Data
public class WorkflowFeedback {

    Long id;

    String uid;

    String userName;

    String botId;

    String flowId;

    String sid;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    Date startTime;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    Date endTime;

    Integer costTime;

    Long token;

    String status;

    String errorCode;

    String picUrl;

    String description;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    Date createTime;


}
