package com.iflytek.astron.console.toolkit.entity.table.tool;

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
public class ToolBox implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * Primary key
     */
    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * Core system tool identifier
     */
    private String toolId;


    /**
     * Tool name
     */
    @TableField("`name`")
    private String name;


    /**
     * Tool description
     */
    private String description;

    /**
     * Avatar icon
     */
    private String icon;


    /**
     * User ID
     */
    private String userId;

    /**
     * Space ID
     */
    private Long spaceId;


    /**
     * appid
     */
    private String appId;


    /**
     * Request endpoint
     */
    private String endPoint;


    /**
     * Request method
     */
    private String method;


    /**
     * Web protocol
     */
    private String webSchema;


    /**
     * Protocol
     */
    @TableField("`schema`")
    private String schema;

    /**
     * Visibility: 0-visible only to self, 1-visible to partial users
     */
    private Integer visibility;


    /**
     * Whether deleted: 1-deleted, 0-not deleted
     */
    private Boolean deleted;


    /**
     * Creation time
     */
    @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private Timestamp createTime;


    /**
     * Modification time
     */
    @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private Timestamp updateTime;


    private Boolean isPublic;
    /**
     * Favorite count
     */

    private Integer favoriteCount;
    /**
     * Usage count
     */

    private Integer usageCount;

    private String toolTag;

    private String operationId;

    Integer creationMethod;

    Integer authType;

    String authInfo;

    Integer top;

    Integer source;

    String displaySource;

    String avatarColor;

    /**
     * Status: 0-draft, 1-official
     */
    Integer status = 0;

    String version;

    String temporaryData;
}
