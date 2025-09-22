package com.iflytek.astra.console.hub.service.chat;

import com.iflytek.astra.console.commons.entity.chat.ChatRespModelDto;
import com.iflytek.astra.console.commons.entity.chat.ChatTraceSource;

import java.util.List;

public interface TraceToSourceService {

    void respAddTrace(List<ChatRespModelDto> respList, List<ChatTraceSource> traceList);
}
