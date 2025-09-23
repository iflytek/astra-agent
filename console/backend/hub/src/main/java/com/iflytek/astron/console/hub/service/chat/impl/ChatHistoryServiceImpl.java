package com.iflytek.astron.console.hub.service.chat.impl;

import com.alibaba.fastjson2.JSONObject;
import com.iflytek.astron.console.commons.dto.llm.SparkChatRequest;
import com.iflytek.astron.console.commons.entity.chat.*;
import com.iflytek.astron.console.commons.service.data.ChatDataService;
import com.iflytek.astron.console.commons.service.data.ChatHistoryService;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.util.Base64Util;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;

import java.util.*;
import java.util.stream.Collectors;

@Service
@Slf4j
public class ChatHistoryServiceImpl implements ChatHistoryService {

    @Autowired
    private ChatDataService chatDataService;

    public static final int MAX_HISTORY_NUMBERS = 8000;

    @Override
    public List<SparkChatRequest.MessageDto> getSystemBotHistory(String uid, Long chatId) {
        // Get question history
        List<ChatReqModelDto> chatReqModelDtos = chatDataService.getReqModelBotHistoryByChatId(uid, chatId);
        List<SparkChatRequest.MessageDto> messages = new ArrayList<>();
        if (CollectionUtils.isEmpty(chatReqModelDtos)) {
            return messages;
        }
        // Get answer history
        List<Long> reqIds = chatReqModelDtos.stream().map(ChatReqModelDto::getId).collect(Collectors.toList());
        List<ChatRespModelDto> chatRespModelDtos = chatDataService.getChatRespModelBotHistoryByChatId(uid, chatId, reqIds);
        // Group answer history by reqId
        Map<Long, ChatRespModelDto> respMap = new HashMap<>();
        if (!CollectionUtils.isEmpty(chatRespModelDtos)) {
            for (ChatRespModelDto respDto : chatRespModelDtos) {
                respMap.put(respDto.getReqId(), respDto);
            }
        }
        // Merge conversation history in chronological order of questions
        for (ChatReqModelDto reqDto : chatReqModelDtos) {
            // Add user message
            SparkChatRequest.MessageDto userMessage = new SparkChatRequest.MessageDto();
            userMessage.setRole("user");
            userMessage.setContent(reqDto.getMessage());
            messages.add(userMessage);
            // Add corresponding assistant response
            ChatRespModelDto respDto = respMap.get(reqDto.getId());
            if (respDto != null && respDto.getMessage() != null && !respDto.getMessage().trim().isEmpty()) {
                SparkChatRequest.MessageDto assistantMessage = new SparkChatRequest.MessageDto();
                assistantMessage.setRole("assistant");
                assistantMessage.setContent(respDto.getMessage());
                messages.add(assistantMessage);
            }
        }
        return messages;
    }

    @Override
    public ChatRequestDtoList getHistory(String uid, Long chatId, List<ChatReqModelDto> reqList) {
        if (reqList == null || reqList.isEmpty()) {
            return new ChatRequestDtoList();
        }
        List<Long> reqIdList = reqList.stream().filter(Objects::nonNull).map(ChatReqModelDto::getId).collect(Collectors.toList());
        List<ChatRespModelDto> respList = chatDataService.getChatRespModelBotHistoryByChatId(uid, chatId, reqIdList);
        ChatRequestDtoList chatRecordList = new ChatRequestDtoList();
        int tempLength = 0;
        if (respList == null) {
            respList = new ArrayList<>();
        }
        // Flush historical sessions to cache, will automatically scroll update to maximum range
        for (int i = 0; i < Math.min(reqList.size(), respList.size()); i++) {
            // Add answer, history records answer first then question
            String answer = respList.get(i).getMessage();
            int answerLength = answer == null ? 0 : answer.length();
            // If there is data in multimodal content, it means this is a multimodal return, append history in
            // multimodal design format
            if (StringUtils.isNotBlank(respList.get(i).getContent())) {
                // Multimodal concatenation length defaults to 200
                answerLength = 200;
                String url = respList.get(i).getUrl();
                String content = respList.get(i).getContent();
                String type = respList.get(i).getType();
                String dataId = respList.get(i).getDataId();
                int needHis = respList.get(i).getNeedHis();
                if (needHis == 0) {
                    ChatContentMeta contentMeta = new ChatContentMeta(null, content, true, dataId);
                    chatRecordList.getMessages().addFirst(new ChatRequestDto("assistant", url, type, contentMeta));
                } else if (needHis == 2) {
                    // Insert at the first position, the rest automatically shift down
                    chatRecordList.getMessages().addFirst(new ChatRequestDto("assistant", answer));
                    // This is the single round length for image description return set to 800
                    // answerLength = 800;
                }
            } else {
                // Insert at the first position, the rest automatically shift down
                chatRecordList.getMessages().addFirst(new ChatRequestDto("assistant", answer));
            }
            // Historical length concatenation
            tempLength = tempLength + answerLength;
            if (tempLength > MAX_HISTORY_NUMBERS) {
                return chatRecordList;
            }
            /*** Add question ***/
            if (i < reqList.size()) {
                String ask = reqList.get(i).getMessage();
                int askLength = ask == null ? 0 : ask.length();
                // If the question is an image, set length to 800 to prevent history from exceeding 10 images
                if (StringUtils.isNotBlank(reqList.get(i).getUrl())) {
                    askLength = 800;
                }
                tempLength = tempLength + askLength;
                if (tempLength > MAX_HISTORY_NUMBERS) {
                    return chatRecordList;
                }

                // If there is data in multimodal content, it means this is multimodal input, append history in
                // multimodal design QQA format
                if (StringUtils.isNotBlank(reqList.get(i).getUrl())) {
                    String url = reqList.get(i).getUrl();
                    List<ChatModelMeta> metaList = urlToArray(url, ask);
                    chatRecordList.getMessages().addFirst(new ChatRequestDto("user", metaList));
                } else {
                    chatRecordList.getMessages().addFirst(new ChatRequestDto("user", ask));
                }
            }
        }
        chatRecordList.setLength(tempLength);
        return chatRecordList;
    }

    /**
     * Convert url to large model multimodal protocol content array
     */
    @Override
    public List<ChatModelMeta> urlToArray(String url, String ask) {
        List<ChatModelMeta> metaList = new ArrayList<>();
        // Image address concatenation
        if (StringUtils.isNotBlank(url)) {
            String[] urls = url.split(",");
            // Assemble images
            for (String tempUrl : urls) {
                // Skip if image address is empty
                if (StringUtils.isBlank(tempUrl) || "null".equals(tempUrl)) {
                    continue;
                }
                ChatModelMeta meta = new ChatModelMeta();
                JSONObject jb = new JSONObject();
                jb.put("url", Base64Util.encode(tempUrl));
                meta.setType("image_url");
                meta.setImage_url(jb);
                metaList.add(meta);
            }
        }

        // Text must be placed at the end of the array
        if (StringUtils.isNotBlank(ask)) {
            ChatModelMeta meta = new ChatModelMeta();
            meta.setType("text");
            meta.setText(ask);
            metaList.add(meta);
        }
        return metaList;
    }
}
