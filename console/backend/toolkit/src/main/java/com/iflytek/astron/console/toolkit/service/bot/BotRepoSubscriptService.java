package com.iflytek.astron.console.toolkit.service.bot;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.iflytek.astron.console.toolkit.entity.table.bot.BotRepoSubscript;
import com.iflytek.astron.console.toolkit.mapper.bot.BotRepoSubscriptMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Service
@Slf4j
public class BotRepoSubscriptService extends ServiceImpl<BotRepoSubscriptMapper, BotRepoSubscript> {
    public BotRepoSubscript getOnly(QueryWrapper<BotRepoSubscript> wrapper) {
        wrapper.last("limit 1");
        return this.getOne(wrapper);
    }

    public BotRepoSubscript getOnly(LambdaQueryWrapper<BotRepoSubscript> wrapper) {
        wrapper.last("limit 1");
        return this.getOne(wrapper);
    }
}
