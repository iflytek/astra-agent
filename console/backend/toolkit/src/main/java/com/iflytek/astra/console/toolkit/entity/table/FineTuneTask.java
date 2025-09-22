package com.iflytek.astra.console.toolkit.entity.table;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.util.Date;

@Data
@TableName("fine_tune_task")
public class FineTuneTask {
    @TableId(type = IdType.AUTO)
    Long id;
    Long optimizeTaskId;
    Long datasetId;
    Long modelId;
    Long fineTuneTaskId;
    String fineTuneTaskRemark;
    Date createTime;
    Date updateTime;

    Long baseModelId;
    String serverName;
    String optimizeNode;
    Integer status;
    Long serverId;
    Integer serverStatus;
}
