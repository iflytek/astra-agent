package com.iflytek.astron.console.hub.dto.publish.mcp;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/**
 * MCP Publish Request DTO
 *
 * Corresponds to original interface: publishMCP
 *
 * @author Omuigix
 */
@Data
@Schema(name = "McpPublishRequestDto", description = "MCP publish request")
public class McpPublishRequestDto {

    @NotNull(message = "Bot ID cannot be null")
    @Schema(description = "Bot ID", required = true, example = "4011451")
    private Integer botId;

    @NotBlank(message = "MCP server name cannot be empty")
    @Schema(description = "MCP server name", required = true, example = "Weather MCP Server")
    private String serverName;

    @Schema(description = "MCP server icon URL", example = "https://example.com/icon.png")
    private String icon;

    @Schema(description = "MCP server description", example = "MCP server providing weather query functionality")
    private String description;

    @Schema(description = "MCP server content configuration", example = "weather service configuration")
    private String content;

    @Schema(description = "MCP server URL", example = "https://weather-mcp.example.com")
    private String serverUrl;

    @Schema(description = "MCP service parameter configuration")
    private Object args;

    /**
     * MCP parameter configuration
     */
    @Data
    @Schema(name = "McpArgument", description = "MCP parameter configuration")
    public static class McpArgument {
        @Schema(description = "Parameter ID")
        private String id;

        @Schema(description = "Parameter name")
        private String name;

        @Schema(description = "Parameter type")
        private String type;

        @Schema(description = "Whether required")
        private Boolean required;

        @Schema(description = "Parameter description")
        private String description;

        @Schema(description = "Parameter schema definition")
        private Object schema;

        @Schema(description = "Whether delete is disabled")
        private Boolean deleteDisabled;

        @Schema(description = "Name error message")
        private String nameErrMsg;
    }
}
