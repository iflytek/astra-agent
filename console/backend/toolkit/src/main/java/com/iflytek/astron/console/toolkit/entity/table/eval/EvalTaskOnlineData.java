package com.iflytek.astron.console.toolkit.entity.table.eval;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

@Data
@TableName("effect_eval_task_online_data")
public class EvalTaskOnlineData {
    @TableId(type = IdType.AUTO)
    Long id;
    Long evalTaskId;
    String dataIds;
}
