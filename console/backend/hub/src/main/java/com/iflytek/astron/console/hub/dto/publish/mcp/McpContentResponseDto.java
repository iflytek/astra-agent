package com.iflytek.astron.console.hub.dto.publish.mcp;

import com.fasterxml.jackson.annotation.JsonFormat;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * MCP Content Response DTO
 *
 * Corresponds to the return result of original interface: getMcpContent
 *
 * @author Omuigix
 */
@Data
@Schema(name = "McpContentResponseDto", description = "MCP content response")
public class McpContentResponseDto {

    @Schema(description = "Bot ID")
    private Integer botId;

    @Schema(description = "User ID")
    private String uid;

    @Schema(description = "MCP server name")
    private String serverName;

    @Schema(description = "MCP server description")
    private String description;

    @Schema(description = "MCP server content configuration")
    private String content;

    @Schema(description = "MCP server icon URL")
    private String icon;

    @Schema(description = "MCP server URL")
    private String serverUrl;

    @Schema(description = "MCP service parameter configuration")
    private Object args;

    @Schema(description = "Version name")
    private String versionName;

    @Schema(description = "Publish status: 0=unpublished, 1=published")
    private String released;

    @Schema(description = "Create time")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createTime;
}
