package com.iflytek.astra.console.commons.entity.chat;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDateTime;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
@TableName("chat_file_req")
@Schema(name = "ChatFileReq", description = "Chat file Q&A binding information")
public class ChatFileReq {

    @TableId(type = IdType.AUTO)
    private Long id;

    @Schema(description = "Document Q&A file ID")
    private String fileId;

    @Schema(description = "Chat ID")
    private Long chatId;

    @Schema(description = "req_id")
    private Long reqId;

    @Schema(description = "Owner UID")
    private String uid;

    @Schema(description = "Create time")
    private LocalDateTime createTime;

    @Schema(description = "Update time")
    private LocalDateTime updateTime;

    @Schema(description = "Client type: 0 Unknown, 1 PC, 2 H5 mainly for statistics")
    private Integer clientType;

    @Schema(description = "Deletion status: 0 Not deleted, 1 Deleted")
    private Integer deleted;

    @Schema(description = "Document type: 0 Long document, 1 Long audio, 2 Long video, 3 OCR")
    private Integer businessType;
}
