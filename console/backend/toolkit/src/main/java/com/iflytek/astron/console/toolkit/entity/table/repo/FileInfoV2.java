package com.iflytek.astron.console.toolkit.entity.table.repo;

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
 * @since 2023-12-07
 */
@Data
@EqualsAndHashCode(callSuper = false)
public class FileInfoV2 implements Serializable {

    private static final long serialVersionUID = 1L;
    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * UUID (i.e., docId)
     */
    private String uuid;

    /**
     * Last UUID during splice (i.e., docId)
     */
    private String lastUuid;


    /**
     * User ID
     */
    private String uid;

    /**
     * Knowledge base ID
     */
    private Long repoId;



    /**
     * File name
     */
    @TableField("`name`")
    private String name;


    /**
     * File storage address
     */
    private String address;


    /**
     * File size
     */
    private Long size;

    /**
     * File character length
     */
    private Long charCount;


    /**
     * File type
     */
    private String type;


    /**
     * Knowledge base build status 0 - Success 1 - Building 10001 - Resource acquisition failed 10002 -
     * Content parsing failed 10003 - Knowledge building failed 10004 - Resource size exceeds limit,
     * currently only supports files under 10M
     */
    @TableField("`status`")
    private Integer status;

    /**
     * 0: Disabled 1: Enabled
     */
    private Integer enabled;


    /**
     * Failure reason
     */
    private String reason;

    /**
     * Slice configuration
     */
    private String sliceConfig;

    /**
     * Currently effective slice configuration
     */
    private String currentSliceConfig;

    /**
     * Identifies the folder to which the file belongs
     */
    private Long pid;

    /**
     * File source AIUI-RAG2 (default) CBG-RAG
     */
    private String source;

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
     * File download URL
     */
    @TableField(exist = false)
    private String downloadUrl;

    private Long spaceId;

}
