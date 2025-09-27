package com.iflytek.astron.console.commons.service.data.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.iflytek.astron.console.commons.mapper.bot.BotTypeListMapper;
import com.iflytek.astron.console.commons.service.bot.BotTypeListService;
import com.iflytek.astron.console.commons.entity.bot.BotTypeList;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * @author yun-zhi-ztl
 */
@Service
public class BotTypeListServiceImpl implements BotTypeListService {

    @Autowired
    private BotTypeListMapper botTypeListMapper;

    @Override
    public List<BotTypeList> getBotTypeList() {
        // Conditions: recommended and enabled, sorted by weight
        return botTypeListMapper.selectList(Wrappers.<BotTypeList>lambdaQuery()
                .eq(BotTypeList::getShowIndex, 1)
                .eq(BotTypeList::getIsAct, 1)
                .orderByAsc(BotTypeList::getOrderNum));
    }
}
