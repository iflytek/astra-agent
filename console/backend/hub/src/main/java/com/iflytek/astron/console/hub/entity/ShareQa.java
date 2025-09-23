package com.iflytek.astron.console.hub.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDateTime;
import lombok.Data;

@Data
@TableName("share_qa")
@Schema(name = "ShareQa", description = "Conversation sharing Q&A content table")
public class ShareQa {

    @TableId(type = IdType.AUTO)
    private Long id;

    @Schema(description = "User ID")
    private String uid;

    @Schema(description = "Primary key ID of corresponding share_chat")
    private Long shareChatId;

    @Schema(description = "Question content")
    private String messageQ;

    @Schema(description = "Answer content")
    private String messageA;

    @Schema(description = "Answer SID")
    private String sid;

    @Schema(description = "Validity: 1 Valid, 0 Invalid")
    private Integer showStatus;

    @Schema(description = "Create time")
    private LocalDateTime createTime;

    @Schema(description = "Update time")
    private LocalDateTime updateTime;

    @Schema(description = "User question, chat_req_records primary key ID")
    private Long reqId;

    @Schema(description = "Multimodal question type")
    private Integer reqType;

    @Schema(description = "Multimodal question URL")
    private String reqUrl;

    @Schema(description = "Answer table primary key ID")
    private Long respId;

    @Schema(description = "Multimodal return type")
    private String respType;

    @Schema(description = "Multimodal return URL")
    private String respUrl;

    @Schema(description = "Identifier for direct conversation on sharing page, same function as chatId")
    private String chatKey;
}
