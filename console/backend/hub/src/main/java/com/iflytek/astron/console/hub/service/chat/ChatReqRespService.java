package com.iflytek.astron.console.hub.service.chat;

public interface ChatReqRespService {

    void updateBotChatContext(Long chatId, String uid, Integer botId);
}
