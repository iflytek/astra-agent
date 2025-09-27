package com.iflytek.astron.console.hub.service.chat;

import com.iflytek.astron.console.commons.entity.chat.ChatRespModelDto;
import com.iflytek.astron.console.commons.entity.chat.ChatReasonRecords;
import com.iflytek.astron.console.commons.entity.chat.ChatTraceSource;

import java.util.List;

public interface ChatReasonRecordsService {

    void assembleRespReasoning(List<ChatRespModelDto> respList, List<ChatReasonRecords> reasonRecordsList, List<ChatTraceSource> traceList);
}
