package com.iflytek.astron.console.commons.entity.chat;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class ChatListCreateRequest {
    private String chatListName;

    private Integer botId;

    // 1 is domestic version name, 2 is overseas version name
    private Integer showType;

    private String chatFileId;

    /**
     * Special window identifier, see SpecialChatEnum type for details
     */
    private Integer specialType;
}
