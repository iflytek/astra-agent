package com.iflytek.astra.console.commons.entity.chat;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDateTime;
import lombok.Data;

@Data
@TableName("chat_resp_model")
@Schema(name = "ChatRespModel", description = "Multimodal response records table")
public class ChatRespModel {

    @TableId(type = IdType.AUTO)
    private Long id;

    @Schema(description = "User ID")
    private String uid;

    @Schema(description = "Chat window ID")
    private Long chatId;

    @Schema(description = "Chat question ID, multimodal records will be stored before answers, so use reqid for association")
    private Long reqId;

    @Schema(description = "Multimodal return content")
    private String content;

    @Schema(description = "Multimodal output type: text, image, audio, video")
    private String type;

    @Schema(description = "Need history concatenation: 0 No, 1 Yes")
    private Integer needHis;

    @Schema(description = "Multimodal resource URL address")
    private String url;

    @Schema(description = "Resource status: 0 Available, 1 Unavailable")
    private Integer status;

    @Schema(description = "Create time")
    private LocalDateTime createTime;

    @Schema(description = "Modify time")
    private LocalDateTime updateTime;

    @Schema(description = "Large model generated resource ID, needs to be passed back for history concatenation")
    private String dataId;

    @Schema(description = "Watermark resource URL address")
    private String waterUrl;
}
