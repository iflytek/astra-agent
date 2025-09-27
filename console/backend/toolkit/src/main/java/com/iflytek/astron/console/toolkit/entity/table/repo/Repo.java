package com.iflytek.astron.console.toolkit.entity.table.repo;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.io.Serializable;
import java.util.Date;


/**
 * <p>
 *
 * </p>
 *
 * @author xxzhang23
 * @since 2023-12-11
 */
@Data
@EqualsAndHashCode(callSuper = false)
public class Repo implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * Primary key
     */
    @TableId(type = IdType.AUTO)
    private Long id;


    /**
     * Bot name
     */
    @TableField("`name`")
    private String name;


    private String userId;

    /**
     * appid
     */
    private String appId;


    private String outerRepoId;


    private String coreRepoId;


    /**
     * Description
     */
    private String description;

    /**
     * Avatar icon
     */
    private String icon;


    private String color;


    /**
     * 1:created 2:published 3:offline 4:deleted
     */
    @TableField("`status`")
    private Integer status;


    /**
     * Embedding model
     */
    private String embeddedModel;


    /**
     * Index type: 0-high quality, 1-low quality
     */
    private Integer indexType;

    /**
     * Visibility: 0-visible only to self, 1-visible to partial users
     */
    private Integer visibility;

    /**
     * Source: 0-web created, 1-api created
     */
    private Integer source;

    /**
     * Whether content audit is enabled: 0-disabled, 1-enabled (default)
     */
    private Boolean enableAudit;


    /**
     * Whether deleted: 1-deleted, 0-not deleted
     */
    private Boolean deleted;


    /**
     * Creation time
     */
    private Date createTime;


    /**
     * Modification time
     */
    private Date updateTime;


    private Boolean isTop;

    // Knowledge base type, CBG-RAG / AIUI-RAG2
    private String tag;

    private Long spaceId;

}
