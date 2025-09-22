package com.iflytek.astra.console.hub.service.chat;

import com.iflytek.astra.console.commons.entity.chat.ChatReqModelDto;
import com.iflytek.astra.console.commons.entity.chat.ChatRespModelDto;

import java.util.List;

public interface ChatHistoryMultiModalService {

    /**
     * Merge document history records
     *
     * @param reqList
     * @param respList
     * @param botId
     * @return
     */
    List<Object> mergeChatHistory(List<ChatReqModelDto> reqList, List<ChatRespModelDto> respList, Integer botId);
}
