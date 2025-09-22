package com.iflytek.astra.console.hub.service.chat;

import com.iflytek.astra.console.commons.entity.bot.ChatBotBase;
import com.iflytek.astra.console.commons.entity.bot.ChatBotReqDto;
import com.iflytek.astra.console.commons.entity.chat.ChatListCreateResponse;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;

public interface BotChatService {

    void chatMessageBot(ChatBotReqDto chatBotReqDto, SseEmitter sseEmitter, String sseId, String workflowOperation, String workflowVersion);

    void reAnswerMessageBot(Long requestId, Integer botId, SseEmitter sseEmitter, String sseId);

    void debugChatMessageBot(String text, String prompt, List<String> messages, String uid, String openedTool, String model, SseEmitter sseEmitter, String sseId);

    ChatListCreateResponse clear(Long chatId, String uid, Integer botId, ChatBotBase botBase);

}
