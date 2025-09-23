package com.iflytek.astron.console.toolkit.entity.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.Map;

/**
 * @Author clliu19
 * @Date: 2025/4/24 14:41
 */
@Data
public class McpPushDto {
    /**
     * Server ID to be called
     */
    @NotNull(message = "mcp_id cannot be empty")
    private String mcpId;
    private String recordId;

    @NotNull(message = "mcp_server_id cannot be empty")
    private String serverName;
    private String serverDesc;
    private Map<String, String> env;
    /**
     * Whether it has custom parameters
     */
    private Boolean customize;
}
