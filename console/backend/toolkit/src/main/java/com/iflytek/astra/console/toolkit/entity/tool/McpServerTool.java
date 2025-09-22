package com.iflytek.astra.console.toolkit.entity.tool;

import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.annotation.JSONField;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

/**
 * @Author clliu19
 * @Date: 2025/4/24 09:16
 */
@Data
public class McpServerTool {
    /**
     * Description
     */
    private String brief;
    private String content;
    @JSONField(name = "create_time")
    @JsonProperty("create_time")
    private String createTime;
    private String creator;
    private String id;
    @JSONField(name = "logo_url")
    @JsonProperty("logo_url")
    private String logoUrl;
    private String name;
    private String overview;
    /**
     * Server address
     */
    @JSONField(name = "server_url")
    @JsonProperty("server_url")
    private String serverUrl;
    @JSONField(name = "spark_id")
    @JsonProperty("spark_id")
    private String sparkId;
    @JSONField(name = "flow_id")
    @JsonProperty("flow_id")
    private String flowId;
    @JSONField(name = "record_id")
    @JsonProperty("record_id")
    private String recordId;
    @JSONField(name = "mcp_type")
    @JsonProperty("mcp_type")
    private String mcpType;
    private JSONArray tags;
    private JSONArray tools;
    private Boolean hasConfig = false;
    /**
     * Whether parameters have been updated
     */
    private Boolean param = false;

    private Boolean authorized;

}
