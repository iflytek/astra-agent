package com.iflytek.astron.console.hub.service.chat.impl;

import com.iflytek.astron.console.commons.service.data.ChatDataService;
import com.iflytek.astron.console.hub.service.chat.ChatReqRespService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
@Slf4j
public class ChatReqRespServiceImpl implements ChatReqRespService {

    @Autowired
    private ChatDataService chatDataService;

    @Override
    public void updateBotChatContext(Long chatId, String uid, Integer botId) {
        chatDataService.updateNewContextByUidAndChatId(uid, chatId);
    }
}
