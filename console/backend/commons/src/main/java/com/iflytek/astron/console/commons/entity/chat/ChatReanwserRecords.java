package com.iflytek.astron.console.commons.entity.chat;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDateTime;
import lombok.Data;

@Data
@TableName("chat_reanwser_records")
@Schema(name = "ChatReanwserRecords", description = "Chat regenerate response records table")
public class ChatReanwserRecords {

    @TableId(type = IdType.AUTO)
    private Long id;

    @Schema(description = "User ID")
    private String uid;

    @Schema(description = "Chat ID")
    private Long chatId;

    @Schema(description = "Request ID before regeneration, for locating historical context position")
    private Long reqId;

    @Schema(description = "Prompt content")
    private String ask;

    @Schema(description = "Reply content")
    private String answer;

    @Schema(description = "Question record time")
    private LocalDateTime askTime;

    @Schema(description = "Answer record time")
    private LocalDateTime answerTime;

    @Schema(description = "Reply SID")
    private String sid;

    @Schema(description = "Reply type: 0 System, 1 Quick fix (not used by API), 2 Large model, 3 Abort")
    private Integer answerType;

    @Schema(description = "Create time")
    private LocalDateTime createTime;

    @Schema(description = "Update time")
    private LocalDateTime updateTime;
}
