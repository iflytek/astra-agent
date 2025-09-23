package com.iflytek.astra.console.commons.service.bot.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.iflytek.astra.console.commons.entity.bot.BotDataset;
import com.iflytek.astra.console.commons.mapper.bot.BotDatasetMapper;
import com.iflytek.astra.console.commons.service.bot.BotDatasetService;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

/**
 * @author yun-zhi-ztl
 */
@Service
@Slf4j
public class BotDatasetServiceImpl implements BotDatasetService {

    @Resource
    private BotDatasetMapper botDatasetMapper;

    @Override
    public void deleteByBotId(Integer botId) {
        botDatasetMapper.update(null, Wrappers.lambdaUpdate(BotDataset.class)
                .eq(BotDataset::getBotId, botId)
                .set(BotDataset::getIsAct, 0)
                .set(BotDataset::getUpdateTime, LocalDateTime.now()));
    }
}
