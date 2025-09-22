package com.iflytek.astra.console.hub.service.chat.impl;

import cn.hutool.core.collection.CollUtil;
import com.alibaba.fastjson2.JSONObject;
import com.iflytek.astra.console.commons.entity.chat.ChatRespModelDto;
import com.iflytek.astra.console.commons.entity.chat.ChatReasonRecords;
import com.iflytek.astra.console.commons.entity.chat.ChatTraceSource;
import com.iflytek.astra.console.hub.service.chat.ChatReasonRecordsService;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

@Service
@Slf4j
public class ChatReasonRecordsServiceImpl implements ChatReasonRecordsService {


    @Override
    public void assembleRespReasoning(List<ChatRespModelDto> respList, List<ChatReasonRecords> reasonRecordsList, List<ChatTraceSource> traceList) {
        if (CollUtil.isEmpty(respList) || CollUtil.isEmpty(reasonRecordsList)) {
            return;
        }
        Map<Long, ChatReasonRecords> reqIdToReasonRecord = reasonRecordsList.stream()
                        .collect(Collectors.toMap(ChatReasonRecords::getReqId,
                                        entity -> entity,
                                        (existing, replacement) -> replacement));

        for (ChatRespModelDto chatRespModelDto : respList) {
            ChatReasonRecords reasonRecords = reqIdToReasonRecord.get(chatRespModelDto.getReqId());
            if (Objects.nonNull(reasonRecords)) {
                chatRespModelDto.setReasoning(reasonRecords.getContent());

                // Convert to {"thinking_cost":xxx, text: xxx}
                chatRespModelDto.setReasoningElapsedSecs(reasonRecords.getThinkingElapsedSecs());
                if (StringUtils.isNotEmpty(reasonRecords.getContent())) {
                    JSONObject jsonObj = new JSONObject();
                    jsonObj.put("text", reasonRecords.getContent());
                    jsonObj.put("thinking_cost", reasonRecords.getThinkingElapsedSecs());
                    chatRespModelDto.setContent(jsonObj.toString());
                }
            }
        }
    }
}
