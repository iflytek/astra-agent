package com.iflytek.astron.console.commons.entity.chat;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDateTime;
import lombok.Data;

@Data
@TableName("chat_trace_source")
@Schema(name = "ChatTraceSource", description = "Chat trace source information storage table")
public class ChatTraceSource {

    @TableId(type = IdType.AUTO)
    private Long id;

    @Schema(description = "User ID")
    private String uid;

    @Schema(description = "Chat ID")
    private Long chatId;

    @Schema(description = "Request ID")
    private Long reqId;

    @Schema(description = "Trace content, JSON array of one frame")
    private String content;

    @Schema(description = "Trace type: search trace, others to be supplemented")
    private String type;

    @Schema(description = "Create time")
    private LocalDateTime createTime;

    @Schema(description = "Update time")
    private LocalDateTime updateTime;
}
