package com.iflytek.astra.console.hub.service.chat.impl;

import com.iflytek.astra.console.commons.service.data.ChatDataService;
import com.iflytek.astra.console.commons.entity.chat.ChatReasonRecords;
import com.iflytek.astra.console.commons.entity.chat.ChatReqRecords;
import com.iflytek.astra.console.commons.entity.chat.ChatRespRecords;
import com.iflytek.astra.console.commons.service.ChatRecordModelService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Slf4j
@Service
public class ChatRecordModelServiceImpl implements ChatRecordModelService {

    @Autowired
    private ChatDataService chatDataService;

    /**
     * Save thinking process result
     *
     * @param chatReqRecords Chat request record
     * @param thinkingResult Result of thinking process
     * @param edit Whether it's in edit mode
     */
    @Override
    public void saveThinkingResult(ChatReqRecords chatReqRecords, StringBuffer thinkingResult, boolean edit) {
        if (thinkingResult.isEmpty()) {
            return;
        }

        java.time.LocalDateTime now = java.time.LocalDateTime.now();

        if (edit) {
            // Edit mode: query existing record and update
            ChatReasonRecords existingRecord = chatDataService.findReasonByUidAndChatIdAndReqId(
                            chatReqRecords.getUid(),
                            chatReqRecords.getChatId(),
                            chatReqRecords.getId());

            if (existingRecord != null) {
                existingRecord.setContent(thinkingResult.toString());
                existingRecord.setUpdateTime(now);

                chatDataService.updateReasonByUidAndChatIdAndReqId(existingRecord);
                log.info("Updated thinking process record, reqId: {}, chatId: {}, uid: {}",
                                chatReqRecords.getId(), chatReqRecords.getChatId(), chatReqRecords.getUid());
            }
        } else {
            // Create mode: create new record
            createNewThinkingResult(chatReqRecords, thinkingResult, now);
        }
    }

    /**
     * Create new thinking process record
     */
    private void createNewThinkingResult(ChatReqRecords chatReqRecords, StringBuffer thinkingResult, java.time.LocalDateTime now) {
        ChatReasonRecords chatReasonRecords = new ChatReasonRecords();
        chatReasonRecords.setUid(chatReqRecords.getUid());
        chatReasonRecords.setChatId(chatReqRecords.getChatId());
        chatReasonRecords.setReqId(chatReqRecords.getId());
        chatReasonRecords.setContent(thinkingResult.toString());
        chatReasonRecords.setType("spark_reasoning");
        chatReasonRecords.setThinkingElapsedSecs(0L);
        chatReasonRecords.setCreateTime(now);
        chatReasonRecords.setUpdateTime(now);

        chatDataService.createReasonRecord(chatReasonRecords);
        log.info("Created new thinking process record, reqId: {}, chatId: {}, uid: {}",
                        chatReqRecords.getId(), chatReqRecords.getChatId(), chatReqRecords.getUid());
    }

    /**
     * Save chat response information
     *
     * @param chatReqRecords Chat request record
     * @param finalResult Final result string builder
     * @param sid Session ID string builder
     */
    @Override
    public void saveChatResponse(ChatReqRecords chatReqRecords, StringBuffer finalResult, StringBuffer sid, boolean edit, Integer answerType) {
        java.time.LocalDateTime now = java.time.LocalDateTime.now();
        int dateStamp = Integer.parseInt(java.time.LocalDate.now().format(java.time.format.DateTimeFormatter.ofPattern("yyyyMMdd")));

        if (edit) {
            // Edit mode: query existing record and update
            ChatRespRecords existingRecord = chatDataService.findResponseByUidAndChatIdAndReqId(
                            chatReqRecords.getUid(),
                            chatReqRecords.getChatId(),
                            chatReqRecords.getId());

            if (existingRecord != null) {
                existingRecord.setMessage(finalResult.toString());
                existingRecord.setSid(sid.toString());
                existingRecord.setUpdateTime(now);
                existingRecord.setDateStamp(dateStamp);
                existingRecord.setAnswerType(answerType);

                chatDataService.updateByUidAndChatIdAndReqId(existingRecord);
                log.info("Updated chat response record, reqId: {}, chatId: {}, uid: {}",
                                chatReqRecords.getId(), chatReqRecords.getChatId(), chatReqRecords.getUid());
            }
        } else {
            // Create mode: create new record
            createNewChatResponse(chatReqRecords, finalResult, sid, now, dateStamp, answerType);
        }
    }

    /**
     * Create new chat response record
     */
    private void createNewChatResponse(ChatReqRecords chatReqRecords, StringBuffer finalResult, StringBuffer sid,
                    java.time.LocalDateTime now, int dateStamp, Integer answerType) {
        ChatRespRecords chatRespRecords = new ChatRespRecords();
        chatRespRecords.setUid(chatReqRecords.getUid());
        chatRespRecords.setChatId(chatReqRecords.getChatId());
        chatRespRecords.setReqId(chatReqRecords.getId());
        chatRespRecords.setMessage(finalResult.toString());
        chatRespRecords.setAnswerType(answerType);
        chatRespRecords.setSid(sid.toString());
        chatRespRecords.setCreateTime(now);
        chatRespRecords.setUpdateTime(now);
        chatRespRecords.setDateStamp(dateStamp);

        chatDataService.createResponse(chatRespRecords);
        log.info("Created new chat response record, reqId: {}, chatId: {}, uid: {}",
                        chatReqRecords.getId(), chatReqRecords.getChatId(), chatReqRecords.getUid());
    }

}
