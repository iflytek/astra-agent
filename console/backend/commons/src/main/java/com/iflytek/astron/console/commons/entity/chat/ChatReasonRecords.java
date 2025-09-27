package com.iflytek.astron.console.commons.entity.chat;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDateTime;
import lombok.Data;

@Data
@TableName("chat_reason_records")
@Schema(name = "ChatReasonRecords", description = "Chat reasoning records table")
public class ChatReasonRecords {

    @TableId(type = IdType.AUTO)
    private Long id;

    @Schema(description = "User ID")
    private String uid;

    @Schema(description = "Chat session ID")
    private Long chatId;

    @Schema(description = "Request ID")
    private Long reqId;

    @Schema(description = "Reasoning content")
    private String content;

    @Schema(description = "Thinking elapsed time (seconds)")
    private Long thinkingElapsedSecs;

    @Schema(description = "Reasoning type (e.g., x1_math)")
    private String type;

    @Schema(description = "Create time")
    private LocalDateTime createTime;

    @Schema(description = "Update time")
    private LocalDateTime updateTime;
}
