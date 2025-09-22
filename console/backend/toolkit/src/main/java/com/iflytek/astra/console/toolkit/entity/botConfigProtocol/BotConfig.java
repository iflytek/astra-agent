package com.iflytek.astra.console.toolkit.entity.botConfigProtocol;

import com.alibaba.fastjson2.annotation.JSONField;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.io.Serializable;
import java.util.List;

@Data
public class BotConfig implements Serializable {
    @JSONField(name = "app_id")
    @JsonProperty("app_id")
    String appId;
    @JSONField(name = "bot_id")
    @JsonProperty("bot_id")
    String botId;
    @JSONField(name = "model_config")
    @JsonProperty("model_config")
    ModelConfig modelConfig;
    @JSONField(name = "regular_config")
    @JsonProperty("regular_config")
    RegularConfig regularConfig = new RegularConfig();

    @JSONField(name = "knowledge_config")
    @JsonProperty("knowledge_config")
    KnowledgeConfig knowledgeConfig;

    @JSONField(name = "tool_ids")
    @JsonProperty("tool_ids")
    List<String> toolIds;
    @JSONField(name = "flow_ids")
    @JsonProperty("flow_ids")
    List<String> flowIds;
    /**
     * MCP server ID list
     */
    @JSONField(name = "mcp_server_ids")
    @JsonProperty("mcp_server_ids")
    List<String> mcpServerIds;
    /**
     * MCP server URL list
     */
    @JSONField(name = "mcp_server_urls")
    @JsonProperty("mcp_server_urls")
    List<String> mcpServerUrls;
}
