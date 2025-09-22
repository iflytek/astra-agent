package com.iflytek.astra.console.toolkit.entity.table.eval;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.util.Date;

@Data
@TableName("effect_eval_task_online_log")
public class EffectEvalTaskOnlineLog {
    @TableId(type = IdType.AUTO)
    private Long id;

    private Long evalTaskId;

    private String sid;

    private String question;

    private Integer statusCode;

    private Boolean deleted;

    private Date createTime;

    private Date updateTime;

    private String answer;

    private String expectedAnswer;

    private Integer seq;

}
