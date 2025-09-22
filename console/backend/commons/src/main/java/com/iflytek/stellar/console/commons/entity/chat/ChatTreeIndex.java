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
@TableName("chat_tree_index")
@Schema(name = "ChatTreeIndex", description = "Conversation history tree link information")
public class ChatTreeIndex {

    @TableId(type = IdType.AUTO)
    @Schema(description = "Primary key ID")
    private Long id;

    @Schema(description = "Root session ID")
    private Long rootChatId;

    @Schema(description = "Parent session ID")
    private Long parentChatId;

    @Schema(description = "Child session ID")
    private Long childChatId;

    @Schema(description = "User ID")
    private String uid;

    @Schema(description = "Create time")
    private LocalDateTime createTime;

    @Schema(description = "Update time")
    private LocalDateTime updateTime;
}
