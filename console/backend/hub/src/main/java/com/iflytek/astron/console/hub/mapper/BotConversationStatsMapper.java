package com.iflytek.astron.console.hub.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.iflytek.astron.console.hub.dto.publish.BotSummaryStatsVO;
import com.iflytek.astron.console.hub.dto.publish.BotTimeSeriesStatsVO;
import com.iflytek.astron.console.hub.entity.BotConversationStats;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDate;
import java.util.List;

/**
 * 智能体对话统计Mapper
 */
@Mapper
public interface BotConversationStatsMapper extends BaseMapper<BotConversationStats> {


    /**
     * 查询智能体总体统计数据
     *
     * @param botId 智能体ID
     * @param uid 用户ID（可为空，表示查询所有用户）
     * @param spaceId 空间ID（可为空）
     * @return 总体统计数据
     */
    BotSummaryStatsVO selectSummaryStats(@Param("botId") Integer botId,
            @Param("uid") Long uid,
            @Param("spaceId") Long spaceId);

    /**
     * 查询智能体时间序列统计数据
     *
     * @param botId 智能体ID
     * @param startDate 开始日期
     * @param uid 用户ID（可为空，表示查询所有用户）
     * @param spaceId 空间ID（可为空）
     * @return 时间序列统计数据
     */
    List<BotTimeSeriesStatsVO> selectTimeSeriesStats(@Param("botId") Integer botId,
            @Param("startDate") LocalDate startDate,
            @Param("uid") Long uid,
            @Param("spaceId") Long spaceId);

}
