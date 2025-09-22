package com.iflytek.astra.console.hub.service.chat.impl;

import cn.hutool.core.io.unit.DataSizeUtil;
import cn.hutool.core.lang.Validator;
import cn.hutool.core.util.ObjectUtil;
import cn.hutool.core.util.StrUtil;
import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import com.iflytek.astra.console.commons.constant.ResponseEnum;
import com.iflytek.astra.console.commons.exception.BusinessException;
import com.iflytek.astra.console.commons.service.data.ChatDataService;
import com.iflytek.astra.console.hub.dto.chat.ChatEnhanceChatHistoryListFileVo;
import com.iflytek.astra.console.hub.dto.chat.ChatEnhanceSaveFileVo;
import com.iflytek.astra.console.commons.entity.chat.ChatReqModelDto;
import com.iflytek.astra.console.commons.entity.bot.BotChatFileParam;
import com.iflytek.astra.console.commons.entity.chat.ChatFileReq;
import com.iflytek.astra.console.commons.entity.chat.ChatFileUser;
import com.iflytek.astra.console.hub.enums.ChatFileLimitEnum;
import com.iflytek.astra.console.hub.enums.LongContextStatusEnum;
import com.iflytek.astra.console.hub.service.chat.ChatEnhanceService;
import com.iflytek.astra.console.hub.util.CommonUtil;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.redisson.api.RedissonClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

@Service
@Slf4j
public class ChatEnhanceServiceImpl implements ChatEnhanceService {

    @Autowired
    private ChatDataService chatDataService;

    @Autowired
    private RedissonClient redissonClient;

    @Override
    public Map<String, Object> addHistoryChatFile(List<Object> assembledHistoryList, String uid, Long chatId) {
        // Get all bound file information under this ChatId
        List<ChatFileReq> chatFileReqList = chatDataService.getFileList(uid, chatId);
        List<ChatEnhanceChatHistoryListFileVo> chatEnhanceChatHistoryListFileVos = new ArrayList<>();
        Map<Long, List<ChatEnhanceChatHistoryListFileVo>> multiValuedMap = new HashMap<>();
        // Iterate through the file information bound to chatId
        for (ChatFileReq chatFileReq : chatFileReqList) {
            Long reqId = chatFileReq.getReqId();
            ChatEnhanceChatHistoryListFileVo chatEnhanceChatHistoryListFileVo = new ChatEnhanceChatHistoryListFileVo();
            ChatFileUser chatFileUser = chatDataService.getByFileIdAll(chatFileReq.getFileId(), chatFileReq.getUid());
            if (ObjectUtil.isEmpty(chatFileUser)) {
                log.info("{} user chat: {} file {} has become invalid", uid, chatId, chatFileReq.getFileId());
                continue;
            }
            chatEnhanceChatHistoryListFileVo.setFileUrl(chatFileUser.getFileUrl());
            chatEnhanceChatHistoryListFileVo.setFileName(chatFileUser.getFileName());
            chatEnhanceChatHistoryListFileVo.setFilePdfUrl(Validator.isUrl(chatFileUser.getFilePdfUrl()) ? chatFileUser.getFilePdfUrl() : null);
            chatEnhanceChatHistoryListFileVo.setFileSize(DataSizeUtil.format(chatFileUser.getFileSize()));
            chatEnhanceChatHistoryListFileVo.setFileStatus(chatFileUser.getFileStatus());
            chatEnhanceChatHistoryListFileVo.setBusinessType(chatFileUser.getBusinessType());
            chatEnhanceChatHistoryListFileVo.setChatId(chatFileReq.getChatId());
            chatEnhanceChatHistoryListFileVo.setFileId(chatFileReq.getFileId());
            chatEnhanceChatHistoryListFileVo.setUid(chatFileReq.getUid());
            DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
            chatEnhanceChatHistoryListFileVo.setCreateTime(chatFileReq.getCreateTime().format(formatter));
            chatEnhanceChatHistoryListFileVo.setFileStatus(chatFileUser.getFileStatus());
            chatEnhanceChatHistoryListFileVo.setIcon(chatFileUser.getIcon());
            chatEnhanceChatHistoryListFileVo.setCollectOriginFrom(chatFileUser.getCollectOriginFrom());

            if (ObjectUtil.isEmpty(reqId)) {
                // Those not bound to reqId are returned to chat level
                chatEnhanceChatHistoryListFileVos.add(chatEnhanceChatHistoryListFileVo);
            } else {
                // Those bound to reqId are first put into the map
                chatEnhanceChatHistoryListFileVo.setReqId(reqId);
                if (ObjectUtil.isEmpty(multiValuedMap.get(reqId))) {
                    multiValuedMap.put(reqId, new ArrayList<>());
                }
                multiValuedMap.get(reqId).add(chatEnhanceChatHistoryListFileVo);
            }
        }

        // Iterate through historyList and insert files bound to reqId
        JSONArray historyJson = new JSONArray();
        for (Object tempObj : assembledHistoryList) {
            JSONObject tempJson;
            try {
                if (tempObj instanceof JSONObject) {
                    tempJson = (JSONObject) tempObj;
                } else if (tempObj instanceof String) {
                    tempJson = JSONObject.parseObject((String) tempObj);
                } else {
                    // For other types, first convert to JSON string then parse
                    String jsonStr = JSON.toJSONString(tempObj);
                    tempJson = JSONObject.parseObject(jsonStr);
                }
                Long reqId = tempJson.getLong("id");
                tempJson.put("chatFileList", multiValuedMap.get(reqId));
                historyJson.add(tempJson);
            } catch (Exception e) {
                log.error("Failed to parse object to JSONObject: {}, error: {}", tempObj, e.getMessage());
                // If parsing fails, create a JSONObject containing error information
                JSONObject errorJson = new JSONObject();
                errorJson.put("error", "Failed to parse object");
                errorJson.put("originalObject", tempObj != null ? tempObj.toString() : "null");
                historyJson.add(errorJson);
            }
        }

        // Assemble return
        Map<String, Object> map = new HashMap<>();
        map.put("chatFileListNoReq", chatEnhanceChatHistoryListFileVos);
        map.put("historyList", historyJson);
        if (!chatFileReqList.isEmpty()) {
            map.put("businessType", chatFileReqList.getFirst().getBusinessType());
        } else {
            map.put("businessType", null);
        }
        map.put("existChatFileSize", chatFileReqList.size());
        // Whether there are multimodal images
        List<ChatReqModelDto> reqModelDtoList = chatDataService.getReqModelWithImgByChatId(uid, chatId);
        map.put("existChatImage", !reqModelDtoList.isEmpty());
        return map;
    }

    @Override
    public Map<String, String> saveFile(String uid, ChatEnhanceSaveFileVo vo) {
        String fileName = vo.getFileName();
        String fileUrl = vo.getFileUrl();
        Long fileSize = vo.getFileSize();
        Integer businessType = vo.getBusinessType();
        // File extension and file type validation
        if (!ChatFileLimitEnum.checkFileByBusinessType(fileName, businessType)) {
            throw new BusinessException(ResponseEnum.LONG_CONTENT_WRONG_BUSINESS_TYPE);
        }
        // Convert to file type, determine whether to use the institute's interface based on the presence of
        // fileBizType field
        ChatFileLimitEnum limitEnum = ChatFileLimitEnum.getByValue(businessType);
        checkFile(uid, fileName, fileUrl, fileSize, limitEnum);

        Map<String, String> fileIdMap = documentHandler(null, uid, vo.getChatId(), fileUrl, fileName, fileSize,
                        limitEnum, vo.getFileBusinessKey(), vo.getDocumentType(), vo.getParamName());

        if (fileIdMap == null) {
            fileIdMap = new HashMap<>();
            fileIdMap.put("file_id", null);
            fileIdMap.put("error_msg", LongContextStatusEnum.FINALLY.getErrorMsg());
        }
        String fileId = fileIdMap.get("file_id");
        if (StringUtils.isBlank(fileId)) {
            fileIdMap.put("error_msg", LongContextStatusEnum.FINALLY.getErrorMsg());
        }
        return fileIdMap;
    }

    @Override
    public ChatFileUser findById(Long linkId, String uid) {
        return chatDataService.findChatFileUserByIdAndUid(linkId, uid);
    }

    /**
     * 删除chat_file_req表单信息
     * 注：所有绑定ReqId的信息都不做删除
     *
     * @param fileId 文件ID
     * @param chatId 聊天ID
     * @param uid 用户ID
     */
    @Override
    public void delete(String fileId, Long chatId, String uid) {
        chatDataService.deleteChatFileReq(fileId, chatId, uid);
    }

    private void checkFile(String uid, String fileName, String fileUrl, Long fileSize, ChatFileLimitEnum limitEnum) {
        if (StringUtils.isBlank(fileName) || StringUtils.isBlank(fileUrl)) {
            throw new BusinessException(ResponseEnum.LONG_CONTENT_MISS_FILE_INFO);
        }
        if (limitEnum == null) {
            throw new BusinessException(ResponseEnum.LONG_CONTENT_WRONG_BUSINESS_TYPE);
        }
        // Current document size validation
        if (fileSize > limitEnum.getMaxSize()) {
            throw new BusinessException(ResponseEnum.LONG_CONTENT_FILE_SIZE_OUT_LIMIT);
        }
        // Daily maximum upload count limit
        if (redissonClient.getAtomicLong(limitEnum.getRedisPrefix() + uid).addAndGet(1L) > limitEnum.getDailyUploadNum()) {
            redissonClient.getAtomicLong(limitEnum.getRedisPrefix() + uid).addAndGet(-1L);
            throw new BusinessException(ResponseEnum.LONG_CONTENT_FILE_NUM_OUT_LIMIT);
        }
    }

    public Map<String, String> documentHandler(Long chatFileUserId, String uid, Long chatId, String fileUrl, String fileName, Long fileSize,
                    ChatFileLimitEnum limitEnum, String fileBusinessKey,
                    Integer documentType, String paramName) {
        // Metering
        redissonClient.getKeys().expire(limitEnum.getRedisPrefix() + uid, CommonUtil.calculateSecondsUntilEndOfDay(), TimeUnit.SECONDS);
        log.info("User {} currently uploaded file count: {}", uid, redissonClient.getBucket(limitEnum.getRedisPrefix() + uid).get());
        // External link has already implemented the insert operation
        if (chatFileUserId == null) {
            // First write to chat_file_user table as placeholder to get chatFileUserId
            Integer businessType = limitEnum.getValue();
            ChatFileUser chatFileUser = ChatFileUser.builder()
                            .fileId(null)
                            .fileName(fileName)
                            .uid(uid)
                            .fileUrl(fileUrl)
                            .fileSize(fileSize)
                            .createTime(LocalDateTime.now())
                            .updateTime(LocalDateTime.now())
                            .clientType(1)
                            .deleted(0)
                            .businessType(businessType)
                            .display(limitEnum.getDisplay())
                            .fileStatus(LongContextStatusEnum.PROCESSING.getValue())
                            .extraLink(null)
                            .fileBusinessKey(fileBusinessKey)
                            .documentType(documentType)
                            .collectOriginFrom(null)
                            .icon(null)
                            .fileIndex(chatDataService.getFileUserCount(uid) + 1)
                            .build();
            chatFileUserId = chatDataService.createChatFileUser(chatFileUser).getId();
        }

        // Subsequent processing
        return agentMaasHandle(uid, chatId, fileUrl, fileName, chatFileUserId, limitEnum, paramName);
    }

    private Map<String, String> agentMaasHandle(String uid,
                    Long chatId,
                    String fileUrl,
                    String fileName,
                    Long chatFileUserId,
                    ChatFileLimitEnum limitEnum, String paramName) {
        log.info("agent platform document parsing, uid: {}, chatId:{}, fileUrl: {}, fileName: {}, chatFileUserId: {}",
                        uid, chatId, fileUrl, fileName, chatFileUserId);
        // Bind chatFileUser and chatFileReq
        String fileId = "agent_" + chatFileUserId;
        // After parsing is complete, update fileId
        chatDataService.setFileId(chatFileUserId, fileId);
        ChatFileReq chatFileReq = ChatFileReq.builder()
                        .fileId(fileId)
                        .chatId(chatId)
                        .uid(uid)
                        .createTime(LocalDateTime.now())
                        .updateTime(LocalDateTime.now())
                        .clientType(1)
                        .businessType(limitEnum.getValue())
                        .build();
        chatDataService.createChatFileReq(chatFileReq);
        // Set file status to completed
        chatDataService.setProcessed(chatFileUserId);


        if (StrUtil.isNotEmpty(paramName)) {
            List<BotChatFileParam> oneByChatIdAndNameList = chatDataService.findAllBotChatFileParamByChatIdAndNameAndIsDelete(chatId, paramName, 0);
            if (ObjectUtil.isEmpty(oneByChatIdAndNameList)) {
                BotChatFileParam botChatFileParam = new BotChatFileParam();
                botChatFileParam.setName(paramName);
                botChatFileParam.setChatId(chatId);
                botChatFileParam.setUid(uid);
                List<String> fileIds = new ArrayList<>();
                fileIds.add(fileId);

                List<String> fileUrls = new ArrayList<>();
                fileUrls.add(fileUrl);
                botChatFileParam.setFileIds(fileIds);
                botChatFileParam.setFileUrls(fileUrls);
                botChatFileParam.setIsDelete(0);
                botChatFileParam.setCreateTime(LocalDateTime.now());
                chatDataService.createBotChatFileParam(botChatFileParam);
            } else {
                BotChatFileParam oneByChatIdAndName = oneByChatIdAndNameList.getFirst();
                oneByChatIdAndName.getFileIds().add(fileId);
                oneByChatIdAndName.getFileUrls().add(fileUrl);
                oneByChatIdAndName.setUpdateTime(LocalDateTime.now());
                chatDataService.updateBotChatFileParam(oneByChatIdAndName);
            }
        }
        Map<String, String> req = new HashMap<>();
        req.put("file_id", fileId);
        return req;
    }
}
