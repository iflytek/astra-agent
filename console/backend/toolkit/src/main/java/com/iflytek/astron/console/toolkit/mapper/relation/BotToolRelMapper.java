package com.iflytek.astron.console.toolkit.mapper.relation;


import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.iflytek.astron.console.toolkit.entity.table.relation.BotToolRel;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;


@Mapper
public interface BotToolRelMapper extends BaseMapper<BotToolRel> {

    int deleteByBotIdAndToolIds(@Param("botId") Long botId, @Param("toolIds") List<String> toolIds);
}
