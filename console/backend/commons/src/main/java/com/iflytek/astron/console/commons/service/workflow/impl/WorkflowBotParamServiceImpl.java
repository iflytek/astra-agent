package com.iflytek.astron.console.commons.service.workflow.impl;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.ObjectUtil;
import com.alibaba.fastjson2.JSONObject;
import com.iflytek.astron.console.commons.entity.bot.BotChatFileParam;
import com.iflytek.astron.console.commons.entity.chat.ChatFileReq;
import com.iflytek.astron.console.commons.entity.chat.ChatFileUser;
import com.iflytek.astron.console.commons.entity.chat.ChatReqModel;
import com.iflytek.astron.console.commons.entity.chat.ChatReqModelDto;
import com.iflytek.astron.console.commons.service.data.ChatDataService;
import com.iflytek.astron.console.commons.service.workflow.WorkflowBotParamService;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

@Service
@Slf4j
public class WorkflowBotParamServiceImpl implements WorkflowBotParamService {

    @Autowired
    private ChatDataService chatDataService;

    @Override
    public void handleSingleParam(String uid, Long chatId, String sseId, Long leftId, String fileUrl,
            JSONObject extraInputs, Long reqId, JSONObject inputs, Integer botId) {
        // Set multimodal input parameters
        if (Objects.nonNull(extraInputs) && !extraInputs.isEmpty()) {
            String key = extraInputs.keySet().stream().findFirst().orElse(null);
            if (StringUtils.isNotBlank(fileUrl)) {
                fileUrl = fileUrl.replace(",", "");
                // Here we need to create some parameters to bind with the chat window
                ChatReqModel chatReqModel = new ChatReqModel();
                chatReqModel.setChatReqId(reqId);
                chatReqModel.setChatId(chatId);
                chatReqModel.setUid(uid);
                chatReqModel.setDataId(sseId);
                chatReqModel.setIntention(null);
                // Set type to image
                chatReqModel.setType(1);
                chatReqModel.setUrl(fileUrl);
                chatReqModel.setNeedHis(1);
                chatReqModel.setOcrResult(null);
                chatReqModel.setCreateTime(LocalDateTime.now());
                chatReqModel.setUpdateTime(LocalDateTime.now());
                chatDataService.createChatReqModel(chatReqModel);
                inputs.put(key, fileUrl);
            } else {
                // Query file table
                List<ChatFileReq> chatFileReqList = chatDataService.getFileList(uid, chatId);
                chatFileReqList = chatFileReqList.stream()
                        .sorted(Comparator.comparingLong(ChatFileReq::getId))
                        .toList();
                // Query multimodal table
                List<ChatReqModelDto> reqModelDtoList = chatDataService.getReqModelWithImgByChatId(uid, chatId);

                // Trade-off between multimodal and conversation files
                ChatReqModelDto reqModelDto = !reqModelDtoList.isEmpty() ? reqModelDtoList.getFirst() : null;
                ChatFileReq fileReq = !chatFileReqList.isEmpty() ? chatFileReqList.getLast() : null;
                // Make logical judgments based on existence
                if (reqModelDto != null && fileReq != null) {
                    // If both exist, compare timestamps
                    if (reqModelDto.getCreateTime().isAfter(fileReq.getCreateTime())) {
                        inputs.put(key, reqModelDto.getUrl());
                    } else {
                        handleFileReqInput(fileReq, uid, chatId, reqId, leftId, inputs, key);
                    }
                } else if (reqModelDto != null) {
                    inputs.put(key, reqModelDto.getUrl());
                } else if (fileReq != null) {
                    handleFileReqInput(fileReq, uid, chatId, reqId, leftId, inputs, key);
                }
            }
        }

        // return resultJson;
    }


    @Override
    public boolean handleMultiFileParam(String uid, Long chatId, Long leftId, List<JSONObject> extraInputsConfig, JSONObject inputs, Long reqId) {
        List<BotChatFileParam> botChatFileParamList = chatDataService.findBotChatFileParamsByChatIdAndIsDelete(chatId, 0);

        boolean hasSet = false;
        // Query file table
        List<ChatFileReq> chatFileReqList = chatDataService.getFileList(uid, chatId);
        chatFileReqList = chatFileReqList.stream()
                .sorted(Comparator.comparingLong(ChatFileReq::getId))
                .collect(Collectors.toList());
        if (CollUtil.isNotEmpty(extraInputsConfig) && CollUtil.isNotEmpty(botChatFileParamList)) {
            botChatFileParamList = botChatFileParamList.stream().filter(a -> ObjectUtil.isNotEmpty(a.getFileUrls())).collect(Collectors.toList());
            for (JSONObject inputObject : extraInputsConfig) {
                String name = inputObject.getString("name");
                List<String> fileUrls = botChatFileParamList.stream()
                        .filter(param -> name.equals(param.getName()))
                        .flatMap(param -> param.getFileUrls().stream())
                        .collect(Collectors.toList());
                if (CollUtil.isEmpty(fileUrls)) {
                    continue;
                }

                Object param = isFileArray(inputObject) ? fileUrls : fileUrls.getLast();
                inputs.put(name, param);
                hasSet = true;
            }
        }

        // Bind all unbound files with reqId
        handleMultiFileReqInput(chatFileReqList, uid, chatId, reqId, leftId);

        return hasSet;
    }

    // Bind all unbound files with reqId
    private void handleMultiFileReqInput(List<ChatFileReq> chatFileReqList, String uid, Long chatId, Long reqId, Long leftId) {
        if (chatFileReqList != null) {
            List<String> collect = chatFileReqList.stream()
                    .filter(fileReq -> ObjectUtil.isEmpty(fileReq.getReqId()))
                    .map(ChatFileReq::getFileId)
                    .collect(Collectors.toList());
            // Bind request ID
            chatDataService.updateFileReqId(chatId, uid, collect, reqId, false, leftId);
        }
    }

    // Common method for handling file request input
    private void handleFileReqInput(ChatFileReq fileReq, String uid, Long chatId, Long reqId, Long leftId, JSONObject inputs, String key) {

        String fileId = fileReq.getFileId();
        ChatFileUser fileUser = chatDataService.getByFileId(fileId, uid);
        if (fileUser != null) {
            if (inputs != null && key != null) {
                inputs.put(key, fileUser.getFileUrl());
            }
            // Bind request ID
            if (fileReq.getReqId() == null) {
                chatDataService.updateFileReqId(chatId, uid, Collections.singletonList(fileId), reqId, false, leftId);
            }
        }
    }

    /**
     * Determine if parameter is array type
     *
     * @param param
     * @return
     */
    public static boolean isFileArray(JSONObject param) {
        try {
            if ("array-string".equalsIgnoreCase(param.getJSONObject("schema").getString("type"))) {
                return true;
            } else {
                return false;
            }
        } catch (Exception e) {
            log.error("Exception when determining if parameter is array type: {}", e.getMessage());
            return false;
        }
    }

}
