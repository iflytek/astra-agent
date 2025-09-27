package com.iflytek.astron.console.toolkit.entity.table.model;

import com.baomidou.mybatisplus.annotation.*;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.iflytek.astron.console.toolkit.entity.vo.CategoryTreeVO;
import lombok.Data;
import org.springframework.format.annotation.DateTimeFormat;

import java.io.Serializable;
import java.util.Date;
import java.util.List;

/**
 * model_common entity class
 *
 * Corresponding table: ai_cloud_spark_bot.model_common
 */
@Data
public class ModelCommon implements Serializable {

    private static final long serialVersionUID = 1L;

    /** Auto-increment primary key */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** Model name */
    private String name;

    /** Model description */
    @TableField(value = "`desc`")
    private String desc;

    /** Model introduction */
    private String intro;

    /** Username */
    private String userName;

    /** User avatar */
    private String userAvatar;

    /** Model type: 0 Open source large model 1 Spark */
    private Integer modelType;

    /** Service ID */
    private String serviceId;

    /** Server ID */
    private String serverId;

    /** Domain */
    private String domain;

    /** Authorization channel */
    private String licChannel;

    /** LLM source */
    private String llmSource;

    /** Model access URL */
    private String url;
    /** Model access HTTP URL */
    private String httpUrl;

    /** Type */
    @TableField(value = "`type`")
    private Integer type;

    /** Source */
    @TableField(value = "`source`")
    private Integer source;

    /** Whether has thinking capability */
    private Boolean isThink;

    /** Whether supports multimodal */
    private Boolean multiMode;

    /** Whether deleted */
    private Boolean isDelete;

    /** Creator */
    private Long createBy;

    /** User control ID */
    private String uid;

    /** Disclaimer */
    private String disclaimer;
    /** Model configuration */
    private String config;

    /** Updater */
    private Long updateBy;
    /**
     * Shelf status: 0 on shelf, 1 to be taken off shelf, 2 off shelf
     */
    private Integer shelfStatus;

    /** Creation time */
    @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private Date createTime;

    /** Update time */
    @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private Date updateTime;
    /** Off shelf time */
    @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private Date shelfOffTime;

    @TableField(exist = false)
    private List<CategoryTreeVO> categoryTree;


}
