package com.iflytek.astra.console.commons.service.bot.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.iflytek.astra.console.commons.entity.bot.ChatBotTag;
import com.iflytek.astra.console.commons.mapper.bot.ChatBotTagMapper;
import com.iflytek.astra.console.commons.service.bot.ChatBotTagService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

@Service
public class ChatBotTagServiceImpl extends ServiceImpl<ChatBotTagMapper, ChatBotTag> implements ChatBotTagService {

    @Override
    public List<String> getBotTagList(Long botId) {
        if (Objects.isNull(botId)) {
            return Collections.emptyList();
        }
        LambdaQueryWrapper<ChatBotTag> chatBotTagQueryWrapper = Wrappers.lambdaQuery();
        chatBotTagQueryWrapper.eq(ChatBotTag::getBotId, botId)
                        .eq(ChatBotTag::getVerify, 1)
                        .orderByDesc(ChatBotTag::getOrder);
        List<ChatBotTag> chatBotTags = baseMapper.selectList(chatBotTagQueryWrapper);
        if (Objects.nonNull(chatBotTags) && !chatBotTags.isEmpty()) {
            List<String> tags = new ArrayList<>();
            chatBotTags.forEach(chatBotTag -> tags.add(chatBotTag.getTag()));
            return tags;
        }
        return Collections.emptyList();
    }

    @Override
    @Transactional
    public void updateTags(Long botId) {
        // 先将原来可用的标签变为不可用
        ChatBotTag updateChatBotTag = new ChatBotTag();
        updateChatBotTag.setVerify(0);
        baseMapper.update(updateChatBotTag, Wrappers.lambdaQuery(ChatBotTag.class).eq(ChatBotTag::getBotId, botId));
        // 将最新的标签变为可用状态
        updateChatBotTag.setVerify(1);
        baseMapper.update(updateChatBotTag, Wrappers.lambdaQuery(ChatBotTag.class).eq(ChatBotTag::getBotId, botId).eq(ChatBotTag::getIsAct, 1));
    }
}
