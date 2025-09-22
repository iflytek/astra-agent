package com.iflytek.astra.console.toolkit.entity.table.eval;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.util.*;

@Data
@TableName("effect_model_optimize_task")
public class ModelOptimizeTask {
    @TableId(type = IdType.AUTO)
    Long id;
    String uid;
    String name;
    Long applicationId;
    Integer applicationType;
    @TableField("`status`")
    Integer status;
    Date createTime;
    Date updateTime;
    Boolean deleted;
    Long baseModelId;
    String optimizeNode;
    String trainSetVerId;
    String dataIds;

    // Deprecated fields due to refactoring
    @Deprecated
    String evalTaskId;
    @Deprecated
    String nodeInfoIds;
    @Deprecated
    String dataSource;
    @Deprecated
    String serverName;
}
