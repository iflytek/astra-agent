package com.iflytek.astron.console.hub.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Bot Conversation Statistics Entity
 *
 * @author Omuigix
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("bot_conversation_stats")
public class BotConversationStats {

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /**
     * User ID
     */
    private String uid;

    /**
     * Space ID, NULL for personal bots
     */
    private Long spaceId;

    /**
     * Bot ID
     */
    private Integer botId;

    /**
     * Chat ID
     */
    private Long chatId;

    /**
     * Session identifier
     */
    private String sid;

    /**
     * Number of tokens consumed in this conversation
     */
    private Integer tokenConsumed;

    /**
     * Number of message rounds in this conversation
     */
    private Integer messageRounds;

    /**
     * Conversation date
     */
    private LocalDate conversationDate;

    /**
     * Create time
     */
    private LocalDateTime createTime;

    /**
     * Delete status: 0=not deleted, 1=deleted
     */
    private Integer isDelete;

    /**
     * Convenient method to create Builder with preset default values
     */
    public static BotConversationStatsBuilder createBuilder() {
        return BotConversationStats.builder()
                .conversationDate(LocalDate.now())
                .createTime(LocalDateTime.now())
                .isDelete(0);
    }

}
