package com.iflytek.astron.console.toolkit.entity.dto;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import com.fasterxml.jackson.annotation.JsonSetter;
import jakarta.validation.constraints.NotNull;
import lombok.Data;


/**
 * @Author clliu19
 * @Date: 2025/4/7 20:05
 */
@Data
public class McpToolReq {
    /**
     * ID of the server to call
     */
    @NotNull(message = "mcp_server_id cannot be empty")
    private String mcpServerId;
    /**
     * URL of the server to call, takes priority over mcp_server_id
     */
    private String mcpServerUrl;
    /**
     * Name of the tool to call
     */
    @NotNull(message = "tool_name cannot be empty")
    private String toolName;
    /**
     * Parameters to pass to the tool
     */
    private JSONObject toolArgs;

    private String toolId;

    /**
     * Custom setter to handle both string and object formats for toolArgs
     */
    @JsonSetter("toolArgs")
    public void setToolArgs(Object toolArgs) {
        if (toolArgs == null) {
            this.toolArgs = null;
        } else if (toolArgs instanceof String) {
            // If it's a string, try to parse it as JSON
            try {
                Object parsed = JSON.parse((String) toolArgs);
                if (parsed instanceof JSONObject) {
                    this.toolArgs = (JSONObject) parsed;
                } else {
                    // If it's not a JSONObject (e.g., array), wrap it in a JSONObject
                    JSONObject wrapper = new JSONObject();
                    wrapper.put("args", parsed);
                    this.toolArgs = wrapper;
                }
            } catch (Exception e) {
                // If parsing fails, treat as a simple string value
                JSONObject wrapper = new JSONObject();
                wrapper.put("value", toolArgs);
                this.toolArgs = wrapper;
            }
        } else if (toolArgs instanceof JSONObject) {
            this.toolArgs = (JSONObject) toolArgs;
        } else {
            // For other types, convert to JSONObject
            try {
                @SuppressWarnings("unchecked")
                java.util.Map<String, Object> map = (java.util.Map<String, Object>) toolArgs;
                this.toolArgs = new JSONObject(map);
            } catch (ClassCastException e) {
                // If it's not a Map, wrap it in a JSONObject
                JSONObject wrapper = new JSONObject();
                wrapper.put("value", toolArgs);
                this.toolArgs = wrapper;
            }
        }
    }
}
