package com.iflytek.astron.console.hub.dto.publish;

import com.fasterxml.jackson.annotation.JsonFormat;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Bot Detail Response DTO
 *
 * @author Omuigix
 */
@Data
@Schema(description = "Bot detail response")
public class BotDetailResponseDto {

    @Schema(description = "Bot ID", example = "123")
    private Integer botId;

    @Schema(description = "Bot name", example = "Customer Service Assistant")
    private String botName;

    @Schema(description = "Bot description", example = "Professional customer service bot")
    private String botDesc;

    @Schema(description = "Version number", example = "1")
    private Integer version;

    @Schema(description = "Publish status", example = "1", allowableValues = {"0", "1"})
    private Integer publishStatus;

    @Schema(description = "Publish channels list", example = "[\"MARKET\", \"API\", \"WECHAT\", \"MCP\"]")
    private List<String> publishChannels;

    @Schema(description = "WeChat publish status", example = "1", allowableValues = {"0", "1"})
    private Integer wechatRelease;

    @Schema(description = "WeChat AppID", example = "wx[16 characters]")
    private String wechatAppid;

    @Schema(description = "Create time", example = "2024-01-01 12:00:00")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createTime;

    @Schema(description = "Update time", example = "2024-01-01 12:00:00")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime updateTime;

    @Schema(description = "User ID", example = "3")
    private Long uid;

    @Schema(description = "Space ID", example = "1")
    private Long spaceId;
}
