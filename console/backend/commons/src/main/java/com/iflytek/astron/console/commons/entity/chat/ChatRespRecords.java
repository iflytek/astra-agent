package com.iflytek.astron.console.commons.entity.chat;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDateTime;
import lombok.Data;

@Data
@TableName("chat_resp_records")
@Schema(name = "ChatRespRecords", description = "Chat response records table")
public class ChatRespRecords {

    @TableId(type = IdType.AUTO)
    private Long id;

    @Schema(description = "User ID")
    private String uid;

    @Schema(description = "Chat ID")
    private Long chatId;

    @Schema(description = "Chat question ID, one question corresponds to one answer")
    private Long reqId;

    @Schema(description = "Engine serial number SID")
    private String sid;

    @Schema(description = "Answer type: 1 Hotfix, 2 GPT")
    private Integer answerType;

    @Schema(description = "Answer message")
    private String message;

    @Schema(description = "Create time")
    private LocalDateTime createTime;

    @Schema(description = "Update time")
    private LocalDateTime updateTime;

    @Schema(description = "cmp_core.BigdataServicesMonitorDaily")
    private Integer dateStamp;
}
