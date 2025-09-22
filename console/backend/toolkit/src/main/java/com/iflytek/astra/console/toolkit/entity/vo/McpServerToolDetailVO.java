package com.iflytek.astra.console.toolkit.entity.vo;

import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.annotation.JSONField;
import lombok.Data;

import java.util.List;

/**
 * @Description:
 */
@Data
public class McpServerToolDetailVO {
    /**
     * Tool brief description (short description)
     */
    private String brief;

    /**
     * Tool overview
     */
    private String overview;

    /**
     * Creator (e.g., official, third-party developer)
     */
    private String creator;

    /**
     * Spark platform unique identifier ID
     */
    @JSONField(name = "spark_id")
    private String sparkId;

    /**
     * Creation time (format: yyyy-MM-dd'T'HH:mm:ssXXX, e.g., 2025-04-26T12:01:31+08:00)
     */
    @JSONField(name = "create_time")
    private String createTime;

    /**
     * Tool logo image URL address
     */
    @JSONField(name = "logo_url)")
    private String logoUrl;

    /**
     * MCP tool type (e.g., flow type, function type, etc.)
     */
    @JSONField(name = "mcp_type")
    private String mcpType;

    /**
     * Associated tool list (contains tool input schema, name, description, etc.)
     */
    private JSONArray tools;

    /**
     * Tool detailed description content (complete documentation, including introduction, features,
     * usage guide, etc.)
     */
    private String content;

    /**
     * Tool tags (for categorization and search, e.g., "search", "data aggregation", etc.)
     */
    private List<String> tags;

    /**
     * Record ID (reserved field, may be used for data association or version control)
     */
    @JSONField(name = "record_id")
    private String recordId;

    /**
     * Tool name (e.g., "Aggregated Search")
     */
    private String name;

    /**
     * Tool unique identifier ID
     */
    private String id;

    /**
     * Server URL (tool request address, reserved field)
     */
    @JSONField(name = "server_url")
    private String serverUrl;

}
