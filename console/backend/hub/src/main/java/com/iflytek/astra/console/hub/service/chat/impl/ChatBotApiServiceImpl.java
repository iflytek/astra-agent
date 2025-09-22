package com.iflytek.astra.console.hub.service.chat.impl;

import com.iflytek.astra.console.commons.dto.bot.ChatBotApi;
import com.iflytek.astra.console.commons.mapper.bot.ChatBotApiMapper;
import com.iflytek.astra.console.hub.service.chat.ChatBotApiService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;

@Slf4j
@Service
public class ChatBotApiServiceImpl implements ChatBotApiService {

    @Autowired
    private ChatBotApiMapper chatBotApiMapper;

    @Override
    public List<ChatBotApi> getBotApiList(String uid) {
        return chatBotApiMapper.selectListWithVersion(uid);
    }

}
