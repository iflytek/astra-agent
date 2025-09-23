package com.iflytek.astron.console.toolkit.entity.table.eval;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.util.Date;

@Data
@TableName("effect_eval_task_node_score_data")
public class NodeScoreData {
    @TableId(type = IdType.AUTO)
    Long id;
    Long nodeInfoId;
    Integer score;
    Date createTime;
    Date updateTime;
}
