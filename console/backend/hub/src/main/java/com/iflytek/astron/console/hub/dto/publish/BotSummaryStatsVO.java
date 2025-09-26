package com.iflytek.astron.console.hub.dto.publish;

import lombok.Data;

/**
 * 智能体总体statistics数据VO 字段顺序与前端页面显示顺序保持一致：累计会话数、累计用户数、累计TOKEN消耗(k)、累计消息数
 */
@Data
public class BotSummaryStatsVO {

    /**
     * 累计会话数
     */
    private long totalChats;

    /**
     * 累计user数
     */
    private long totalUsers;

    /**
     * 累计TOKEN消耗(k)
     */
    private long totalTokens;

    /**
     * 累计消息数
     */
    private long totalMessages;
}
