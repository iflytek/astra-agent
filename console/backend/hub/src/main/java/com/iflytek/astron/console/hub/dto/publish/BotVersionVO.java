package com.iflytek.astron.console.hub.dto.publish;

import com.fasterxml.jackson.annotation.JsonIgnore;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * Workflow Version Information VO
 *
 * @author Omuigix
 */
@Data
@Schema(description = "Workflow version information")
public class BotVersionVO {

    @Schema(description = "Version record ID", example = "12345")
    private Long id;

    @Schema(description = "Version name", example = "v1.0")
    private String name;

    @Schema(description = "Version number", example = "20241201123456789")
    private String versionNum;

    @Schema(description = "Version description", example = "Fixed workflow logic issues")
    private String description;

    @Schema(description = "Workflow ID", example = "flow123")
    private String flowId;

    @Schema(description = "Publish channels", example = "MARKET,API")
    private String publishChannels;

    @Schema(description = "Publish time", example = "2024-12-01T10:30:00")
    private LocalDateTime createdTime;

    @Schema(description = "Update time", example = "2024-12-01T10:35:00")
    private LocalDateTime updatedTime;

    @Schema(description = "Whether it's the current version", example = "true")
    private Boolean isCurrent;

    @Schema(description = "Bot ID", example = "50")
    private String botId;

    // Temporary fields for internal processing, not exposed externally
    @JsonIgnore
    @Schema(hidden = true)
    private String data;

    @JsonIgnore
    @Schema(hidden = true)
    private String sysData;
}
