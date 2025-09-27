package com.iflytek.astron.console.hub.service.chat;


import com.iflytek.astron.console.commons.entity.chat.ChatListCreateResponse;

public interface ChatRestartService {
    ChatListCreateResponse createNewTreeIndexByRootChatId(Long chatId, String uid, String chatListName);
}
