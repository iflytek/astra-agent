package com.iflytek.astra.console.commons.entity.chat;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDateTime;
import lombok.Data;
import org.springframework.data.annotation.Transient;

@Data
@TableName("chat_req_records")
@Schema(name = "ChatReqRecords", description = "Chat request records table")
public class ChatReqRecords {

    @TableId(type = IdType.AUTO)
    private Long id;

    @Schema(description = "Chat ID")
    private Long chatId;

    @Schema(description = "User ID")
    private String uid;

    @Schema(description = "Question content")
    private String message;

    @Schema(description = "Client type when user asks: 0 Unknown, 1 PC, 2 H5 mainly for statistics")
    private Integer clientType;

    @Schema(description = "Multimodal related ID")
    private Integer modelId;

    @Schema(description = "Create time")
    private LocalDateTime createTime;

    @Schema(description = "Update time")
    private LocalDateTime updateTime;

    @Schema(description = "cmp_core.BigdataServicesMonitorDaily")
    private Integer dateStamp;

    @Schema(description = "Bot new context: 1 Yes, 0 No")
    private Integer newContext;

    /**
     * Need underline
     */
    @Transient
    @TableField(exist = false)
    private boolean needDraw;
}
