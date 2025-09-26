package com.iflytek.astron.console.commons.mapper.bot;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.iflytek.astron.console.commons.entity.bot.BotDetail;
import com.iflytek.astron.console.commons.entity.bot.ChatBotBase;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface ChatBotBaseMapper extends BaseMapper<ChatBotBase> {
    BotDetail botDetail(Integer botId);

    List<ChatBotBase> selectByBotIds(@Param("botIds") List<Long> botIds);

    /**
     * 验证用户是否有权限访问该智能体
     *
     * @param botId 智能体ID
     * @param uid 用户ID
     * @param spaceId 空间ID（可选）
     * @return 权限数量（>0表示有权限，0表示无权限）
     */
    int checkBotPermission(@Param("botId") Integer botId,
            @Param("uid") String uid,
            @Param("spaceId") Long spaceId);

    /**
     * 验证用户是否有权限访问该智能体（Long类型botId的重载方法）
     *
     * @param botId 智能体ID
     * @param uid 用户ID
     * @param spaceId 空间ID（可选）
     * @return 权限数量（>0表示有权限，0表示无权限）
     */
    default int checkBotPermission(Long botId, String uid, Long spaceId) {
        return checkBotPermission(botId.intValue(), uid, spaceId);
    }
}
