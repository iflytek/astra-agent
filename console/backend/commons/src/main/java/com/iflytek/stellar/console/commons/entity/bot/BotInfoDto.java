package com.iflytek.astra.console.commons.entity.bot;

import com.alibaba.fastjson2.JSONObject;
import lombok.Data;

import java.util.List;
import java.util.Set;

@Data
public class BotInfoDto {

    /** Bot ID */
    private Integer botId;

    /** Bot name */
    private String botName;

    /** Bot description */
    private String botDesc;

    /** Bot avatar */
    private String avatar;

    /** Bot type */
    private Integer botType;

    /** Version number */
    private Integer version;

    /** Opening statement */
    private String prologue;

    /** Input examples */
    private List<String> inputExample;

    /** Supported upload file types */
    private List<JSONObject> supportUpload;

    /** Supported upload configuration */
    private List<JSONObject> supportUploadConfig;

    /** Bot status */
    private Integer botStatus;

    /** Popularity */
    private String hotNum;

    /** Whether favorited (0: not favorited, 1: favorited) */
    private Integer isFavorite;

    /** Whether created by user */
    private Boolean mine;

    /** Whether added to chat list (0: not added, 1: added) */
    private Integer isAdd;

    /** Chat ID */
    private Long chatId;

    /** Bot logo */
    private String logo;

    /** Dataset list */
    private List<String> dataset;

    /** Template ID */
    private Integer templateId;

    /** Creator avatar */
    private String creatorAvatar;

    /** Creator nickname */
    private String creatorNickname;

    /** Bot web status */
    private Integer botwebStatus;

    /** Channel */
    private String channel;

    /** Plugin ID */
    private String pluginId;

    /** Special bot code */
    private Set<String> specialBotCode;

    /** Tag list */
    private List<String> tags;

    /** PC background */
    private String pcBackground;

    /** Workflow version */
    private String workflowVersion;

    /** Whether liked (0: not liked, 1: liked) */
    private Integer isLike;

    /** Whether recommended (0: not recommended, 1: recommended) */
    private Integer isRecommend;

    /** User ID */
    private String uid;

    private Long flowId;
    private Long maasId;

    private String model;
}
