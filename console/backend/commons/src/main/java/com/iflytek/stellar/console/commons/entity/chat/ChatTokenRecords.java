package com.iflytek.astra.console.commons.entity.chat;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDateTime;
import lombok.Data;

@Data
@TableName("chat_token_records")
@Schema(name = "ChatTokenRecords", description = "Chat token records table")
public class ChatTokenRecords {

    @TableId(type = IdType.AUTO)
    private Long id;

    @Schema(description = "Session identifier")
    private String sid;

    @Schema(description = "Number of prompt tokens")
    private Integer promptTokens;

    @Schema(description = "Number of current question tokens")
    private Integer questionTokens;

    @Schema(description = "Number of completion tokens")
    private Integer completionTokens;

    @Schema(description = "Total number of tokens")
    private Integer totalTokens;

    @Schema(description = "Creation time")
    private LocalDateTime createTime;

    @Schema(description = "Update time")
    private LocalDateTime updateTime;
}
