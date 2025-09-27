package com.iflytek.astron.console.commons.entity.workflow;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import lombok.Data;

import java.util.Date;

@Data
public class Workflow {
    @TableId(type = IdType.AUTO)
    Long id;
    String appId;
    String flowId;
    String name;
    String description;
    String uid;
    Boolean deleted;
    Boolean isPublic;
    Date createTime;
    Date updateTime;
    String data;
    String publishedData;
    String avatarIcon;
    String avatarColor;
    Integer status;
    Boolean canPublish;
    Boolean appUpdatable;
    @TableField("`order`")
    Integer order;
    String edgeType;
    Integer source;

    @Deprecated
    Long evalSetId;

    Boolean editing;
    Boolean evalPageFirstTime;

    /**
     * Advanced configuration
     */
    String advancedConfig;
    String ext;
    Integer category;

    Long spaceId;
}
