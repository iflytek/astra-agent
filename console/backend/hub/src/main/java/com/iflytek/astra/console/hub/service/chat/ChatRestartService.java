package com.iflytek.astra.console.hub.service.chat;


import com.iflytek.astra.console.commons.entity.chat.ChatListCreateResponse;

public interface ChatRestartService {
    ChatListCreateResponse createNewTreeIndexByRootChatId(Long chatId, String uid, String chatListName);
}
