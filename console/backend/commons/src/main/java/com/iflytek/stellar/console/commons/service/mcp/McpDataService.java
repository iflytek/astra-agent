package com.iflytek.astra.console.commons.service.mcp;

import com.iflytek.astra.console.commons.entity.model.McpData;

import java.util.List;

/**
 * @author wowo_zZ
 * @since 2025/9/11 09:56
 **/

public interface McpDataService {

    List<McpData> getMcpByUid(String uid);

    McpData insert(McpData mcpData);

    McpData getMcp(Long botId);
}
