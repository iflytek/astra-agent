package com.iflytek.astra.console.hub.service.chat;

import com.iflytek.astra.console.commons.dto.bot.ChatBotApi;

import java.util.List;

public interface ChatBotApiService {

    List<ChatBotApi> getBotApiList(String uid);

}
