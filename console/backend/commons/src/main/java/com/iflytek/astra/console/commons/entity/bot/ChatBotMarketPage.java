package com.iflytek.astra.console.commons.entity.bot;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;
import org.springframework.format.annotation.DateTimeFormat;

import java.time.LocalDateTime;
import java.util.List;

/**
 * BOT market pagination object
 */
@Data
public class ChatBotMarketPage {
    private Integer version;
    private Integer marketBotId;
    private Integer botId;
    private String uid;
    private Long chatId;
    private String title;
    private String botName;

    private Integer botType;

    private String avatar;

    private String prompt;

    private String botDesc;

    private String botNameEn;

    private Integer botStatus;
    private Integer isDelete;

    private String blockReason;

    private String hotNum;

    private Integer showIndex;

    private Integer supportContext;

    /**
     * Whether created by user
     */
    private boolean mine;

    private int isFavorite;

    private Integer enable;

    private boolean hasTemplate;

    private String action;

    private Object extra;

    private String logo;

    private String clientHide;

    private List<String> tags;

    private String creatorName;

    /**
     * Audit time
     */
    @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private LocalDateTime auditTime;

    /**
     * Create time
     */
    @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private LocalDateTime createTime;

    /**
     * Update time
     */
    @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private LocalDateTime updateTime;
}
