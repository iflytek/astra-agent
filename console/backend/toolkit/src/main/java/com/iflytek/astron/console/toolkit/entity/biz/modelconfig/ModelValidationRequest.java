package com.iflytek.astron.console.toolkit.entity.biz.modelconfig;

import com.iflytek.astron.console.toolkit.entity.vo.ModelCategoryReq;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.List;

@Data
public class ModelValidationRequest {
    @NotNull(message = "Model endpoint cannot be empty")
    private String endpoint;
    @NotNull(message = "API key cannot be empty")
    private String apiKey;
    private String modelName;
    /**
     * Model domain
     */
    @NotNull(message = "Model cannot be empty")
    private String domain;
    private String description;
    private List<String> tag;
    private String icon;
    private String color;
    private String uid;
    private Long id;
    /**
     * Model configuration parameters
     */
    private List<Config> config;
    /**
     * Determine if API key is changed
     *
     */
    private Boolean apiKeyMasked = false;
    ModelCategoryReq modelCategoryReq;
}
