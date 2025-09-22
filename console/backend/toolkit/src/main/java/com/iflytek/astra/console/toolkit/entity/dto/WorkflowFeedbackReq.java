package com.iflytek.astra.console.toolkit.entity.dto;

import lombok.Data;

@Data
public class WorkflowFeedbackReq {

    String sid;

    String botId;

    String flowId;

    String description;

    String picUrl;
}
