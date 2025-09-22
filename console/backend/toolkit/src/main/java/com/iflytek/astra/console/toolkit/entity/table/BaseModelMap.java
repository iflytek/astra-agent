package com.iflytek.astra.console.toolkit.entity.table;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.util.Date;

@TableName("base_model_map")
@Data
public class BaseModelMap {
    @TableId(type = IdType.AUTO)
    Long id;
    String domain;
    Long baseModelId;
    String baseModelName;
    Date createTime;
}
