package com.iflytek.astra.console.hub.service.bot.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.iflytek.astra.console.hub.dto.bot.BotOffiaccountStatusEnum;
import com.iflytek.astra.console.hub.entity.BotOffiaccount;
import com.iflytek.astra.console.hub.mapper.BotOffiaccountMapper;
import com.iflytek.astra.console.hub.service.bot.BotOffiaccountService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;

@Slf4j
@Service
public class BotOffiaccountServiceImpl implements BotOffiaccountService {

    @Autowired
    private BotOffiaccountMapper botOffiaccountMapper;

    @Override
    public List<BotOffiaccount> getAccountList(String uid) {
        return botOffiaccountMapper.selectList(Wrappers.lambdaQuery(BotOffiaccount.class)
                        .eq(BotOffiaccount::getUid, uid)
                        .eq(BotOffiaccount::getStatus, BotOffiaccountStatusEnum.BOUND.getStatus())
                        .orderByDesc(BotOffiaccount::getUpdateTime));
    }

}
