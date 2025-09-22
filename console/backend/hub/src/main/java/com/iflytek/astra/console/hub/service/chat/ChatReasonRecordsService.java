package com.iflytek.astra.console.hub.service.chat;

import com.iflytek.astra.console.commons.entity.chat.ChatRespModelDto;
import com.iflytek.astra.console.commons.entity.chat.ChatReasonRecords;
import com.iflytek.astra.console.commons.entity.chat.ChatTraceSource;

import java.util.List;

public interface ChatReasonRecordsService {

    void assembleRespReasoning(List<ChatRespModelDto> respList, List<ChatReasonRecords> reasonRecordsList, List<ChatTraceSource> traceList);
}
