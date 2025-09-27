package com.iflytek.astron.console.commons.service.data;

import com.iflytek.astron.console.commons.entity.bot.BotChatFileParam;
import com.iflytek.astron.console.commons.entity.chat.*;

import java.time.LocalDateTime;
import java.util.List;

public interface ChatDataService {


    /** Query request records by chat ID and user ID */
    List<ChatReqRecords> findRequestsByChatIdAndUid(Long chatId, String uid);

    /** Query request records by chat ID and time range */
    List<ChatReqRecords> findRequestsByChatIdAndTimeRange(Long chatId, LocalDateTime startTime, LocalDateTime endTime);

    /** Create request record */
    ChatReqRecords createRequest(ChatReqRecords chatReqRecords);

    /** Query response records by request ID */
    List<ChatRespRecords> findResponsesByReqId(Long reqId);

    /** Query response records by chat ID */
    List<ChatRespRecords> findResponsesByChatId(Long chatId);

    /** Create response record */
    ChatRespRecords createResponse(ChatRespRecords chatRespRecords);

    /** Count chat numbers by user ID */
    long countChatsByUid(String uid);

    /** Count message numbers by chat ID */
    long countMessagesByChatId(Long chatId);

    /** Query recent chat records */
    List<ChatList> findRecentChatsByUid(String uid, int limit);

    /**
     * Get multimodal assistant request history by chat ID
     *
     * @param uid
     * @param chatId
     * @return
     */
    List<ChatReqModelDto> getReqModelBotHistoryByChatId(String uid, Long chatId);

    /**
     * Get Q history with multimodal information by chat ID
     *
     * @param uid
     * @param chatId
     * @return
     */
    List<ChatRespModelDto> getChatRespModelBotHistoryByChatId(String uid, Long chatId, List<Long> reqIds);


    /**
     * Create reasoning process
     */
    ChatReasonRecords createReasonRecord(ChatReasonRecords chatReasonRecords);

    /**
     * Create trace source record
     */
    ChatTraceSource createTraceSource(ChatTraceSource chatTraceSource);

    /**
     * Query request record by request ID
     */
    ChatReqRecords findRequestById(Long reqId);

    /**
     * Update response record by uid, chatId, reqId
     */
    Integer updateByUidAndChatIdAndReqId(ChatRespRecords chatRespRecords);

    /**
     * Query corresponding ChatRespRecords by uid, chatId, reqId
     */
    ChatRespRecords findResponseByUidAndChatIdAndReqId(String uid, Long chatId, Long reqId);

    /**
     * Query corresponding ChatReasonRecords by uid, chatId, reqId
     */
    ChatReasonRecords findReasonByUidAndChatIdAndReqId(String uid, Long chatId, Long reqId);

    /**
     * Update reasoning record by uid, chatId, reqId
     */
    Integer updateReasonByUidAndChatIdAndReqId(ChatReasonRecords chatReasonRecords);

    /**
     * Query corresponding ChatTraceSource by uid, chatId, reqId
     */
    ChatTraceSource findTraceSourceByUidAndChatIdAndReqId(String uid, Long chatId, Long reqId);

    /**
     * Update trace source record by uid, chatId, reqId
     */
    Integer updateTraceSourceByUidAndChatIdAndReqId(ChatTraceSource chatTraceSource);

    /**
     * Update questions before new conversation
     */
    Integer updateNewContextByUidAndChatId(String uid, Long chatId);

    List<ChatTraceSource> findTraceSourcesByChatId(Long chatId);

    List<ChatReasonRecords> getReasonRecordsByChatId(Long chatId);

    List<ChatFileReq> getFileList(String uid, Long chatId);

    ChatFileUser getByFileIdAll(String fileId, String uid);

    ChatFileUser getByFileId(String fileId, String uid);

    List<ChatReqModelDto> getReqModelWithImgByChatId(String uid, Long chatId);

    ChatReqModel createChatReqModel(ChatReqModel chatReqModel);

    /**
     * Query bot chat file parameters by chat ID and delete status
     */
    List<BotChatFileParam> findBotChatFileParamsByChatIdAndIsDelete(Long chatId, Integer isDelete);

    void updateFileReqId(Long chatId, String uid, List<String> fileIds, Long reqId, boolean edit, Long leftId);

    ChatFileUser createChatFileUser(ChatFileUser chatFileUser);

    Integer getFileUserCount(String uid);

    ChatFileUser setFileId(Long chatFileUserId, String fileId);

    ChatFileReq createChatFileReq(ChatFileReq chatFileReq);

    void setProcessed(Long chatFileUserId);

    List<BotChatFileParam> findAllBotChatFileParamByChatIdAndNameAndIsDelete(Long chatId, String name, Integer isDelete);

    BotChatFileParam createBotChatFileParam(BotChatFileParam botChatFileParam);

    BotChatFileParam updateBotChatFileParam(BotChatFileParam botChatFileParam);

    /**
     * Find ChatFileUser by link ID and user ID within valid time range
     */
    ChatFileUser findChatFileUserByIdAndUid(Long linkId, String uid);

    /**
     * Delete ChatFileReq by marking it as deleted Only deletes records that are not bound to any reqId
     */
    void deleteChatFileReq(String fileId, Long chatId, String uid);
}
