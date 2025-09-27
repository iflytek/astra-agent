package com.iflytek.astron.console.commons.entity.workflow;

import lombok.Data;

@Data
public class CloneSynchronize {

    private String uid;
    private String originId;
    private Long currentId;
    private Long spaceId;
    private String flowId;
}
