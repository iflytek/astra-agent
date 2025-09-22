package com.iflytek.astra.console.commons.entity.chat;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDateTime;
import lombok.Data;

@Data
@TableName("chat_resp_alltool_data")
@Schema(name = "ChatRespAlltoolData", description = "Large model returns alltools paragraph data, one Q&A returns multiple alltools paragraph data")
public class ChatRespAlltoolData {

    @TableId(type = IdType.AUTO)
    private Long id;

    @Schema(description = "User ID")
    private String uid;

    @Schema(description = "Chat ID")
    private Long chatId;

    @Schema(description = "Request ID")
    private Long reqId;

    @Schema(description = "Sequence number, like p1, p2")
    private String seqNo;

    @Schema(description = "Alltools structured data for each frame return to be stored")
    private String toolData;

    @Schema(description = "Alltools type name")
    private String toolName;

    @Schema(description = "Create time")
    private LocalDateTime createTime;

    @Schema(description = "Update time")
    private LocalDateTime updateTime;
}
