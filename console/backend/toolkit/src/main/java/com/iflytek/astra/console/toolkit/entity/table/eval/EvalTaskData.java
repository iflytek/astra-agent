package com.iflytek.astra.console.toolkit.entity.table.eval;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.*;

@TableName("effect_eval_task_data")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class EvalTaskData {
    Long evalTaskId;
    String data;
}
