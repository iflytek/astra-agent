package com.iflytek.astra.console.hub.dto.bot;

import lombok.Data;

/**
 * Prompt Structure DTO
 */
@Data
public class PromptStructDTO {

    /**
     * Prompt key name
     */
    private String promptKey;

    /**
     * Prompt content
     */
    private String promptValue;
}
