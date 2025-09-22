package com.iflytek.astra.console.commons.entity.bot;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

@Data
@Schema(description = "Create bot model")
public class BotCreateForm {
    @Schema(description = "Bot ID, passed when editing, not passed when creating")
    private Integer botId;

    @Schema(description = "Bot type")
    private int botType;

    @Size(max = 32, message = "Bot name cannot exceed 32 characters")
    @Schema(description = "Bot name")
    private String name;

    @Size(max = 2560, message = "Avatar link length cannot exceed 2560 characters")
    @Pattern(regexp = "^https://.*\\.(jpg|png|jpeg).*", message = "Avatar must be a valid HTTPS image link")
    @Schema(description = "Avatar")
    private String avatar;

    @Size(max = 2560, message = "Background image link length cannot exceed 2560 characters")
    @Pattern(regexp = "^https://.*\\.(jpg|png|jpeg).*", message = "PC background image must be a valid HTTPS image link")
    @Schema(description = "PC chat background image")
    private String pcBackground;

    @Size(max = 2560, message = "Background image link length cannot exceed 2560 characters")
    @Pattern(regexp = "^https://.*\\.(jpg|png|jpeg).*", message = "Mobile background image must be a valid HTTPS image link")
    @Schema(description = "Mobile chat background image")
    private String appBackground;

    @Size(max = 200, message = "Feature description cannot exceed 200 characters")
    @Schema(description = "Feature description")
    private String botDesc;

    @Schema(description = "Input template")
    private String botTemplate;

    @Schema(description = "Multi-turn conversation | Whether to support context")
    private Integer supportContext;

    @Schema(description = "Whether to support document Q&A")
    private Integer supportDocument;

    @Schema(description = "Whether to support system instructions: 0 not supported, 1 supported")
    private Integer supportSystem;

    @Schema(description = "Whether to strictly follow document Q&A")
    private Integer accordStrictly = 0;

    @Schema(description = "Dataset ID")
    private List<Long> datasetList;

    @Schema(description = "Professional dataset ID")
    private List<Long> maasDatasetList;

    @Schema(description = "0 custom instruction 1 structured instruction")
    private Integer promptType;

    @Schema(description = "Opening statement")
    private String prompt;

    @Schema(description = "Assistant instruction, only needs to be passed when selecting custom instruction (promptType=0)")
    private String prologue;

    @Schema(description = "Input example")
    private List<String> inputExample;

    @Schema(description = "Custom parameters")
    private List<PromptStruct> promptStructList;

    @Schema(description = "Selected model")
    private String model;

    private int clientType;

    /**
     * Chinese voice actor
     */
    private String vcnCn;

    /**
     * English voice actor
     */
    private String vcnEn;

    /**
     * Voice actor speech speed
     */
    private int vcnSpeed;

    /**
     * Whether it's generated from a single sentence
     */
    private int isSentence;

    @Schema(description = "Enabled tools, joined by comma, e.g.: ifly_search,text_to_image,codeinterpreter")
    private String openedTool;

    @Schema(description = "Background image color scheme: 0 Light, 1 Dark")
    private Integer backgroundColor;

    @Schema(description = "System instruction status")
    private Integer promptSystem;

    @Schema(description = "Document upload support: 0 Not supported, 1 Supported")
    private Integer supportUpload;

    @Schema(description = "Assistant name in English")
    private String botNameEn;

    @Schema(description = "Assistant description in English")
    private String botDescEn;

    @Schema(description = "Opening statement - English")
    private String prologueEn;

    @Schema(description = "Recommended questions - English")
    private List<String> inputExampleEn;

    @Schema(description = "Hidden on certain clients")
    private String clientHide;

    @Schema(description = "Virtual personality type")
    private Integer virtualBotType;

    @Schema(description = "virtual_agent_list primary key")
    private Long virtualAgentId;

    @Schema(description = "Style type: 0 Original image, 1 Business elite, 2 Casual moment")
    private Integer style;

    @Schema(description = "Background setting")
    private String background;

    @Schema(description = "Character setting")
    private String virtualCharacter;

    @Schema(description = "mass_bot_id")
    private String massBotId;

    @Data
    public static class PromptStruct {
        private String promptKey;
        private String promptValue;
    }
}
