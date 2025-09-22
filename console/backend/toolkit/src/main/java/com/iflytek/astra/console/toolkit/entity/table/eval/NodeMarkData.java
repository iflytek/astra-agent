package com.iflytek.astra.console.toolkit.entity.table.eval;


import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.util.Date;

@Data
@TableName("effect_eval_task_node_mark_data")
public class NodeMarkData {
    @TableId(type = IdType.AUTO)
    Long id;
    Long nodeInfoId;
    String markData;
    Date createTime;
    Date updateTime;
}
