package com.iflytek.astra.console.hub.dto.bot;

import lombok.Data;

import java.util.List;

/**
 * One-sentence assistant generation response DTO
 *
 */
@Data
public class BotGenerationDTO {

    /**
     * Assistant name
     */
    private String botName;

    /**
     * Assistant description
     */
    private String botDesc;

    /**
     * Assistant type 10-Workplace, 13-Learning, 14-Creative, 15-Programming, 17-Lifestyle, 39-Health
     */
    private Integer botType;

    /**
     * Prompt type
     */
    private Integer promptType;

    /**
     * Whether to support context (0-not supported, 1-supported)
     */
    private Integer supportContext;

    /**
     * Whether to support system (0-not supported, 1-supported)
     */
    private Integer supportSystem;

    /**
     * Version number
     */
    private Integer version;

    /**
     * Assistant status
     */
    private Integer botStatus;

    /**
     * Prompt structure list
     */
    private List<PromptStructDTO> promptStructList;

    /**
     * Input example list
     */
    private List<String> inputExample;

    /**
     * Avatar URL (optional)
     */
    private String avatar;
}
