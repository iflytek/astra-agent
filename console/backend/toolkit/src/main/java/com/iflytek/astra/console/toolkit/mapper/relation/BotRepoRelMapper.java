package com.iflytek.astra.console.toolkit.mapper.relation;


import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.iflytek.astra.console.toolkit.entity.table.relation.BotRepoRel;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;


@Mapper

public interface BotRepoRelMapper extends BaseMapper<BotRepoRel> {
    int deleteByAppIdAndBotIdAndRepoIds(@Param("appId") String appId, @Param("botId") Long botId, @Param("repoIds") List<String> repoIds);

    List<BotRepoRel> getModelListByAppIdAndRepoIdAndBotId(@Param("appId") String appId, @Param("repoId") String repoId, @Param("botId") String botId);
}
