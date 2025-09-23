package com.iflytek.astra.console.hub.data.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.collection.CollectionUtil;
import cn.hutool.core.util.ObjectUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.iflytek.astra.console.commons.constant.ResponseEnum;
import com.iflytek.astra.console.commons.entity.bot.BotChatFileParam;
import com.iflytek.astra.console.commons.entity.chat.*;
import com.iflytek.astra.console.commons.exception.BusinessException;
import com.iflytek.astra.console.commons.mapper.chat.ChatListMapper;
import com.iflytek.astra.console.commons.mapper.chat.ChatTreeIndexMapper;
import com.iflytek.astra.console.commons.service.data.ChatDataService;
import com.iflytek.astra.console.hub.enums.LongContextStatusEnum;
import com.iflytek.astra.console.hub.mapper.*;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.*;
import java.util.stream.Collectors;

import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.CollectionUtils;

@Service
public class ChatDataServiceImpl implements ChatDataService {

    @Autowired
    private ChatListMapper chatListMapper;

    @Autowired
    private ChatReqRecordsMapper chatReqRecordsMapper;

    @Autowired
    private ChatRespRecordsMapper chatRespRecordsMapper;

    @Autowired
    private ChatReqModelMapper chatReqModelMapper;

    @Autowired
    private ChatRespModelMapper chatRespModelMapper;

    @Autowired
    private ChatReasonRecordsMapper chatReasonRecordsMapper;

    @Autowired
    private ChatTraceSourceMapper chatTraceSourceMapper;

    @Autowired
    private ChatFileReqMapper chatFileReqMapper;

    @Autowired
    private ChatFileUserMapper chatFileUserMapper;

    @Autowired
    private ChatTreeIndexMapper chatTreeIndexMapper;

    @Autowired
    private BotChatFileParamMapper botChatFileParamMapper;

    public static final int MatHistoryNumbers = 8000;

    @Override
    public List<ChatReqRecords> findRequestsByChatIdAndUid(Long chatId, String uid) {
        LambdaQueryWrapper<ChatReqRecords> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(ChatReqRecords::getChatId, chatId);
        wrapper.eq(ChatReqRecords::getUid, uid);
        wrapper.orderByDesc(ChatReqRecords::getCreateTime);
        return chatReqRecordsMapper.selectList(wrapper);
    }

    @Override
    public List<ChatReqRecords> findRequestsByChatIdAndTimeRange(Long chatId, LocalDateTime startTime, LocalDateTime endTime) {
        LambdaQueryWrapper<ChatReqRecords> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(ChatReqRecords::getChatId, chatId);
        wrapper.between(ChatReqRecords::getCreateTime, startTime, endTime);
        wrapper.orderByDesc(ChatReqRecords::getCreateTime);
        return chatReqRecordsMapper.selectList(wrapper);
    }

    @Override
    public ChatReqRecords createRequest(ChatReqRecords chatReqRecords) {
        ChatList chatList = chatListMapper.selectOne(Wrappers.lambdaQuery(ChatList.class)
                .eq(ChatList::getId, chatReqRecords.getChatId())
                .eq(ChatList::getUid, chatReqRecords.getUid()));
        if (chatList != null && chatList.getEnable() == 0) {
            throw new BusinessException(ResponseEnum.CHAT_REQ_ZJ_ERROR);
        }

        chatReqRecordsMapper.insert(chatReqRecords);

        LambdaUpdateWrapper<ChatList> updateWrapper = Wrappers.lambdaUpdate(ChatList.class);
        updateWrapper.eq(ChatList::getId, chatReqRecords.getChatId());
        updateWrapper.set(ChatList::getUpdateTime, LocalDateTime.now());
        chatListMapper.update(null, updateWrapper);
        LambdaQueryWrapper<ChatTreeIndex> chatTreeQuery = new LambdaQueryWrapper<ChatTreeIndex>()
                .eq(ChatTreeIndex::getChildChatId, chatReqRecords.getChatId())
                .eq(ChatTreeIndex::getUid, chatReqRecords.getUid())
                .orderByAsc(ChatTreeIndex::getId);
        List<ChatTreeIndex> childChatTreeIndexList = chatTreeIndexMapper.selectList(chatTreeQuery);
        Long rootId = childChatTreeIndexList.getFirst().getRootChatId();
        if (rootId != null && !rootId.equals(chatReqRecords.getChatId())) {
            updateWrapper.eq(ChatList::getId, rootId);
            chatListMapper.update(null, updateWrapper);
        }
        return chatReqRecords;
    }

    @Override
    public List<ChatRespRecords> findResponsesByReqId(Long reqId) {
        LambdaQueryWrapper<ChatRespRecords> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(ChatRespRecords::getReqId, reqId);
        wrapper.orderByDesc(ChatRespRecords::getCreateTime);
        return chatRespRecordsMapper.selectList(wrapper);
    }

    @Override
    public List<ChatRespRecords> findResponsesByChatId(Long chatId) {
        LambdaQueryWrapper<ChatRespRecords> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(ChatRespRecords::getChatId, chatId);
        wrapper.orderByDesc(ChatRespRecords::getCreateTime);
        return chatRespRecordsMapper.selectList(wrapper);
    }

    @Override
    public ChatRespRecords createResponse(ChatRespRecords chatRespRecords) {
        chatRespRecordsMapper.insert(chatRespRecords);
        return chatRespRecords;
    }

    @Override
    public long countChatsByUid(String uid) {
        LambdaQueryWrapper<ChatList> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(ChatList::getUid, uid);
        wrapper.eq(ChatList::getIsDelete, 0);
        return chatListMapper.selectCount(wrapper);
    }

    @Override
    public long countMessagesByChatId(Long chatId) {
        LambdaQueryWrapper<ChatReqRecords> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(ChatReqRecords::getChatId, chatId);
        return chatReqRecordsMapper.selectCount(wrapper);
    }

    @Override
    public List<ChatList> findRecentChatsByUid(String uid, int limit) {
        LambdaQueryWrapper<ChatList> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(ChatList::getUid, uid);
        wrapper.eq(ChatList::getIsDelete, 0);
        wrapper.orderByDesc(ChatList::getUpdateTime);
        wrapper.last("LIMIT " + limit);
        return chatListMapper.selectList(wrapper);
    }

    /**
     * Get multimodal assistant request history by chatID
     *
     * @param uid
     * @param chatId
     * @return
     */
    @Override
    public List<ChatReqModelDto> getReqModelBotHistoryByChatId(String uid, Long chatId) {
        // 1. Get chat_req_records records
        List<ChatReqRecords> queryList = chatReqRecordsMapper.selectList(
                Wrappers.<ChatReqRecords>lambdaQuery()
                        .eq(ChatReqRecords::getUid, uid)
                        .eq(ChatReqRecords::getChatId, chatId)
                        .orderByDesc(ChatReqRecords::getCreateTime)
                        .last("LIMIT 500"));
        // 2. Get reqId list
        List<Long> reqIdList = queryList.stream().map(ChatReqRecords::getId).collect(Collectors.toList());
        // 3. Get chat_req_model records
        if (CollectionUtils.isEmpty(reqIdList)) {
            return new ArrayList<>();
        }
        List<ChatReqModel> chatReqModelList = chatReqModelMapper.selectList(Wrappers.lambdaQuery(ChatReqModel.class)
                .in(ChatReqModel::getChatReqId, reqIdList));
        // 4. Process queryList and chatReqModelList data
        Map<Long, ChatReqRecords> chatReqRecordsMap = queryList.stream()
                .collect(Collectors.toMap(ChatReqRecords::getId, chatReqRecords -> chatReqRecords, (existing, replacement) -> existing));
        Map<Long, ChatReqModel> chatReqModelMap = chatReqModelList.stream()
                .collect(Collectors.toMap(ChatReqModel::getChatReqId, chatReqModel -> chatReqModel, (existing, replacement) -> existing));
        // 5. Perform merge
        List<ChatReqModelDto> chatReqModelDtos = new ArrayList<>();
        for (Long reqId : reqIdList) {
            ChatReqModelDto chatReqModelDto = new ChatReqModelDto();
            ChatReqRecords reqRecords = chatReqRecordsMap.get(reqId);
            // Ignore requests that are not the latest new conversation
            if (reqRecords.getNewContext() == 0) {
                break;
            }
            BeanUtil.copyProperties(reqRecords, chatReqModelDto);
            ChatReqModel chatReqModel = chatReqModelMap.get(reqId);
            if (chatReqModel != null) {
                chatReqModelDto.setUrl(chatReqModel.getUrl());
                chatReqModelDto.setType(chatReqModel.getType());
                chatReqModelDto.setImgDesc(chatReqModel.getImgDesc());
                chatReqModelDto.setOcrResult(chatReqModel.getOcrResult());
                chatReqModelDto.setDataId(chatReqModel.getDataId());
                chatReqModelDto.setNeedHis(chatReqModel.getNeedHis());
                chatReqModelDto.setIntention(chatReqModel.getIntention());
            }
            chatReqModelDtos.add(chatReqModelDto);
        }
        // Sort in chronological order
        return chatReqModelDtos;
    }

    /**
     * Get Q history with multimodal information by chatID
     *
     * @param uid
     * @param chatId
     * @return
     */
    @Override
    public List<ChatRespModelDto> getChatRespModelBotHistoryByChatId(String uid, Long chatId, List<Long> reqIds) {
        List<ChatRespModelDto> chatRespModelDtos = new ArrayList<>();
        Map<Long, Integer> reqIdsMap = new HashMap<>();
        List<ChatRespRecords> chatRespRecords;
        LambdaQueryWrapper<ChatRespRecords> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(ChatRespRecords::getChatId, chatId);
        wrapper.eq(ChatRespRecords::getUid, uid);
        if (!reqIds.isEmpty()) {
            wrapper.in(ChatRespRecords::getReqId, reqIds);
        }
        wrapper.orderByDesc(ChatRespRecords::getId);
        chatRespRecords = chatRespRecordsMapper.selectList(wrapper);
        for (int i = 0; i < chatRespRecords.size(); i++) {
            reqIdsMap.put(chatRespRecords.get(i).getReqId(), i);
            ChatRespModelDto tempDto = new ChatRespModelDto();
            BeanUtils.copyProperties(chatRespRecords.get(i), tempDto);
            chatRespModelDtos.add(tempDto);
        }
        if (chatRespRecords.isEmpty()) {
            return null;
        }
        List<ChatRespModel> chatRespModels = chatRespModelMapper.selectList(
                Wrappers.lambdaQuery(ChatRespModel.class)
                        .eq(ChatRespModel::getUid, uid)
                        .eq(ChatRespModel::getChatId, chatId)
                        .in(ChatRespModel::getReqId, reqIdsMap.keySet()));
        if (!chatRespModels.isEmpty()) {
            for (int i = 0; i < chatRespModels.size(); i++) {
                Integer index = reqIdsMap.get(chatRespModels.get(i).getReqId());
                if (Objects.nonNull(index)) {
                    chatRespModelDtos.get(index).setUrl(chatRespModels.get(i).getUrl());
                    chatRespModelDtos.get(index).setType(chatRespModels.get(i).getType());
                    chatRespModelDtos.get(index).setContent(chatRespModels.get(i).getContent());
                    chatRespModelDtos.get(index).setNeedHis(chatRespModels.get(i).getNeedHis());
                    chatRespModelDtos.get(index).setDataId(chatRespModels.get(i).getDataId());
                }
            }
        }
        return chatRespModelDtos;
    }


    /**
     * Create reasoning process
     *
     * @param chatReasonRecords
     */
    @Override
    public ChatReasonRecords createReasonRecord(ChatReasonRecords chatReasonRecords) {
        chatReasonRecordsMapper.insert(chatReasonRecords);
        return chatReasonRecords;
    }

    /**
     * Create trace source record
     *
     * @param chatTraceSource
     */
    @Override
    public ChatTraceSource createTraceSource(ChatTraceSource chatTraceSource) {
        chatTraceSourceMapper.insert(chatTraceSource);
        return chatTraceSource;
    }

    /**
     * Query request record by reqId
     */
    @Override
    public ChatReqRecords findRequestById(Long reqId) {
        return chatReqRecordsMapper.selectById(reqId);
    }

    /**
     * Update response record by uid,chatId,reqId
     */
    @Override
    public Integer updateByUidAndChatIdAndReqId(ChatRespRecords chatRespRecords) {
        LambdaUpdateWrapper<ChatRespRecords> updateWrapper = Wrappers.lambdaUpdate(ChatRespRecords.class);
        updateWrapper.eq(ChatRespRecords::getUid, chatRespRecords.getUid());
        updateWrapper.eq(ChatRespRecords::getChatId, chatRespRecords.getChatId());
        updateWrapper.eq(ChatRespRecords::getReqId, chatRespRecords.getReqId());
        return chatRespRecordsMapper.update(chatRespRecords, updateWrapper);
    }

    /**
     * Query corresponding ChatRespRecords by uid,chatId,reqId
     */
    @Override
    public ChatRespRecords findResponseByUidAndChatIdAndReqId(String uid, Long chatId, Long reqId) {
        LambdaQueryWrapper<ChatRespRecords> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(ChatRespRecords::getUid, uid);
        wrapper.eq(ChatRespRecords::getChatId, chatId);
        wrapper.eq(ChatRespRecords::getReqId, reqId);
        return chatRespRecordsMapper.selectOne(wrapper);
    }

    /**
     * Query corresponding ChatReasonRecords by uid,chatId,reqId
     */
    @Override
    public ChatReasonRecords findReasonByUidAndChatIdAndReqId(String uid, Long chatId, Long reqId) {
        LambdaQueryWrapper<ChatReasonRecords> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(ChatReasonRecords::getUid, uid);
        wrapper.eq(ChatReasonRecords::getChatId, chatId);
        wrapper.eq(ChatReasonRecords::getReqId, reqId);
        return chatReasonRecordsMapper.selectOne(wrapper);
    }

    /**
     * Update reasoning record by uid,chatId,reqId
     */
    @Override
    public Integer updateReasonByUidAndChatIdAndReqId(ChatReasonRecords chatReasonRecords) {
        LambdaUpdateWrapper<ChatReasonRecords> updateWrapper = Wrappers.lambdaUpdate(ChatReasonRecords.class);
        updateWrapper.eq(ChatReasonRecords::getUid, chatReasonRecords.getUid());
        updateWrapper.eq(ChatReasonRecords::getChatId, chatReasonRecords.getChatId());
        updateWrapper.eq(ChatReasonRecords::getReqId, chatReasonRecords.getReqId());
        return chatReasonRecordsMapper.update(chatReasonRecords, updateWrapper);
    }

    /**
     * Query corresponding ChatTraceSource by uid,chatId,reqId
     */
    @Override
    public ChatTraceSource findTraceSourceByUidAndChatIdAndReqId(String uid, Long chatId, Long reqId) {
        LambdaQueryWrapper<ChatTraceSource> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(ChatTraceSource::getUid, uid);
        wrapper.eq(ChatTraceSource::getChatId, chatId);
        wrapper.eq(ChatTraceSource::getReqId, reqId);
        return chatTraceSourceMapper.selectOne(wrapper);
    }

    /**
     * Update trace source record by uid,chatId,reqId
     */
    @Override
    public Integer updateTraceSourceByUidAndChatIdAndReqId(ChatTraceSource chatTraceSource) {
        LambdaUpdateWrapper<ChatTraceSource> updateWrapper = Wrappers.lambdaUpdate(ChatTraceSource.class);
        updateWrapper.eq(ChatTraceSource::getUid, chatTraceSource.getUid());
        updateWrapper.eq(ChatTraceSource::getChatId, chatTraceSource.getChatId());
        updateWrapper.eq(ChatTraceSource::getReqId, chatTraceSource.getReqId());
        return chatTraceSourceMapper.update(chatTraceSource, updateWrapper);
    }

    /**
     * Update questions before new conversation
     *
     * @param uid
     * @param chatId
     */
    @Override
    public Integer updateNewContextByUidAndChatId(String uid, Long chatId) {
        LambdaUpdateWrapper<ChatReqRecords> updateWrapper = Wrappers.lambdaUpdate(ChatReqRecords.class);
        updateWrapper.eq(ChatReqRecords::getUid, uid);
        updateWrapper.eq(ChatReqRecords::getChatId, chatId);
        updateWrapper.set(ChatReqRecords::getNewContext, 1);
        return chatReqRecordsMapper.update(null, updateWrapper);
    }

    @Override
    public List<ChatTraceSource> findTraceSourcesByChatId(Long chatId) {
        return chatTraceSourceMapper.selectList(Wrappers.lambdaQuery(ChatTraceSource.class)
                .eq(ChatTraceSource::getChatId, chatId));
    }

    @Override
    public List<ChatReasonRecords> getReasonRecordsByChatId(Long chatId) {
        LambdaQueryWrapper<ChatReasonRecords> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(ChatReasonRecords::getChatId, chatId);
        wrapper.orderByAsc(ChatReasonRecords::getCreateTime);
        return chatReasonRecordsMapper.selectList(wrapper);
    }

    @Override
    public List<ChatFileReq> getFileList(String uid, Long chatId) {
        return chatFileReqMapper.selectList(Wrappers.lambdaQuery(ChatFileReq.class)
                .eq(ChatFileReq::getChatId, chatId)
                .eq(ChatFileReq::getUid, uid)
                .eq(ChatFileReq::getDeleted, 0));
    }

    @Override
    public ChatFileUser getByFileIdAll(String fileId, String uid) {
        LocalDateTime lastTime = getLastTime();
        // Avoid duplicate fileId in historical dirty data
        List<ChatFileUser> chatFileUsers = chatFileUserMapper.selectList(Wrappers.lambdaQuery(ChatFileUser.class)
                .eq(ChatFileUser::getUid, uid)
                .eq(ChatFileUser::getFileId, fileId)
                .ge(ChatFileUser::getCreateTime, lastTime)
                .orderByDesc(ChatFileUser::getCreateTime));
        if (CollectionUtil.isNotEmpty(chatFileUsers)) {
            return chatFileUsers.getFirst();
        }
        return null;
    }

    @Override
    public ChatFileUser getByFileId(String fileId, String uid) {
        LocalDateTime lastTime = getLastTime();
        return chatFileUserMapper.selectOne(Wrappers.lambdaQuery(ChatFileUser.class)
                .eq(ChatFileUser::getUid, uid)
                .eq(ChatFileUser::getFileId, fileId)
                .ge(ChatFileUser::getCreateTime, lastTime)
                .eq(ChatFileUser::getDeleted, 0));
    }

    @Override
    public List<ChatReqModelDto> getReqModelWithImgByChatId(String uid, Long chatId) {
        List<ChatReqModel> chatReqModels = chatReqModelMapper.selectList(
                Wrappers.lambdaQuery(ChatReqModel.class)
                        .select(ChatReqModel::getId, ChatReqModel::getUrl, ChatReqModel::getCreateTime)
                        .eq(ChatReqModel::getUid, uid)
                        .eq(ChatReqModel::getChatId, chatId)
                        .eq(ChatReqModel::getType, 1)
                        .orderByDesc(ChatReqModel::getCreateTime));

        return chatReqModels.stream()
                .map(model -> {
                    ChatReqModelDto dto = new ChatReqModelDto();
                    dto.setId(Long.valueOf(model.getId()));
                    dto.setUrl(model.getUrl());
                    dto.setCreateTime(model.getCreateTime());
                    return dto;
                })
                .collect(Collectors.toList());
    }

    @Override
    public ChatReqModel createChatReqModel(ChatReqModel chatReqModel) {
        chatReqModelMapper.insert(chatReqModel);
        return chatReqModel;
    }

    @Override
    public List<BotChatFileParam> findBotChatFileParamsByChatIdAndIsDelete(Long chatId, Integer isDelete) {
        LambdaQueryWrapper<BotChatFileParam> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(BotChatFileParam::getChatId, chatId);
        wrapper.eq(BotChatFileParam::getIsDelete, isDelete);
        return botChatFileParamMapper.selectList(wrapper);
    }

    @Override
    @Transactional
    public void updateFileReqId(Long chatId, String uid, List<String> fileIds, Long reqId, boolean edit, Long leftId) {
        List<ChatFileReq> chatFileReqs = chatFileReqMapper.selectList(Wrappers.lambdaQuery(ChatFileReq.class)
                .eq(ChatFileReq::getChatId, chatId)
                .eq(ChatFileReq::getReqId, leftId)
                .eq(ChatFileReq::getDeleted, 0));
        if (CollectionUtil.isNotEmpty(chatFileReqs)) {
            chatFileReqs.forEach(e -> {
                ChatFileReq chatFileReq = ChatFileReq.builder().reqId(reqId).fileId(e.getFileId()).chatId(chatId).uid(uid).businessType(e.getBusinessType()).build();
                chatFileReqMapper.insert(chatFileReq);
            });
        } else {
            // Q&A interface binds file
            if (CollectionUtil.isNotEmpty(fileIds)) {
                ChatFileReq chatFileReq = ChatFileReq.builder().reqId(reqId).build();
                chatFileReqMapper.update(chatFileReq, Wrappers.lambdaQuery(ChatFileReq.class)
                        .eq(ChatFileReq::getChatId, chatId)
                        .eq(ChatFileReq::getUid, uid)
                        .eq(ChatFileReq::getDeleted, 0)
                        .in(ChatFileReq::getFileId, fileIds)
                        .isNull(ChatFileReq::getReqId));
            }
        }
    }

    @Override
    public ChatFileUser createChatFileUser(ChatFileUser chatFileUser) {
        chatFileUserMapper.insert(chatFileUser);
        return chatFileUser;
    }

    @Override
    public Integer getFileUserCount(String uid) {
        LocalDate today = LocalDate.now();
        Date startOfDay = Date.from(today.atStartOfDay(ZoneId.systemDefault()).toInstant());
        Date endOfDay = Date.from(today.plusDays(1).atStartOfDay(ZoneId.systemDefault()).toInstant());

        Long count = chatFileUserMapper.selectCount(Wrappers.lambdaQuery(ChatFileUser.class)
                .eq(ChatFileUser::getUid, uid)
                .eq(ChatFileUser::getDisplay, 0)
                .between(ChatFileUser::getCreateTime, startOfDay, endOfDay));
        if (count == null) {
            count = 0L;
        }
        return Math.toIntExact(count);
    }

    @Override
    @Transactional
    public ChatFileUser setFileId(Long chatFileUserId, String fileId) {
        ChatFileUser chatFileUser = chatFileUserMapper.selectOne(Wrappers.lambdaQuery(ChatFileUser.class)
                .eq(ChatFileUser::getId, chatFileUserId)
                .eq(ChatFileUser::getDeleted, 0));
        if (ObjectUtil.isEmpty(chatFileUser)) {
            return null;
        }
        chatFileUser.setFileId(fileId);
        chatFileUser.setUpdateTime(LocalDateTime.now());
        chatFileUserMapper.updateById(chatFileUser);
        return chatFileUser;
    }

    @Override
    public ChatFileReq createChatFileReq(ChatFileReq chatFileReq) {
        chatFileReqMapper.insert(chatFileReq);
        return chatFileReq;
    }

    @Override
    public void setProcessed(Long chatFileUserId) {
        ChatFileUser chatFileUser = chatFileUserMapper.selectOne(Wrappers.lambdaQuery(ChatFileUser.class)
                .eq(ChatFileUser::getId, chatFileUserId));
        chatFileUser.setFileStatus(LongContextStatusEnum.PROCESSED.getValue());
        chatFileUser.setUpdateTime(LocalDateTime.now());
        chatFileUserMapper.updateById(chatFileUser);
    }

    @Override
    public List<BotChatFileParam> findAllBotChatFileParamByChatIdAndNameAndIsDelete(Long chatId, String name, Integer isDelete) {
        LambdaQueryWrapper<BotChatFileParam> wrapper = Wrappers.lambdaQuery(BotChatFileParam.class)
                .eq(BotChatFileParam::getChatId, chatId)
                .eq(BotChatFileParam::getName, name)
                .eq(BotChatFileParam::getIsDelete, isDelete);
        return botChatFileParamMapper.selectList(wrapper);
    }

    @Override
    public BotChatFileParam createBotChatFileParam(BotChatFileParam botChatFileParam) {
        botChatFileParamMapper.insert(botChatFileParam);
        return botChatFileParam;
    }

    @Override
    public BotChatFileParam updateBotChatFileParam(BotChatFileParam botChatFileParam) {
        botChatFileParamMapper.updateById(botChatFileParam);
        return botChatFileParam;
    }

    @Override
    public ChatFileUser findChatFileUserByIdAndUid(Long linkId, String uid) {
        LocalDateTime lastTime = getLastTime();
        return chatFileUserMapper.selectOne(Wrappers.lambdaQuery(ChatFileUser.class)
                .eq(ChatFileUser::getId, linkId)
                .eq(ChatFileUser::getUid, uid)
                .ge(ChatFileUser::getCreateTime, lastTime));
    }

    @Override
    public void deleteChatFileReq(String fileId, Long chatId, String uid) {
        ChatFileReq chatFileReq = ChatFileReq.builder()
                .deleted(1)
                .updateTime(LocalDateTime.now())
                .build();
        chatFileReqMapper.update(chatFileReq, Wrappers.lambdaQuery(ChatFileReq.class)
                .eq(ChatFileReq::getChatId, chatId)
                .eq(ChatFileReq::getFileId, fileId)
                .eq(ChatFileReq::getUid, uid)
                .eq(ChatFileReq::getDeleted, 0)
                .isNull(ChatFileReq::getReqId));
    }

    private LocalDateTime getLastTime() {
        LocalDate startTime = LocalDate.now();
        LocalDate days = startTime.minusDays(365);
        return days.atStartOfDay().atZone(ZoneId.systemDefault()).toLocalDateTime();
    }
}
