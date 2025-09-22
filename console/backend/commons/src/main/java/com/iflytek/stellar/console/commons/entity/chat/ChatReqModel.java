package com.iflytek.astra.console.commons.entity.chat;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDateTime;
import lombok.Data;

@Data
@TableName("chat_req_model")
@Schema(name = "ChatReqModel", description = "Multimodal request table")
public class ChatReqModel {

    @TableId(type = IdType.AUTO)
    private Integer id;

    @Schema(description = "User ID")
    private String uid;

    @Schema(description = "Chat window ID")
    private Long chatId;

    @Schema(description = "Chat request ID")
    private Long chatReqId;

    @Schema(description = "Multimodal type, refer to MultiModelEnum")
    private Integer type;

    @Schema(description = "Resource URL")
    private String url;

    @Schema(description = "Review status")
    private Integer status;

    @Schema(description = "Need history concatenation: 0 No, 1 Yes")
    private Integer needHis;

    @Schema(description = "Multimodal input description")
    private String imgDesc;

    @Schema(description = "Image intent: document or universal natural image")
    private String intention;

    @Schema(description = "OCR recognition result")
    private String ocrResult;

    @Schema(description = "Create time")
    private LocalDateTime createTime;

    @Schema(description = "Modify time")
    private LocalDateTime updateTime;

    @Schema(description = "Multimodal image ID, stores sseId to identify which image for Engineering Academy")
    private String dataId;
}
