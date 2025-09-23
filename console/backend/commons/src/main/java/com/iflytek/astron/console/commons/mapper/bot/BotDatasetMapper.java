package com.iflytek.astron.console.commons.mapper.bot;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.iflytek.astron.console.commons.entity.bot.BotDataset;
import com.iflytek.astron.console.commons.entity.bot.DatasetInfo;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface BotDatasetMapper extends BaseMapper<BotDataset> {
    List<DatasetInfo> selectDatasetListByBotId(@Param("botId") Integer botId);
}
