package com.iflytek.astron.console.hub.service.chat.impl;

import com.alibaba.fastjson2.JSON;
import com.iflytek.astron.console.commons.entity.chat.ChatReqModelDto;
import com.iflytek.astron.console.commons.entity.chat.ChatRespModelDto;
import com.iflytek.astron.console.commons.entity.workflow.WorkflowEventData;
import com.iflytek.astron.console.hub.service.chat.ChatHistoryMultiModalService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@Slf4j
public class ChatHistoryMultiModalServiceImpl implements ChatHistoryMultiModalService {


    /**
     * Merge document history records
     *
     * @param reqList
     * @param respList
     * @param botId
     * @return
     */
    @Override
    public List<Object> mergeChatHistory(List<ChatReqModelDto> reqList, List<ChatRespModelDto> respList, Integer botId) {
        // respMap with reqId as key, convert list to map
        Map<Long, ChatRespModelDto> respMap = new HashMap<>();
        for (ChatRespModelDto resp : respList) {
            respMap.put(resp.getReqId(), resp);
        }
        setBotLastContext(reqList, botId);
        List<Object> list = new ArrayList<>();
        int reqSize = reqList.size();
        for (int i = reqSize - 1; i >= 0; i--) {
            ChatReqModelDto chatReqRecords = reqList.get(i);
            list.add(chatReqRecords);
            if (respMap.get(reqList.get(i).getId()) != null) {
                ChatRespModelDto chatRespModelDto = respMap.get(reqList.get(i).getId());
                if (chatReqRecords.isNeedDraw()) {
                    // If req that needs drawing exists resp, move down to resp
                    chatRespModelDto.setNeedDraw(true);
                    chatReqRecords.setNeedDraw(false);
                }
                processWorkflowInterruptHistory(chatRespModelDto, reqList, i);
                // Bring req's intention to resp
                chatRespModelDto.setIntention(chatReqRecords.getIntention());
                list.add(chatRespModelDto);
            }
        }
        return list;
    }

    public void setBotLastContext(List<ChatReqModelDto> records, Integer botId) {
        if (botId == null || 0 == botId) {
            return;
        }
        // Iterate, set the most recent old Req in conversation to true (needs drawing)
        for (ChatReqModelDto record : records) {
            if (record.getNewContext() == 0) {
                record.setNeedDraw(true);
                break;
            }
        }
    }

    /**
     * Process workflow interruption history records
     *
     * @param chatRespModelDto Current response history record
     * @param reqList User question list
     * @param currentIndex Index of Q&A record corresponding to current response history record
     *
     */
    private static void processWorkflowInterruptHistory(ChatRespModelDto chatRespModelDto, List<ChatReqModelDto> reqList, int currentIndex) {
        // 41 - Workflow interruption specified answerType
        if (chatRespModelDto.getAnswerType() != 41) {
            return;
        }
        WorkflowEventData.EventValue respEventMsg = JSON.parseObject(chatRespModelDto.getMessage(), WorkflowEventData.EventValue.class);
        if (respEventMsg == null) {
            return;
        }
        // Adjust LLM response record content
        // 1. Fill body data into standard history record message
        // 2. Transmit event-related response content through separate fields
        chatRespModelDto.setMessage(respEventMsg.getMessage());
        chatRespModelDto.setWorkflowEventData(respEventMsg);
        // Prevent index out of bounds
        if (currentIndex == 0) {
            return;
        }
        // Get the next question from current user question
        // Because in workflow it's LLM asking and user answering, so next user's Req has business logic
        // association with previous LLM's Resp
        ChatReqModelDto nextChatReqRecord = reqList.get(currentIndex - 1);
        try {
            WorkflowEventData.EventValue.ValueOption valueOption = JSON.parseObject(nextChatReqRecord.getMessage(), WorkflowEventData.EventValue.ValueOption.class);
            if (valueOption == null) {
                return;
            }
            // For Q&A node selection answers, fill selected items
            nextChatReqRecord.setMessage(valueOption.getId());
            for (WorkflowEventData.EventValue.ValueOption option : respEventMsg.getOption()) {
                if (option.getId().equals(valueOption.getId())) {
                    // Only supports single selection
                    option.setSelected(true);
                }
            }
        } catch (Exception e) {
            log.debug("JSON parsing exception, do not change request history message data : {}", nextChatReqRecord.getMessage());
        }
    }
}
