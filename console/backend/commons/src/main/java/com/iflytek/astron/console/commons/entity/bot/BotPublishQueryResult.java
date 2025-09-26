package com.iflytek.astron.console.commons.entity.bot;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * Bot Publish Query Result Entity
 *
 * Used to receive multi-table join query results, following technical standards: - Use entity
 * classes instead of Map to receive query results - Automatic camelCase conversion, field names
 * correspond to AS aliases in SQL
 *
 * @author Omuigix
 */
@Data
public class BotPublishQueryResult {

    /**
     * User ID
     */
    private Long uid;

    /**
     * Space ID
     */
    private Long spaceId;

    /**
     * Bot ID
     */
    private Integer botId;

    /**
     * Bot name
     */
    private String botName;

    /**
     * Bot description
     */
    private String botDesc;

    /**
     * Version number
     */
    private Integer version;

    /**
     * Publish status (status after CASE processing)
     */
    private Integer botStatus;

    /**
     * Create time
     */
    private LocalDateTime createTime;

    /**
     * Update time
     */
    private LocalDateTime updateTime;

    /**
     * Publish channels (comma-separated string: MARKET,API,WECHAT,MCP)
     */
    private String publishChannels;
}
