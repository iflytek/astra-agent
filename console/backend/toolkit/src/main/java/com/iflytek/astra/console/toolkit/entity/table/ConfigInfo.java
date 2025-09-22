package com.iflytek.astra.console.toolkit.entity.table;

import com.baomidou.mybatisplus.annotation.*;
import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.springframework.format.annotation.DateTimeFormat;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * <p>
 * Configuration table
 * </p>
 *
 * @author xywang73
 * @since 2022-05-05
 */
@Data
@EqualsAndHashCode(callSuper = false)
@TableName(value = "config_info", autoResultMap = true)
public class ConfigInfo implements Serializable {

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
     * Configuration code, key
     */
    @TableField("`code`")
    private String code;

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
     * Whether effective, 0-inactive, 1-active
     */
    private Integer isValid;

    /**
     * Remarks, comments
     */
    private String remarks;

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
