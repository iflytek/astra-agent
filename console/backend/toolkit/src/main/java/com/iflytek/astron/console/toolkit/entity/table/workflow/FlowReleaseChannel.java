package com.iflytek.astron.console.toolkit.entity.table.workflow;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.util.Date;

@TableName("flow_release_channel")
@Data
public class FlowReleaseChannel {
    @TableId(type = IdType.AUTO)
    Long id;
    String flowId;
    String channel;
    Date createTime;
    Date updateTime;
    Long infoId;
    Integer status;
}
