package com.iflytek.astron.console.toolkit.entity.biz.modelconfig;

import com.iflytek.astron.console.toolkit.entity.vo.ModelCategoryReq;
import lombok.Data;

import java.util.List;


@Data
public class LocalModelDto {
    private String modelName;
    private String domain;
    private String description;
    private String icon;
    private String color;
    private String uid;
    private Long id;
    /**
     * Category, scenario, language, context configuration
     */
    ModelCategoryReq modelCategoryReq;
    /**
     * Performance configuration
     */
    private Integer acceleratorCount;
    /**
     * Replica configuration
     */
    private Integer replicaCount;
    /**
     * Model file path
     */
    private String modelPath;
    /**
     * Model configuration parameters
     */
    private List<Config> config;
}
