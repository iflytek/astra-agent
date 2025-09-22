package com.iflytek.astra.console.toolkit.entity.table.repo;

import com.baomidou.mybatisplus.annotation.*;
import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.springframework.format.annotation.DateTimeFormat;

import java.io.Serializable;
import java.sql.Timestamp;


/**
 * <p>
 *
 * </p>
 *
 * @author xxzhang23
 * @since 2023-12-13
 */
@Data
@EqualsAndHashCode(callSuper = false)
public class ExtractKnowledgeTask implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * Primary key
     */
    @TableId(type = IdType.AUTO)
    private Long id;


    /**
     * Index method 0: High quality 1: Low quality
     */
    private Long fileId;


    /**
     * Bot name
     */
    private String taskId;


    /**
     * 0: Default 1: Success 2: Failed
     */
    @TableField("`status`")
    private Integer status;


    private String reason;


    /**
     * Index method 0: High quality 1: Low quality
     */
    private String userId;


    /**
     * Create time
     */
    @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private Timestamp createTime;


    /**
     * Update time
     */
    @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private Timestamp updateTime;

    /**
     * Parse configuration
     */
    // @TableField("slice_config")
    // private String sliceConfig;

    /**
     * 0: Start parsing 1: Parsing completed 2: Start embedding 3: Embedding completed
     */
    @TableField("task_status")
    private Integer taskStatus;

}
