package com.iflytek.astron.console.toolkit.entity.vo;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.iflytek.astron.console.toolkit.entity.biz.external.shelf.LLMServerInfo;
import lombok.Getter;
import lombok.Setter;
import org.springframework.format.annotation.DateTimeFormat;

import java.util.*;

@Setter
@Getter
public class LLMInfoVo extends LLMServerInfo {
    Integer llmSource;
    Long llmId;
    Integer status;
    String info;
    String icon;
    List<String> tag = new ArrayList<>();
    Long modelId;
    String pretrainedModel;
    Integer modelType;
    String color;
    /**
     * Whether it is a thinking model
     */
    Boolean isThink = false;
    /**
     * Whether it is a multimodal model
     */
    Boolean multiMode = false;
    String address;
    String desc;
    private Date createTime;
    private Date updateTime;
    private List<CategoryTreeVO> categoryTree;
    Boolean enabled = true;
    String userName;
    String apiKey;
    /**
     * Shelf status: 0 - on shelf, 1 - pending off shelf, 2 - off shelf
     */
    private Integer shelfStatus;
    /** Off shelf time */
    @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private Date shelfOffTime;
    private Integer acceleratorCount;
    /**
     * Replica configuration
     */
    private Integer replicaCount;

    private String modelPath;
}
