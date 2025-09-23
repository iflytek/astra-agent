package com.iflytek.astron.console.commons.dto.bot;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("chat_bot_api")
@Schema(name = "ChatBotApi", description = "Assistant API capability information table")
public class ChatBotApi {

    @TableId(type = IdType.AUTO)
    private Long id;

    @Schema(description = "User ID")
    private String uid;

    @Schema(description = "Assistant ID")
    private Integer botId;

    @Schema(description = "Engineering Academy Assistant ID")
    private String assistantId;

    @Schema(description = "APP ID associated with assistant API capabilities")
    private String appId;

    @Schema(description = "API secret")
    private String apiSecret;

    @Schema(description = "API secret")
    private String apiKey;

    @Schema(description = "Path of assistant API capabilities")
    private String apiPath;

    @Schema(description = "Prompt for assistant API capabilities")
    private String prompt;

    @Schema(description = "Plugin IDs, separated by commas")
    private String pluginId;

    @Schema(description = "Embedding IDs, separated by commas")
    private String embeddingId;

    @Schema(description = "Description")
    private String description;

    @Schema(description = "Create time")
    private LocalDateTime createTime;

    @Schema(description = "Update time")
    private LocalDateTime updateTime;
}
