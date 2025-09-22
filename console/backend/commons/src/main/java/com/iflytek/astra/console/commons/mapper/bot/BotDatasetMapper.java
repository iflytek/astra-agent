package com.iflytek.astra.console.commons.mapper.bot;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.iflytek.astra.console.commons.entity.bot.BotDataset;
import com.iflytek.astra.console.commons.entity.bot.DatasetInfo;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface BotDatasetMapper extends BaseMapper<BotDataset> {

    @Select("select d.id, d.uid, d.name, d.description " +
                    "from bot_dataset b " +
                    "left join dataset_info d on b.dataset_id = d.id " +
                    "where b.bot_id = #{botId} and b.is_act = 1 and d.status = 2")
    List<DatasetInfo> selectDatasetListByBotId(@Param("botId") Integer botId);
}
