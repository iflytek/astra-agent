package com.iflytek.astron.console.hub.service.chat;

import com.iflytek.astron.console.commons.entity.chat.ChatFileUser;
import com.iflytek.astron.console.hub.dto.chat.ChatEnhanceSaveFileVo;

import java.util.List;
import java.util.Map;

public interface ChatEnhanceService {

    Map<String, Object> addHistoryChatFile(List<Object> assembledHistoryList, String uid, Long chatId);

    Map<String, String> saveFile(String uid, ChatEnhanceSaveFileVo vo);

    ChatFileUser findById(Long linkId, String uid);

    /**
     * Delete chat_file_req table information Note: All information bound to ReqId will not be deleted
     */
    void delete(String fileId, Long chatId, String uid);
}
