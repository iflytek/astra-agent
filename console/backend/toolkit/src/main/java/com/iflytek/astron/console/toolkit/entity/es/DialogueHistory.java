package com.iflytek.astron.console.toolkit.entity.es;

import lombok.*;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DialogueHistory {
    private String uid;
    /**
     * appId
     */
    private String appId;
    /**
     * botId
     */
    private String botId;
    /**
     * chatId
     */
    private String chatId;

    /**
     * sid
     */
    private String sid;

    /**
     * Question
     */
    private String question;

    /**
     * Answer
     */
    private String answer;

    /**
     * Timestamp
     */
    private Long timestamp;

    /**
     * Metadata
     */
    private Object metadata;

    private Boolean subChatFlag;
}
