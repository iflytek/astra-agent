package com.iflytek.astron.console.commons.service.bot.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.iflytek.astron.console.commons.entity.bot.ChatBotMarket;
import com.iflytek.astron.console.commons.enums.bot.BotStatusEnum;
import com.iflytek.astron.console.commons.mapper.bot.ChatBotMarketMapper;
import com.iflytek.astron.console.commons.service.bot.ChatBotMarketService;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/**
 * @author yun-zhi-ztl
 */
@Service
public class ChatBotMarketServiceImpl implements ChatBotMarketService {
    @Autowired
    private ChatBotMarketMapper chatBotMarketMapper;

    private static final Integer NOT_DELETED = 0;

    @Override
    public Page<ChatBotMarket> getBotPage(Integer type, String search, Integer pageSize, Integer page) {
        Page<ChatBotMarket> marketPage = new Page<>(page, pageSize);
        LambdaQueryWrapper<ChatBotMarket> queryWrapper = Wrappers.lambdaQuery(ChatBotMarket.class)
                .eq(ChatBotMarket::getBotType, type)
                .eq(ChatBotMarket::getIsDelete, NOT_DELETED)
                .orderByDesc(ChatBotMarket::getCreateTime);
        if (StringUtils.isNotBlank(search)) {
            queryWrapper.like(ChatBotMarket::getBotName, "%" + search + "%");
        }
        return chatBotMarketMapper.selectPage(marketPage, queryWrapper);
    }

    @Transactional(propagation = Propagation.REQUIRED)
    @Override
    public void updateBotMarketStatus(String uid, Integer botId) {
        // First check if botId is listed in the market
        Long count = chatBotMarketMapper.selectCount(Wrappers.lambdaQuery(ChatBotMarket.class)
                .eq(ChatBotMarket::getUid, uid)
                .eq(ChatBotMarket::getBotId, botId)
                .eq(ChatBotMarket::getBotStatus, BotStatusEnum.PUBLISHED.getCode())
                .eq(ChatBotMarket::getIsDelete, 0));
        if (count != null && count.intValue() > 0) {
            UpdateWrapper<ChatBotMarket> marketWrapper = new UpdateWrapper<>();
            marketWrapper.eq("uid", uid);
            marketWrapper.eq("bot_id", botId);
            marketWrapper.set("bot_status", 4);
            marketWrapper.set("update_time", LocalDateTime.now());
            chatBotMarketMapper.update(null, marketWrapper);
        }
    }

}
