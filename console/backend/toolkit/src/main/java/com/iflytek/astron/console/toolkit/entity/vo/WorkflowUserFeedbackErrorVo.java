package com.iflytek.astron.console.toolkit.entity.vo;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.util.Date;

@Data
public class WorkflowUserFeedbackErrorVo {

    private String uid;

    private Long errorCode;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private Date errorTime;

    private String errorMsg;
}
