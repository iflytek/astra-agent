package com.iflytek.astron.console.toolkit.entity.table.eval;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.util.Date;

@Data
@TableName("effect_eval_set_ver")
public class EvalSetVer {

    @TableId(type = IdType.AUTO)
    Long id;
    Long evalSetId;
    String ver;
    String description;
    String filename;
    String storageAddr;
    Boolean deleted;
    Date createTime;
    Date updateTime;

    @Deprecated
    Boolean basicVer;
}
