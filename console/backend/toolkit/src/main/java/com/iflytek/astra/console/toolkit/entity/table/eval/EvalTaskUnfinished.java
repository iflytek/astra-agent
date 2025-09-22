package com.iflytek.astra.console.toolkit.entity.table.eval;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.util.Date;

@Data
@TableName("effect_eval_task_unfinished")
public class EvalTaskUnfinished {
    @TableId(type = IdType.AUTO)
    private Long id;

    private Long evalTaskId;

    private Integer seq;

    private String question;

    private Integer status;

    private Boolean deleted;

    private Date createTime;

    private Date updateTime;

    private String prompt;

    private String dimension;

    private String parameters;
}
