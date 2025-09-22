package com.iflytek.astra.console.commons.entity.bot;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("chat_bot_market")
@Schema(name = "ChatBotMarket", description = "Assistant market table")
public class ChatBotMarket {

    @TableId(type = IdType.AUTO)
    private Integer id;

    @Schema(description = "botId")
    private Integer botId;

    @Schema(description = "Publisher UID")
    private String uid;

    @Schema(description = "Bot name, this is a copy, original is at creator's side")
    private String botName;

    @Schema(description = "Bot type: 1 Custom Assistant, 2 Life Assistant, 3 Workplace Assistant, 4 Marketing Assistant, 5 Writing Expert, 6 Knowledge Expert")
    private Integer botType;

    @Schema(description = "Bot avatar")
    private String avatar;

    @Schema(description = "PC chat background image")
    private String pcBackground;

    @Schema(description = "Mobile chat background image")
    private String appBackground;

    @Schema(description = "Background image color scheme: 0 Light, 1 Dark")
    private Integer backgroundColor;

    @Schema(description = "bot_prompt")
    private String prompt;

    @Schema(description = "Opening statement")
    private String prologue;

    @Schema(description = "Whether to show prompt to others: 1 Show, 0 Don't show")
    private Integer showOthers;

    @Schema(description = "Bot description")
    private String botDesc;

    @Schema(description = "Bot status: 0 Delisted, 1 Under review, 2 Approved, 3 Rejected, 4 Modification under review (to be displayed)")
    private Integer botStatus;

    @Schema(description = "Reason for rejection")
    private String blockReason;

    @Schema(description = "Popularity, can be customized for sorting")
    private Integer hotNum;

    @Schema(description = "Application history: 0 Not deleted, 1 Deleted")
    private Integer isDelete;

    @Schema(description = "Show on homepage recommendation: 0 Don't show, 1 Show")
    private Integer showIndex;

    @Schema(description = "Manually set hottest bot position")
    private Integer sortHot;

    @Schema(description = "Manually set latest bot position")
    private Integer sortLatest;

    @Schema(description = "Review time")
    private LocalDateTime auditTime;

    @Schema(description = "Create time")
    private LocalDateTime createTime;

    @Schema(description = "Update time")
    private LocalDateTime updateTime;

    @Schema(description = "Multi-turn conversation support: 1 Support, 0 Not supported")
    private Integer supportContext;

    @Schema(description = "Corresponding large model version, 13, 65, unit: billion")
    private Integer version;

    @Schema(description = "Homepage recommended assistant weight, higher number ranks higher")
    private Integer showWeight;

    @Schema(description = "Score given upon approval")
    private Integer score;

    @Schema(description = "Hidden on certain clients")
    private String clientHide;

    @Schema(description = "Model type")
    private String model;

    @Schema(description = "Used tools")
    private String openedTool;
}
