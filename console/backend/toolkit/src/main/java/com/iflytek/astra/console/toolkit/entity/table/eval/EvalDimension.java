package com.iflytek.astra.console.toolkit.entity.table.eval;


import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.util.Date;

@Data
@TableName("effect_eval_dimension_v2")
public class EvalDimension {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String sceneIds;

    private String uid;

    private String name;

    private String description;

    private String prompt;

    private Boolean isPublic;

    private Boolean deleted;

    private Date createTime;

    private Date updateTime;

    private Long spaceId;
}
