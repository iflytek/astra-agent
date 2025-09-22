package com.iflytek.astra.console.toolkit.entity.table.workflow;

import com.baomidou.mybatisplus.annotation.*;
import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;
import org.springframework.format.annotation.DateTimeFormat;

import java.util.Date;

/**
 * @Author clliu19
 * @Date: 2025/4/23 17:05
 */
@Data
public class McpToolConfig {
    @TableId(type = IdType.AUTO)
    private Long id;
    // Hosting platform ID
    private String mcpId;
    // ID returned by generated short link
    private String serverId;
    // Short link
    private String sortLink;
    private String uid;
    private Integer type;
    private Boolean isDeleted;
    private Boolean customize;
    private String parameters;
    @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private Date createTime;
    @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private Date updateTime;
}
