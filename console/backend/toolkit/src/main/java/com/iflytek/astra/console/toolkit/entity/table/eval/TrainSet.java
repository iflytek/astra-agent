package com.iflytek.astra.console.toolkit.entity.table.eval;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import lombok.experimental.Accessors;

import java.util.Date;

@Data
@TableName("train_set")
@Accessors(chain = true)
public class TrainSet {
    @TableId(type = IdType.AUTO)
    Long id;
    String uid;
    String name;
    String description;
    String currentVer;
    Integer verCount;
    Boolean deleted;
    Date createTime;
    Date updateTime;
    Integer applicationType;
    Long applicationId;

    @Deprecated
    String nodeInfo;
}
