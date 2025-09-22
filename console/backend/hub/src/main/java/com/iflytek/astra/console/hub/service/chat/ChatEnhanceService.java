package com.iflytek.astra.console.hub.service.chat;

import com.iflytek.astra.console.commons.entity.chat.ChatFileUser;
import com.iflytek.astra.console.hub.dto.chat.ChatEnhanceSaveFileVo;

import java.util.List;
import java.util.Map;

public interface ChatEnhanceService {

    Map<String, Object> addHistoryChatFile(List<Object> assembledHistoryList, String uid, Long chatId);

    Map<String, String> saveFile(String uid, ChatEnhanceSaveFileVo vo);

    ChatFileUser findById(Long linkId, String uid);

    /**
     * 删除chat_file_req表单信息
     * 注：所有绑定ReqId的信息都不做删除
     */
    void delete(String fileId, Long chatId, String uid);
}
