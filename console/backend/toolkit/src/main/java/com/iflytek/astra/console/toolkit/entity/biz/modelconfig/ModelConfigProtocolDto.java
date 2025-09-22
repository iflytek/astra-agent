package com.iflytek.astra.console.toolkit.entity.biz.modelconfig;

import lombok.Data;

import java.util.List;

@Data
public class ModelConfigProtocolDto {
    ConversationStarter conversationStarter;
    Enabled feedback;
    // Model model;
    Models models;
    String prePrompt;
    RepoConfigs repoConfigs;
    Enabled retrieverResource;
    Enabled speechToText;
    TextToSpeech textToSpeech;
    Enabled suggestedQuestionsAfterAnswer;
    List<Tool> tools;
    List<Flow> flows;
    List<Object> userInputForm;
}
