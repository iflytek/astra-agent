package com.iflytek.astron.console.toolkit.entity.table.tool;

import com.baomidou.mybatisplus.annotation.*;
import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.springframework.format.annotation.DateTimeFormat;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * @Author clliu19
 * @Date: 2025/9/23 10:55
 */
@Data
@EqualsAndHashCode(callSuper = false)
@TableName(value = "rpa_info", autoResultMap = true)
public class RpaInfo implements Serializable {

    private static final long serialVersionUID = -9027539519294445000L;
    /**
     * Primary key, starting from 10000
     */
    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * Configuration category
     */
    private String category;


    /**
     * Configuration name
     */
    @TableField("`name`")
    private String name;

    /**
     * Configuration content, value
     */
    @TableField("`value`")
    private String value;

    /**
     * Deletion status: 0 Not deleted, 1 Deleted
     */
    private Integer isDeleted;

    /**
     * Remarks, comments
     */
    private String remarks;
    /**
     * icon
     */
    private String icon;

    /**
     * Create time
     */
    @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private LocalDateTime createTime;

    /**
     * Update time
     */
    @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private LocalDateTime updateTime;
}
