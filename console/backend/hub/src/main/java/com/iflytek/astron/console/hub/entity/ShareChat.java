package com.iflytek.astron.console.hub.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDateTime;
import lombok.Data;

@Data
@TableName("share_chat")
@Schema(name = "ShareChat", description = "Conversation sharing information index table")
public class ShareChat {

    @TableId(type = IdType.AUTO)
    private Long id;

    @Schema(description = "Sharing user's UID")
    private String uid;

    @Schema(description = "Key parameter for frontend URL to prevent abuse")
    private String urlKey;

    @Schema(description = "Primary key of shared conversation's chat_list")
    private Long chatId;

    @Schema(description = "Assistant ID for assistant mode, 0 for normal mode")
    private Long botId;

    @Schema(description = "Click count")
    private Integer clickTimes;

    @Schema(description = "Redundant, can limit maximum click count, default -1 means unlimited")
    private Integer maxClickTimes;

    @Schema(description = "Link validity: 0 Invalid, 1 Valid")
    private Integer urlStatus;

    @Schema(description = "Create time")
    private LocalDateTime createTime;

    @Schema(description = "Update time")
    private LocalDateTime updateTime;

    @Schema(description = "Enabled plugin IDs in current conversation list")
    private String enabledPluginIds;

    @Schema(description = "Like count")
    private Integer likeTimes;

    @Schema(description = "IP location when sharing")
    private String ipLocation;
}
