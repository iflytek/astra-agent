package com.iflytek.astron.console.hub.dto.publish;

import com.fasterxml.jackson.annotation.JsonFormat;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Bot Publish Information DTO
 *
 * Used for data transfer in API responses
 *
 * @author Omuigix
 */
@Data
public class BotPublishInfoDto {

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
     * Bot avatar URL
     */
    private String avatar;

    /**
     * Bot type: 1=instruction-based, 3=workflow
     */
    private Integer botType;

    /**
     * Bot version
     */
    private Integer version;

    /**
     * Publish status code: 0=offline, 1=online
     */
    @Schema(description = "Publish status", example = "1", allowableValues = {"0", "1"})
    private Integer publishStatus;

    /**
     * Publish channels list
     */
    private List<String> publishChannels;

    /**
     * Creator user ID
     */
    private Long uid;

    /**
     * Space ID
     */
    private Long spaceId;

    /**
     * Create time
     */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createTime;

    /**
     * Update time
     */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime updateTime;
}
