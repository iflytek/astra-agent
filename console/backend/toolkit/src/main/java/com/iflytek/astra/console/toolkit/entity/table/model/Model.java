package com.iflytek.astra.console.toolkit.entity.table.model;

import com.baomidou.mybatisplus.annotation.*;
import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;
import org.springframework.format.annotation.DateTimeFormat;

import java.util.Date;

/**
 * @Author clliu19
 * @Date: 2025/4/11 17:05
 */
@Data
public class Model {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String name;
    @TableField("`desc`")
    private String desc;
    private Integer source;
    private String uid;
    // 1-custom 2- local model
    private Integer type;
    private Long subType;
    private String content;
    @TableLogic(value = "0", delval = "1")
    private Boolean isDeleted;
    @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private Date createTime;
    @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private Date updateTime;
    private String imageUrl;
    private String docUrl;
    private String remark;
    private Integer sort;
    private String channel;
    private String apiKey;
    private String tag;
    private String domain;
    private String url;
    private String color;
    private String config;
    private Long spaceId;
    /**
     * Whether enabled
     */
    private Boolean enable;
    /**
     * Model publish status, default 1 published 1 published running 2 pending 3 failed 4 initializing 5
     * notExist 6 terminating
     */
    private Integer status;
}
