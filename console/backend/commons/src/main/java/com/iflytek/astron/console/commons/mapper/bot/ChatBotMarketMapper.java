package com.iflytek.astron.console.commons.mapper.bot;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.iflytek.astron.console.commons.dto.bot.BotQueryCondition;
import com.iflytek.astron.console.commons.entity.bot.BotPublishQueryResult;
import com.iflytek.astron.console.commons.entity.bot.ChatBotMarket;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * @author yun-zhi-ztl
 */
@Mapper
public interface ChatBotMarketMapper extends BaseMapper<ChatBotMarket> {

    List<ChatBotMarket> selectByBotIds(@Param("botIds") List<Long> botIds);

    /**
     * 分页查询智能体列表 使用多表关联查询，确保数据一致性和完整性 遵循技术标准：使用实体类接收查询结果
     *
     * @param page 分页参数
     * @param condition 查询条件
     * @return 智能体列表
     */
    Page<BotPublishQueryResult> selectBotListByConditions(
            Page<BotPublishQueryResult> page,
            @Param("condition") BotQueryCondition condition);

    /**
     * 查询智能体详情 关联 chat_bot_base 和 chat_bot_market 表获取完整信息
     *
     * @param botId 智能体ID
     * @param uid 用户ID（用于权限验证）
     * @param spaceId 空间ID（可选，用于空间权限验证）
     * @return 智能体详情
     */
    BotPublishQueryResult selectBotDetail(
            @Param("botId") Integer botId,
            @Param("uid") String uid,
            @Param("spaceId") Long spaceId);

    /**
     * 更新智能体发布状态和发布渠道
     *
     * @param botId 智能体ID
     * @param uid 用户ID（用于权限验证）
     * @param spaceId 空间ID（可选，用于空间权限验证）
     * @param botStatus 新的发布状态
     * @param publishChannels 新的发布渠道
     * @return 影响的行数
     */
    int updatePublishStatus(
            @Param("botId") Integer botId,
            @Param("uid") String uid,
            @Param("spaceId") Long spaceId,
            @Param("botStatus") Integer botStatus,
            @Param("publishChannels") String publishChannels);
}
