package com.iflytek.astron.console.hub.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDateTime;
import lombok.Data;

@Data
@TableName("bot_offiaccount_record")
@Schema(name = "BotOffiaccountRecord", description = "Bot Publishing Operation Record Table")
public class BotOffiaccountRecord {

    @TableId(type = IdType.AUTO)
    private Long id;

    @Schema(description = "Bot ID")
    private Long botId;

    @Schema(description = "Official Account AppId")
    private String appid;

    @Schema(description = "Operation type: 1 Bind, 2 Unbind")
    private Integer authType;

    @Schema(description = "Creation time")
    private LocalDateTime createTime;

    @Schema(description = "Update time")
    private LocalDateTime updateTime;
}
