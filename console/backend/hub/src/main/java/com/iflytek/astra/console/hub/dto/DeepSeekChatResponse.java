package com.iflytek.astra.console.hub.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.List;

@Data
@Schema(description = "DeepSeek large model chat response")
public class DeepSeekChatResponse {

    @Schema(description = "Response ID")
    private String id;

    @Schema(description = "Object type")
    private String object;

    @Schema(description = "Creation timestamp")
    private Long created;

    @Schema(description = "Model name")
    private String model;

    @Schema(description = "Choices list")
    private List<Choice> choices;

    @Schema(description = "Usage statistics")
    private Usage usage;

    @Schema(description = "System fingerprint")
    private String systemFingerprint;

    @Data
    @Schema(description = "Choice item")
    public static class Choice {
        @Schema(description = "Choice index")
        private Integer index;

        @Schema(description = "Delta message")
        private Delta delta;

        @Schema(description = "Complete message")
        private Message message;

        @Schema(description = "Log probabilities")
        private Object logprobs;

        @Schema(description = "Finish reason")
        private String finishReason;
    }

    @Data
    @Schema(description = "Delta message")
    public static class Delta {
        @Schema(description = "Role")
        private String role;

        @Schema(description = "Content")
        private String content;
    }

    @Data
    @Schema(description = "Complete message")
    public static class Message {
        @Schema(description = "Role")
        private String role;

        @Schema(description = "Content")
        private String content;
    }

    @Data
    @Schema(description = "Usage statistics")
    public static class Usage {
        @Schema(description = "Prompt tokens count")
        private Integer promptTokens;

        @Schema(description = "Completion tokens count")
        private Integer completionTokens;

        @Schema(description = "Total tokens count")
        private Integer totalTokens;

        @Schema(description = "Prompt cache hit tokens count")
        private Integer promptCacheHitTokens;

        @Schema(description = "Prompt cache miss tokens count")
        private Integer promptCacheMissTokens;
    }
}
