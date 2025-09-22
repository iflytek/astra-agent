package com.iflytek.astra.console.commons.entity.bot;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * @author yingpeng Bot chat parameters
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class ChatBotReqDto {

    /**
     * Question text
     */
    private String ask;

    /**
     * User ID
     */
    private String uid;

    /**
     * Chat window ID
     */
    private Long chatId;

    /**
     * Bot ID
     */
    private Integer botId;

    /**
     * Whether to edit the question
     */
    private Boolean edit;

    /**
     * File URL
     */
    private String url;

    private String workflowVersion;
}
