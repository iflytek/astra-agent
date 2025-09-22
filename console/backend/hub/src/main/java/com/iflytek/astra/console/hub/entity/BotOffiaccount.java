package com.iflytek.astra.console.hub.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDateTime;
import lombok.Data;

@Data
@TableName("bot_offiaccount")
@Schema(name = "BotOffiaccount", description = "Bot and WeChat official account binding information")
public class BotOffiaccount {

    @TableId(type = IdType.AUTO)
    private Long id;

    @Schema(description = "User ID")
    private String uid;

    @Schema(description = "Bot ID")
    private Long botId;

    @Schema(description = "WeChat official account App ID")
    private String appid;

    @Schema(description = "Release type: 1 WeChat official account")
    private Integer releaseType;

    @Schema(description = "Binding status: 0-not bound, 1-bound, 2-unbound")
    private Integer status;

    @Schema(description = "Creation time")
    private LocalDateTime createTime;

    @Schema(description = "Update time")
    private LocalDateTime updateTime;
}
