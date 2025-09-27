package com.iflytek.astron.console.toolkit.entity.vo.model;

import lombok.Data;

/**
 * @Author clliu19
 * @Date: 2025/9/13 14:38
 */
@Data
public class ModelDeployVo {
    private String modelName;
    private ResourceRequirements resourceRequirements;
    private Integer replicaCount;
    private Integer contextLength;

    @Data
    public static class ResourceRequirements {
        Integer acceleratorCount;
    }
}
