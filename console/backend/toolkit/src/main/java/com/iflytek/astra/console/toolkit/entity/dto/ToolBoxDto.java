package com.iflytek.astra.console.toolkit.entity.dto;

import lombok.Data;

import java.util.List;

@Data
public class ToolBoxDto {
    Long id;
    String name;
    String description;
    String avatarIcon;// Avatar icon

    String avatarColor;

    String toolId;

    /**
     * URL address
     */
    String endPoint;

    /**
     * get post delete patch
     */
    String method;

    /**
     * Web protocol data
     */
    String webSchema;


    List<String> uids;

    Integer updateType;// 1: Basic info 2: Schema

    /**
     * 1=Form creation, 2=Schema
     */
    Integer creationMethod;

    /**
     * 1=No authorization required, 2=Service
     */
    Integer authType;

    String authInfo;

    String version;

    String toolTag;

    Boolean isPublic;
}
