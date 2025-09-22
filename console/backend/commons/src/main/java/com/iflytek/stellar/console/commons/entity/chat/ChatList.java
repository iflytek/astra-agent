package com.iflytek.astra.console.commons.entity.chat;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDateTime;
import lombok.Data;

@Data
@TableName("chat_list")
@Schema(name = "ChatList", description = "Chat list table")
public class ChatList {

    @TableId(type = IdType.AUTO)
    @Schema(description = "Non-business primary key")
    private Long id;

    @Schema(description = "User ID")
    private String uid;

    @Schema(description = "Chat list title")
    private String title;

    @Schema(description = "Deletion status: 0 Not delete, 1 Delete")
    private Integer isDelete;

    @Schema(description = "Enable status: 1 Available, 0 Unavailable")
    private Integer enable;

    @Schema(description = "Assistant ID")
    private Integer botId;

    @Schema(description = "Pin status: 0 Not pinned, 1 Pinned")
    private Integer sticky;

    @Schema(description = "Create time")
    private LocalDateTime createTime;

    @Schema(description = "Modify time")
    private LocalDateTime updateTime;

    @Schema(description = "Multimodal: 0 No, 1 Yes")
    private Integer isModel;

    @Schema(description = "Enabled plugin IDs in current conversation list")
    private String enabledPluginIds;

    @Schema(description = "Is assistant web app: 0 No, 1 Yes")
    private Integer isBotweb;

    @Schema(description = "Document Q&A ID")
    private String fileId;

    @Schema(description = "Is root chat: 1 Yes, 0 No")
    private Integer rootFlag;

    @Schema(description = "Personality chat_personality_base primary key ID")
    private Long personalityId;

    @Schema(description = "Group chat primary key ID, if 0 means not group chat")
    private Long gclId;
}
