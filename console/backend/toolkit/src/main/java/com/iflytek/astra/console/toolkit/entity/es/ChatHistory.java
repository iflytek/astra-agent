package com.iflytek.astra.console.toolkit.entity.es;

import lombok.*;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ChatHistory {
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
     * Session name: default to the first conversation question
     */
    private String content;
    /**
     * Timestamp
     */
    private Long timestamp;
    /**
     * Status 1: Active 0: Inactive
     */
    private Integer status;
}
