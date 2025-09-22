package com.iflytek.astra.console.commons.mapper.bot;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.iflytek.astra.console.commons.entity.bot.ChatBotMarket;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * @author yun-zhi-ztl
 */
@Mapper
public interface ChatBotMarketMapper extends BaseMapper<ChatBotMarket> {

    List<ChatBotMarket> selectByBotIds(@Param("botIds") List<Long> botIds);
}
