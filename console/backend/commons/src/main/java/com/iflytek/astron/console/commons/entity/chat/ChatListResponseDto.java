package com.iflytek.astron.console.commons.entity.chat;

import com.fasterxml.jackson.annotation.JsonFormat;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;
import org.springframework.format.annotation.DateTimeFormat;

import java.time.LocalDateTime;
import java.util.List;

@Getter
@Setter
@ToString
@Schema(name = "ChatListResponseDto", description = "Chat list response DTO")
public class ChatListResponseDto {

    @Schema(description = "Chat list ID")
    private Long id;

    @Schema(description = "User ID")
    private String uid;

    @Schema(description = "Chat list title")
    private String title;

    @Schema(description = "Whether deleted, 0 not deleted, 1 deleted")
    private Integer isDelete;

    @Schema(description = "Whether available, 0 not available, 1 available")
    private Integer enable;

    @Schema(description = "Chat ID")
    private Long chatId;

    @Schema(description = "Enabled plugin ID list")
    private String enabledPluginIds;

    @Schema(description = "Bot description")
    private String botDesc;

    @Schema(description = "Bot English description")
    private String botDescEn;

    @Schema(description = "Popularity count")
    private Integer hotNum;

    @Schema(description = "Bot type")
    private String botType;

    @Schema(description = "Bot title")
    private String botTitle;

    @Schema(description = "Bot English title")
    private String botTitleEn;

    @Schema(description = "Bot ID")
    private Integer botId;

    @Schema(description = "Bot status")
    private Integer botStatus;

    @Schema(description = "Market bot ID")
    private Integer marketBotId;

    @Schema(description = "Bot avatar")
    private String botAvatar;

    @Schema(description = "Market bot user ID")
    private Long marketBotUid;

    @Schema(description = "Bot user ID")
    private Long botUid;

    @Schema(description = "Client hide")
    private String clientHide;

    @Schema(description = "Creator name")
    private String creatorName;

    @Schema(description = "Creation time")
    @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private LocalDateTime createTime;

    @Schema(description = "Update time")
    @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private LocalDateTime updateTime;

    @Schema(description = "Album visibility")
    private Integer albumVisible;

    @Schema(description = "Support context")
    private Integer supportContext;

    @Schema(description = "Whether pinned")
    private Integer sticky;

    @Schema(description = "Whether favorited")
    private Integer isFavorite;

    @Schema(description = "Action")
    private String action;

    @Schema(description = "Extra information")
    private Object extra;

    @Schema(description = "Block reason")
    private String blockReason;

    @Schema(description = "Version")
    private Integer version;

    @Schema(description = "Tag list")
    private List<String> tags;

    @Schema(description = "Whether recommended")
    private Boolean recommend;

    @Schema(description = "Virtual agent ID")
    private Long virtualAgentId;

    public String getClientHide() {
        if (clientHide == null || clientHide.trim().isEmpty()) {
            return "";
        }
        return clientHide;
    }
}
