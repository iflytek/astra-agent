package com.iflytek.astron.console.toolkit.service.bot;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.iflytek.astron.console.toolkit.entity.table.relation.BotToolRel;
import com.iflytek.astron.console.toolkit.mapper.relation.BotToolRelMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;

import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.List;

@Service
@Slf4j
public class BotToolRelService extends ServiceImpl<BotToolRelMapper, BotToolRel> {
    public BotToolRel getOnly(QueryWrapper<BotToolRel> wrapper) {
        wrapper.last("limit 1");
        return this.getOne(wrapper);
    }

    public void updateBotTools(Long botId, List<String> toolArray) {
        List<BotToolRel> botToolRelList = this.list(Wrappers.lambdaQuery(BotToolRel.class).eq(BotToolRel::getBotId, botId));
        if (CollectionUtils.isEmpty(botToolRelList) && CollectionUtils.isEmpty(toolArray)) {
            return;
        }
        List<String> newList = new ArrayList<>();
        if (!CollectionUtils.isEmpty(toolArray)) {
            newList.addAll(toolArray);
        }
        List<String> oldList = new ArrayList<>();
        if (!CollectionUtils.isEmpty(botToolRelList)) {
            for (BotToolRel botToolRel : botToolRelList) {
                oldList.add(botToolRel.getToolId());
            }
        }

        List<String> addList = new ArrayList<>();
        for (String s : newList) {
            if (!oldList.contains(s)) {
                addList.add(s);
            }
        }
        List<String> delList = new ArrayList<>();
        for (String s : oldList) {
            if (!newList.contains(s)) {
                delList.add(s);
            }
        }

        // delete old
        deleteByBotIdAndToolIds(botId, delList);
        // add new
        addByBotIdAndToolIds(botId, addList);

    }


    public void deleteByBotIdAndToolIds(Long botId, List<String> toolIds) {
        if (!CollectionUtils.isEmpty(toolIds)) {
            this.getBaseMapper().deleteByBotIdAndToolIds(botId, toolIds);
        }
    }


    public void addByBotIdAndToolIds(Long botId, List<String> toolIds) {
        List<BotToolRel> botToolRelList = new ArrayList<>();
        Timestamp timestamp = new Timestamp(System.currentTimeMillis());
        if (!CollectionUtils.isEmpty(toolIds)) {
            for (String toolId : toolIds) {
                BotToolRel botToolRel = new BotToolRel();
                botToolRelList.add(botToolRel);
                botToolRel.setBotId(botId);
                botToolRel.setToolId(toolId);
                botToolRel.setCreateTime(timestamp);
            }
            this.saveBatch(botToolRelList);
        }
    }
}
