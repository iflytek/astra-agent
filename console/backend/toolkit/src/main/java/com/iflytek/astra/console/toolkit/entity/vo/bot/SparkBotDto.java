package com.iflytek.astra.console.toolkit.entity.vo.bot;

import lombok.Data;

@Data
public class SparkBotDto {
    Long id;
    String name;// Bot name
    String desc;// Bot description
    String avatarIcon;// Avatar icon
    String avatarColor;// Avatar color
    String greeting;// Greeting
    Boolean floated;// 0: Default, not floating; 1: Set floating
    String appId;// appID
    boolean commonUser;
    String domain;
    Long publicId;
}
