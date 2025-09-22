package com.iflytek.astra.console.hub.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDateTime;
import lombok.Data;

@Data
@TableName("chat_bot_remove")
@Schema(name = "ChatBotRemove", description = "Delisted assistant history table")
public class ChatBotRemove {

    @TableId(type = IdType.AUTO)
    private Integer id;

    @Schema(description = "botId")
    private Integer botId;

    @Schema(description = "Publisher UID")
    private String uid;

    @Schema(description = "Bot name, this is a copy, original is at creator's side")
    private String botName;

    @Schema(description = "Bot type: 1 Custom Assistant, 2 Life Assistant, 3 Workplace Assistant, 4 Marketing Assistant, 5 Writing Expert, 6 Knowledge Expert")
    private Integer botType;

    @Schema(description = "Bot avatar URL")
    private String avatar;

    @Schema(description = "bot_prompt")
    private String prompt;

    @Schema(description = "Bot description")
    private String botDesc;

    @Schema(description = "Reason for rejection")
    private String blockReason;

    @Schema(description = "Application history: 0 Not deleted, 1 Deleted")
    private Integer isDelete;

    @Schema(description = "Review time")
    private LocalDateTime auditTime;

    @Schema(description = "Create time")
    private LocalDateTime createTime;

    @Schema(description = "Update time")
    private LocalDateTime updateTime;
}
