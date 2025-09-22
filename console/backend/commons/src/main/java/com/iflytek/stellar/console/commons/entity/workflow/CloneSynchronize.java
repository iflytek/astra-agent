package com.iflytek.astra.console.commons.entity.workflow;

import lombok.Data;

@Data
public class CloneSynchronize {

    public String uid;
    public String originId;
    public Long currentId;
    public Long spaceId;
    public String flowId;
}
