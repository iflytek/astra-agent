package com.iflytek.astron.console.hub.entity.maas;

import com.alibaba.fastjson2.JSONObject;
import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;
import org.springframework.format.annotation.DateTimeFormat;

import java.time.LocalDateTime;

@Data
public class MaasTemplate {

    private Long id;
    private JSONObject coreAbilities;
    private JSONObject coreScenarios;
    private Byte isAct;
    private Long maasId;
    private String subtitle;
    private String title;
    private Integer botId;
    private String coverUrl;
    private Long groupId;
    private Integer orderIndex;

    @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private LocalDateTime createTime;

    @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private LocalDateTime updateTime;
}
