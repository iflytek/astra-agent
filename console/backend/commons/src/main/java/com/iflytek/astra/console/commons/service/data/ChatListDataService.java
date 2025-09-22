package com.iflytek.astra.console.commons.service.data;

import com.iflytek.astra.console.commons.entity.bot.ChatBotBase;
import com.iflytek.astra.console.commons.entity.chat.ChatBotListDto;
import com.iflytek.astra.console.commons.entity.chat.ChatList;
import com.iflytek.astra.console.commons.entity.chat.ChatTreeIndex;

import java.util.List;

public interface ChatListDataService {

    /**
     * Query chat list by user ID and chat ID
     *
     * @param uid User ID
     * @param chatId Chat ID (corresponding to the primary key id of ChatList)
     * @return Chat list information
     */
    ChatList findByUidAndChatId(String uid, Long chatId);

    List<ChatTreeIndex> findChatTreeIndexByChatIdOrderById(Long rootChatId);

    ChatList createChat(ChatList chatList);

    ChatTreeIndex createChatTreeIndex(ChatTreeIndex chatTreeIndex);

    List<ChatTreeIndex> getListByRootChatId(Long rootChatId, String uid);

    List<ChatBotListDto> getBotChatList(String uid);

    /**
     * Find the latest enabled chat list for specified user and bot
     *
     * @param uid User ID
     * @param botId Bot ID
     * @return Latest chat list, or null if not exists
     */
    ChatList findLatestEnabledChatByUserAndBot(String uid, Integer botId);

    /**
     * Reactivate chat list (set is_delete=0)
     *
     * @param id Chat list ID
     * @return Number of rows affected by update
     */
    int reactivateChat(Long id);

    /**
     * Batch reactivate chat lists (set is_delete=0)
     *
     * @param chatIdList Collection of chat list IDs
     * @return Number of rows affected by update
     */
    int reactivateChatBatch(List<Long> chatIdList);

    long addRootTree(Long curChatId, String uid);

    /**
     * Update user bot chat list status to inactive
     *
     * @param uid User ID
     * @param botId Bot ID
     * @return Number of rows affected by update
     */
    int deactivateChatBotList(String uid, Integer botId);

    /**
     * Get all related chat tree indexes by child chat ID
     *
     * @param childChatId Child chat ID
     * @param uid User ID
     * @return List of chat tree indexes
     */
    List<ChatTreeIndex> getAllListByChildChatId(Long childChatId, String uid);

    /**
     * Delete chat list by ID
     *
     * @param id Chat list ID
     * @return Number of rows affected by deletion
     */
    int deleteById(Long id);

    /**
     * Batch delete chat lists
     *
     * @param idList Collection of chat list IDs
     * @return Number of rows affected by deletion
     */
    int deleteBatchIds(List<Long> idList);

    ChatList getBotChat(String uid, Long botId);

    ChatBotBase insertChatBotList(ChatBotBase chatBotBase);

    ChatBotBase updateChatBotList(ChatBotBase chatBotBase);
}
