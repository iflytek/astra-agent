package com.iflytek.astron.console.commons.service.data;

import com.iflytek.astron.console.commons.dto.llm.SparkChatRequest;
import com.iflytek.astron.console.commons.entity.chat.ChatModelMeta;
import com.iflytek.astron.console.commons.entity.chat.ChatReqModelDto;
import com.iflytek.astron.console.commons.entity.chat.ChatRequestDtoList;

import java.util.List;

/**
 * Chat history service interface
 */
public interface ChatHistoryService {

    /**
     * Get conversation history for system assistant
     *
     * @param uid User ID
     * @param chatId Chat ID
     * @return Message list
     */
    List<SparkChatRequest.MessageDto> getSystemBotHistory(String uid, Long chatId);

    /**
     * Get chat history records
     *
     * @param uid User ID
     * @param chatId Chat ID
     * @param reqList Request list
     * @return Chat request list
     */
    ChatRequestDtoList getHistory(String uid, Long chatId, List<ChatReqModelDto> reqList);

    /**
     * Convert URL to large model multimodal protocol content array
     *
     * @param url
     * @param ask
     * @return
     */
    List<ChatModelMeta> urlToArray(String url, String ask);
}
