package com.iflytek.astra.console.toolkit.entity.table.eval;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.util.Date;

@Data
@TableName("train_set_ver")
public class TrainSetVer {
    @TableId(type = IdType.AUTO)
    Long id;
    Long trainSetId;
    String ver;
    String description;
    String filename;
    String storageAddr;
    Boolean deleted;
    Date createTime;
    Date updateTime;
    String nodeInfo;
}
