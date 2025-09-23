package com.iflytek.astron.console.commons.service.data.impl;

import cn.hutool.core.collection.CollectionUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.iflytek.astron.console.commons.entity.chat.ChatBotListDto;
import com.iflytek.astron.console.commons.entity.chat.ChatList;
import com.iflytek.astron.console.commons.entity.chat.ChatTreeIndex;
import com.iflytek.astron.console.commons.mapper.chat.ChatListMapper;
import com.iflytek.astron.console.commons.mapper.chat.ChatTreeIndexMapper;
import com.iflytek.astron.console.commons.service.data.ChatListDataService;
import com.iflytek.astron.console.commons.entity.bot.ChatBotBase;
import com.iflytek.astron.console.commons.entity.bot.ChatBotList;
import com.iflytek.astron.console.commons.mapper.bot.ChatBotListMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class ChatListDataServiceImpl implements ChatListDataService {

    @Autowired
    private ChatListMapper chatListMapper;

    @Autowired
    private ChatTreeIndexMapper chatTreeIndexMapper;

    @Autowired
    private ChatBotListMapper chatBotListMapper;

    @Override
    public ChatList findByUidAndChatId(String uid, Long chatId) {
        if (uid == null || chatId == null) {
            log.warn("Query parameters cannot be null: uid={}, chatId={}", uid, chatId);
            return null;
        }

        LambdaQueryWrapper<ChatList> wrapper = Wrappers.lambdaQuery(ChatList.class)
                .eq(ChatList::getUid, uid)
                .eq(ChatList::getId, chatId)
                .eq(ChatList::getIsDelete, 0);

        ChatList result = chatListMapper.selectOne(wrapper);
        log.debug("Found chat list by uid={} and chatId={}: {}", uid, chatId, result);

        return result;
    }

    @Override
    public List<ChatTreeIndex> findChatTreeIndexByChatIdOrderById(Long rootChatId) {
        LambdaQueryWrapper<ChatTreeIndex> chatTreeQuery = new LambdaQueryWrapper<ChatTreeIndex>()
                .eq(ChatTreeIndex::getRootChatId, rootChatId)
                .orderByDesc(ChatTreeIndex::getId);
        return chatTreeIndexMapper.selectList(chatTreeQuery);
    }

    @Override
    public ChatList createChat(ChatList chatList) {
        chatListMapper.insert(chatList);
        return chatList;
    }

    @Override
    public ChatTreeIndex createChatTreeIndex(ChatTreeIndex chatTreeIndex) {
        chatTreeIndexMapper.insert(chatTreeIndex);
        return chatTreeIndex;
    }

    @Override
    public List<ChatTreeIndex> getListByRootChatId(Long rootChatId, String uid) {
        LambdaQueryWrapper<ChatTreeIndex> chatTreeQuery = new LambdaQueryWrapper<ChatTreeIndex>()
                .eq(ChatTreeIndex::getRootChatId, rootChatId)
                .orderByAsc(ChatTreeIndex::getId);
        return chatTreeIndexMapper.selectList(chatTreeQuery);
    }

    @Override
    public List<ChatBotListDto> getBotChatList(String uid) {
        return chatListMapper.getBotChatList(uid);
    }

    @Override
    public ChatList findLatestEnabledChatByUserAndBot(String uid, Integer botId) {
        if (uid == null || botId == null) {
            log.warn("Query parameters cannot be null: uid={}, botId={}", uid, botId);
            return null;
        }

        LambdaQueryWrapper<ChatList> wrapper = Wrappers.lambdaQuery(ChatList.class)
                .eq(ChatList::getUid, uid)
                .eq(ChatList::getBotId, botId)
                .eq(ChatList::getEnable, 1)
                .orderByDesc(ChatList::getUpdateTime)
                .last("LIMIT 1");

        ChatList result = chatListMapper.selectOne(wrapper);
        log.debug("Found latest enabled chat list by uid={} and botId={}: {}", uid, botId, result);

        return result;
    }

    @Override
    public int reactivateChat(Long id) {
        if (id == null) {
            log.warn("Reactivate chat list parameter cannot be null: id=null");
            return 0;
        }

        ChatList chatList = new ChatList();
        chatList.setId(id);
        chatList.setIsDelete(0);

        int result = chatListMapper.updateById(chatList);
        log.debug("Reactivated chat list id={}, affected rows={}", id, result);

        return result;
    }

    @Override
    public int reactivateChatBatch(List<Long> chatIdList) {
        if (chatIdList == null || chatIdList.isEmpty()) {
            log.warn("Batch reactivate chat list parameter cannot be null or empty: chatIdList={}", chatIdList);
            return 0;
        }

        // Use MyBatis-Plus batch update
        LambdaQueryWrapper<ChatList> wrapper = Wrappers.lambdaQuery(ChatList.class)
                .in(ChatList::getId, chatIdList);

        ChatList updateEntity = new ChatList();
        updateEntity.setIsDelete(0);

        int result = chatListMapper.update(updateEntity, wrapper);
        log.debug("Batch reactivated chat list chatIdList={}, affected rows={}", chatIdList, result);

        return result;
    }

    @Override
    public long addRootTree(Long curChatId, String uid) {
        // Check if current chat already exists in child nodes
        LambdaQueryWrapper<ChatTreeIndex> chatTreeQuery1 = new LambdaQueryWrapper<ChatTreeIndex>()
                .eq(ChatTreeIndex::getChildChatId, curChatId)
                .eq(ChatTreeIndex::getUid, uid)
                .orderByAsc(ChatTreeIndex::getId);
        List<ChatTreeIndex> childChatTreeIndexList = chatTreeIndexMapper.selectList(chatTreeQuery1);
        if (CollectionUtil.isNotEmpty(childChatTreeIndexList)) {
            return childChatTreeIndexList.getFirst().getRootChatId();
        } else {
            // Add record
            ChatTreeIndex chatTreeIndex = ChatTreeIndex.builder()
                    .rootChatId(curChatId)
                    .parentChatId(0L)
                    .childChatId(curChatId)
                    .uid(uid)
                    .build();
            chatTreeIndexMapper.insert(chatTreeIndex);
            return curChatId;
        }
    }

    @Override
    public int deactivateChatBotList(String uid, Integer botId) {
        if (uid == null || botId == null) {
            log.warn("Deactivate bot chat list parameter cannot be null: uid={}, botId={}", uid, botId);
            return 0;
        }

        UpdateWrapper<ChatBotList> wrapper = new UpdateWrapper<>();
        wrapper.eq("uid", uid);
        wrapper.eq("real_bot_id", botId);
        wrapper.ne("market_bot_id", 0);
        wrapper.set("is_act", 0);
        wrapper.set("update_time", LocalDateTime.now());

        int result = chatBotListMapper.update(null, wrapper);
        log.debug("Deactivated bot chat list uid={}, botId={}, affected rows={}", uid, botId, result);

        return result;
    }

    @Override
    public List<ChatTreeIndex> getAllListByChildChatId(Long childChatId, String uid) {
        if (childChatId == null || uid == null) {
            log.warn("Query parameters cannot be null: childChatId={}, uid={}", childChatId, uid);
            return List.of();
        }

        ChatTreeIndex childChatTreeIndex = chatTreeIndexMapper.selectOne(Wrappers.lambdaQuery(ChatTreeIndex.class)
                .eq(ChatTreeIndex::getChildChatId, childChatId)
                .eq(ChatTreeIndex::getUid, uid));
        if (childChatTreeIndex == null) {
            return List.of();
        }
        List<ChatTreeIndex> result = chatTreeIndexMapper.selectList(Wrappers.lambdaQuery(ChatTreeIndex.class)
                .eq(ChatTreeIndex::getRootChatId, childChatTreeIndex.getRootChatId()));
        log.debug("Found chat tree index by childChatId={} and uid={}: {}", childChatId, uid, result);

        return result;
    }

    @Override
    public int deleteById(Long id) {
        if (id == null) {
            log.warn("Delete chat list parameter cannot be null: id=null");
            return 0;
        }

        ChatList chatList = new ChatList();
        chatList.setId(id);
        chatList.setIsDelete(1);
        chatList.setUpdateTime(LocalDateTime.now());

        int result = chatListMapper.updateById(chatList);
        log.debug("Deleted chat list id={}, affected rows={}", id, result);

        return result;
    }

    @Override
    public int deleteBatchIds(List<Long> idList) {
        if (idList == null || idList.isEmpty()) {
            log.warn("Batch delete chat list parameter cannot be null or empty: idList={}", idList);
            return 0;
        }

        LambdaQueryWrapper<ChatList> wrapper = Wrappers.lambdaQuery(ChatList.class)
                .in(ChatList::getId, idList);

        ChatList updateEntity = new ChatList();
        updateEntity.setIsDelete(1);
        updateEntity.setUpdateTime(LocalDateTime.now());

        int result = chatListMapper.update(updateEntity, wrapper);
        log.debug("Batch deleted chat list idList={}, affected rows={}", idList, result);

        return result;
    }

    @Override
    public ChatList getBotChat(String uid, Long botId) {
        return chatListMapper.selectOne(Wrappers.lambdaQuery(ChatList.class)
                .eq(ChatList::getUid, uid)
                .eq(ChatList::getBotId, botId)
                .eq(ChatList::getEnable, 1)
                .eq(ChatList::getIsDelete, 0)
                .eq(ChatList::getRootFlag, 1)
                .orderByDesc(ChatList::getId)
                .last("limit 1"));
    }

    @Override
    public ChatBotBase insertChatBotList(ChatBotBase chatBotBase) {
        chatBotListMapper.baseBotInsert(chatBotBase);
        return chatBotBase;
    }

    @Override
    public ChatBotBase updateChatBotList(ChatBotBase chatBotBase) {
        UpdateWrapper<ChatBotList> wrapper = new UpdateWrapper<>();
        wrapper.eq("uid", chatBotBase.getUid());
        wrapper.eq("real_bot_id", chatBotBase.getId());
        wrapper.eq("is_act", 1);
        wrapper.set("name", chatBotBase.getBotName());
        wrapper.set("avatar", chatBotBase.getAvatar());
        wrapper.set("bot_type", chatBotBase.getBotType());
        wrapper.set("bot_desc", chatBotBase.getBotDesc());
        wrapper.set("update_time", LocalDateTime.now());
        chatBotListMapper.update(null, wrapper);
        return chatBotBase;
    }
}
