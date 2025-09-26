package com.iflytek.astron.console.hub.service.publish;

import com.iflytek.astron.console.hub.dto.publish.mcp.McpContentResponseDto;
import com.iflytek.astron.console.hub.dto.publish.mcp.McpPublishRequestDto;

/**
 * MCP Service Interface
 *
 * @author Omuigix
 */
public interface McpService {

    /**
     * Get bot MCP content (corresponds to original interface: getMcpContent)
     *
     * @param botId Bot ID
     * @param currentUid Current user ID
     * @param spaceId Space ID
     * @return MCP content
     */
    McpContentResponseDto getMcpContent(Integer botId, String currentUid, Long spaceId);

    /**
     * Publish bot to MCP (corresponds to original interface: publishMCP)
     *
     * @param request Publish request
     * @param currentUid Current user ID
     * @param spaceId Space ID
     */
    void publishMcp(McpPublishRequestDto request, String currentUid, Long spaceId);
}
