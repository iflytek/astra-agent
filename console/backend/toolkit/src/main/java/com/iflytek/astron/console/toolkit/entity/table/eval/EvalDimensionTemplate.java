package com.iflytek.astron.console.toolkit.entity.table.eval;


import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.util.Date;

@Data
@TableName("effect_eval_dimension_template")
public class EvalDimensionTemplate {
    @TableId(type = IdType.AUTO)
    Long id;
    String uid;
    String name;
    String description;
    Integer dimensionCount;
    Boolean deleted;
    Date createTime;
    Date updateTime;
}
