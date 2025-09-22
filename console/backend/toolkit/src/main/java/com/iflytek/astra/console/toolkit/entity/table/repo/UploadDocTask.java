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
 * @since 2024-01-09
 */
@Data
@EqualsAndHashCode(callSuper = false)
public class UploadDocTask implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * Primary key
     */
    @TableId(type = IdType.AUTO)
    private Long id;


    /**
     * Task ID
     */
    private String taskId;

    /**
     * Knowledge extraction task ID
     */
    private String extractTaskId;


    /**
     * File ID
     */
    private Long fileId;

    /**
     * botID
     */
    private Long botId;


    /**
     * Knowledge base ID
     */
    private String repoId;


    /**
     * Processing step: 0 - upload file, 1 - parse file, 2 - embed file, 3 - bot bind knowledge base
     */
    private Integer step;


    /**
     * 1 - success, 2 - failure
     */
    @TableField("`status`")
    private Integer status;


    private String reason;


    /**
     * User ID
     */
    private String appId;


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



}
