package com.iflytek.astra.console.hub.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDateTime;
import lombok.Data;

@Data
@TableName("bot_offiaccount_chat")
@Schema(name = "BotOffiaccountChat", description = "WeChat Official Account Q&A Record Table")
public class BotOffiaccountChat {

    @TableId(type = IdType.AUTO)
    private Long id;

    @Schema(description = "WeChat Official Account AppId")
    private String appId;

    @Schema(description = "User ID who subscribed to WeChat Official Account")
    private String openId;

    @Schema(description = "WeChat message ID, equivalent to req_id")
    private Long msgId;

    @Schema(description = "Message sent by user")
    private String req;

    @Schema(description = "Message returned by LLM")
    private String resp;

    @Schema(description = "Session identifier")
    private String sid;

    @Schema(description = "Creation time")
    private LocalDateTime createTime;

    @Schema(description = "Update time")
    private LocalDateTime updateTime;
}
