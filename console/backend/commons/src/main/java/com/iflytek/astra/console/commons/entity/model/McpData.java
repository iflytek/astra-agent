package com.iflytek.astra.console.commons.entity.model;

import com.alibaba.fastjson2.JSON;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * @author yun-zhi-ztl
 */
@Data
@TableName("mcp_data")
@Schema(name = "McpData", description = "mcp data table")
public class McpData {

    @TableId(type = IdType.AUTO)
    @Schema(description = "Non-business primary key")
    private Long id;

    @Schema(description = "bot ID")
    private Integer botId;

    @Schema(description = "User ID")
    private String uid;

    @Schema(description = "Space ID")
    private Long spaceId;

    @Schema(description = "MCP Server Name")
    private String serverName;

    @Schema(description = "Description of the MCP server")
    private String description;

    @Schema(description = "Content configuration for the MCP server")
    private String content;

    @Schema(description = "Icon URL for the MCP server")
    private String icon;

    @Schema(description = "URL address of the MCP server")
    private String serverUrl;

    @Schema(description = "Service parameters in JSON format")
    private JSON args;

    @Schema(description = "Associated bot version name")
    private String versionName;

    @Schema(description = "Release status: 0=Unpublished, 1=Published")
    private Integer released;

    @Schema(description = "Deletion flag: 0=Not deleted, 1=Deleted")
    private Integer isDelete;

    @Schema(description = "Creation time")
    private LocalDateTime createTime;

    @Schema(description = "Last update time")
    private LocalDateTime updateTime;
}
