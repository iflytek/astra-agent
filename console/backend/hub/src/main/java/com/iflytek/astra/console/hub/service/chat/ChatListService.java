package com.iflytek.astra.console.hub.service.chat;

import com.iflytek.astra.console.commons.entity.bot.BotInfoDto;
import com.iflytek.astra.console.commons.entity.chat.ChatBotListDto;
import com.iflytek.astra.console.commons.entity.chat.ChatListCreateResponse;
import com.iflytek.astra.console.commons.entity.chat.ChatListResponseDto;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

public interface ChatListService {

    @Transactional(rollbackFor = Exception.class, propagation = Propagation.REQUIRED)
    ChatListCreateResponse createChatListForRestart(String uid, String chatListName, Integer botId, long chatId);

    /**
     * Get all chats in descending order by latest conversation time (can exclude certain types of
     * conversations)
     *
     * @return
     */
    List<ChatListResponseDto> allChatList(String uid, String type);

    /**
     * Get user's bot chat list by uid, maximum length is CHAT_LIST_LENGTH_LIMIT
     */
    List<ChatBotListDto> getBotChatList(String uid);

    /**
     * Create chat list
     *
     * @param uid
     * @param chatListName
     * @return
     */
    ChatListCreateResponse createChatList(String uid, String chatListName, Integer botId);

    /**
     * Logically delete user chat list
     *
     * @param chatListId
     * @param uid
     * @return
     */
    boolean logicDeleteChatList(Long chatListId, String uid);

    /**
     * Get chat information data by botId
     *
     * @param uid
     * @param botId
     * @return
     */
    BotInfoDto getBotInfo(HttpServletRequest request, String uid, Integer botId, String workflowVersion);

    /**
     * Clear history button to recreate conversation
     *
     * @param uid
     * @param chatListName
     * @param botId
     * @return
     */
    ChatListCreateResponse createRestartChat(String uid, String chatListName, Integer botId);
}
