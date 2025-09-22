package com.iflytek.astra.console.commons.service.bot;

import com.iflytek.astra.console.commons.entity.bot.*;
import jakarta.servlet.http.HttpServletRequest;

import java.util.List;
import java.util.Map;
import java.util.Optional;

public interface ChatBotDataService {

    /**
     * Query assistant by ID
     */
    Optional<ChatBotBase> findById(Integer botId);

    /**
     * Query assistant by ID and space ID
     */
    Optional<ChatBotBase> findByIdAndSpaceId(Integer botId, Long spaceId);

    /**
     * Query assistant list by user ID
     */
    List<ChatBotBase> findByUid(String uid);

    /**
     * Query assistant list by user ID and space ID
     */
    List<ChatBotBase> findByUidAndSpaceId(String uid, Long spaceId);

    /**
     * Query assistant list by space ID
     */
    List<ChatBotBase> findBySpaceId(Long spaceId);

    /**
     * Query by assistant type
     */
    List<ChatBotBase> findByBotType(Integer botType);

    /**
     * Query by assistant type and space ID
     */
    List<ChatBotBase> findByBotTypeAndSpaceId(Integer botType, Long spaceId);

    /**
     * Query non-deleted assistants
     */
    List<ChatBotBase> findActiveBotsBy(String uid);

    /**
     * Query non-deleted assistants (by space)
     */
    List<ChatBotBase> findActiveBotsBy(String uid, Long spaceId);

    /**
     * Create assistant
     */
    ChatBotBase createBot(ChatBotBase chatBotBase);

    /**
     * Update assistant information
     */
    ChatBotBase updateBot(ChatBotBase chatBotBase);

    /**
     * Soft delete assistant
     */
    boolean deleteBot(Integer botId);

    /**
     * Soft delete assistant (by user)
     */
    boolean deleteBot(Integer botId, String uid);

    /**
     * Soft delete assistant (by space)
     */
    boolean deleteBot(Integer botId, Long spaceId);

    /**
     * Batch delete assistants
     */
    boolean deleteBotsByIds(List<Integer> botIds);

    /**
     * Batch delete assistants (by space)
     */
    boolean deleteBotsByIds(List<Integer> botIds, Long spaceId);

    /**
     * Count user's assistants
     */
    long countBotsByUid(String uid);

    /**
     * Count user's assistants (by space)
     */
    long countBotsByUid(String uid, Long spaceId);

    /**
     * Query user's assistant list
     */
    List<ChatBotList> findUserBotList(String uid);

    /**
     * Add assistant to user list
     */
    ChatBotList addBotToUserList(ChatBotList chatBotList);

    /**
     * Remove assistant from user list
     */
    boolean removeBotFromUserList(String uid, Integer marketBotId);

    /**
     * Query assistant market list
     */
    List<ChatBotMarket> findMarketBots(Integer botStatus, int page, int size);

    /**
     * Query market assistants by popularity
     */
    List<ChatBotMarket> findMarketBotsByHot(int limit);

    /**
     * Search market assistants
     */
    List<ChatBotMarket> searchMarketBots(String keyword, Integer botType);

    /**
     * Query whether assistant is deleted
     */
    boolean botIsDeleted(Long botId);

    /**
     * Query corresponding ChatBotMarket information by botId
     *
     * @param botId Assistant ID
     * @return Assistant market information, returns null if not exists
     */
    ChatBotMarket findMarketBotByBotId(Integer botId);

    /**
     * 检查用户在指定空间内是否有重复的助手名称
     *
     * @param uid     用户ID
     * @param botId   助手ID（编辑时传入，创建时传null）
     * @param botName 助手名称
     * @param spaceId 空间ID
     * @return 如果存在重复名称返回true，否则返回false
     */
    Boolean checkRepeatBotName(String uid, Integer botId, String botName, Long spaceId);

    /**
     * Delete assistants under the space when deleting space
     */
    void deleteBotForDeleteSpace(String uid, Long spaceId, HttpServletRequest request);

    ChatBotList findByUidAndBotId(String uid, Integer botId);

    ChatBotList createUserBotList(ChatBotList chatBotList);

    ChatBotBase copyBot(String uid, Integer botId, Long spaceId);

    Boolean takeoffBot(String uid, Long spaceId, TakeoffList takeoffList);

    /**
     * Update bot basic information (description, prologue, input examples)
     */
    boolean updateBotBasicInfo(Integer botId, String botDesc, String prologue, String inputExamples);

    BotDetail getBotDetail(Long botId);

    PromptBotDetail getPromptBotDetail(Integer botId, String uid);

    Map<String, Object> getVcnDetail(String vcnCode);

    List<Integer> getReleaseChannel(String uid, Integer botId);
}
