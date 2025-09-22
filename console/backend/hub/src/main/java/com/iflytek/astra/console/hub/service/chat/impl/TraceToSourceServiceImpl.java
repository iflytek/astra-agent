package com.iflytek.astra.console.hub.service.chat.impl;

import com.iflytek.astra.console.commons.entity.chat.ChatRespModelDto;
import com.iflytek.astra.console.commons.entity.chat.ChatTraceSource;
import com.iflytek.astra.console.hub.service.chat.TraceToSourceService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@Slf4j
public class TraceToSourceServiceImpl implements TraceToSourceService {

    @Override
    public void respAddTrace(List<ChatRespModelDto> respList, List<ChatTraceSource> traceList) {
        // Iterate through responses, supplement traceability data based on reqId
        for (ChatRespModelDto dto : respList) {
            for (ChatTraceSource chatTraceSource : traceList) {
                if (chatTraceSource == null) {
                    continue;
                }
                dto.setTraceSource(chatTraceSource.getContent());
                dto.setSourceType(chatTraceSource.getType());
            }
        }
    }
}
