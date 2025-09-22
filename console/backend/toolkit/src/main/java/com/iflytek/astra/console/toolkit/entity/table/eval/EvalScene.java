package com.iflytek.astra.console.toolkit.entity.table.eval;


import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.util.Date;

@Data
@TableName("effect_eval_scene")
public class EvalScene {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String name;

    private String uid;

    private Boolean isPublic;

    private Boolean deleted;

    private Date createTime;

    private Date updateTime;

    private Long spaceId;

}
