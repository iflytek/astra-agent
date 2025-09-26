package com.iflytek.astron.console.commons.dto.bot;

import lombok.Data;

/**
 * Bot Model DTO for API response
 */
@Data
public class BotModelDto {
    private Long modelId;
    private String modelName;
    private String modelDomain;
    private String modelIcon;
    private Boolean isCustom = true;
}
