package com.iflytek.astra.console.toolkit.entity.table.bot;

import com.baomidou.mybatisplus.annotation.*;
import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.springframework.format.annotation.DateTimeFormat;

import java.io.Serializable;
import java.sql.Timestamp;


@Data
@EqualsAndHashCode(callSuper = false)
public class SparkBot implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * Primary key
     */
    @TableId(type = IdType.AUTO)
    Long id;

    /**
     * uuid
     */
    String uuid;


    /**
     * Robot name
     */
    @TableField("`name`")
    String name;


    String userId;


    String appId;


    /**
     * Description
     */
    String description;


    /**
     * Avatar icon
     */
    String avatarIcon;


    String color;


    /**
     * Floating window icon
     */
    String floatingIcon;


    /**
     * Greeting message
     */
    String greeting;


    /**
     * Whether set as floating robot 0: Not set 1: Set
     */
    Boolean floated;

    /**
     * Whether deleted: 1-Deleted, 0-Not deleted
     */
    Boolean deleted;


    /**
     * Create time
     */
    @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    Timestamp createTime;


    /**
     * Update time
     */
    @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    Timestamp updateTime;
    /**
     * "Public indicator, 0-No 1-Yes"
     */
    Integer isPublic;
    /**
     * Robot tag
     */
    String botTag;
    /**
     * Usage count
     */
    Long userCount;
    /**
     * Dialog count
     */
    Long dialogCount;
    /**
     * Favorite count
     */
    Integer favoriteCount;
    /**
     * Public Bot ID
     */
    Long publicId;

    String recommendQues;
    Boolean appUpdatable;

    Boolean top;

    @Deprecated
    Long evalSetId;
}
