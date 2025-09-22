package com.iflytek.astra.console.toolkit.entity.table.bot;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.util.Date;

@Data
@TableName("bot_model_bind")
public class BotModelBind {
    @TableId(type = IdType.AUTO)
    Long id;
    String uid;
    Long botId;
    String appId;
    String llmServiceId;
    String domain;
    String patchId;
    String modelName;
    Date createTime;
    Integer modelType;
}
