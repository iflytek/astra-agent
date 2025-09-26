package com.iflytek.astron.console.hub.dto.publish;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

/**
 * 智能体时间序列statistics数据VO
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class BotTimeSeriesStatsVO {

    /**
     * statistics日期
     */
    private LocalDate date;

    /**
     * 当日对话数
     */
    private Integer chatCount;

    /**
     * 当日user数
     */
    private Integer userCount;

    /**
     * 当日Token消耗
     */
    private Integer tokenCount;

    /**
     * 当日消息轮数
     */
    private Integer messageCount;
}
