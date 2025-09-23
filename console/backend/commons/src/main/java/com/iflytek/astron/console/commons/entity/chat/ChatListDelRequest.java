package com.iflytek.astron.console.commons.entity.chat;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class ChatListDelRequest {

    private Long chatListId;

    private Integer changeSticky;

}
