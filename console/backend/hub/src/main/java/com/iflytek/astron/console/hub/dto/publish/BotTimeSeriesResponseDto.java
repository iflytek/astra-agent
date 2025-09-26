package com.iflytek.astron.console.hub.dto.publish;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.List;

/**
 * 智能体时序统计responseDTO 字段顺序与前端页面时序图表显示顺序保持一致：累计会话数、活跃用户数、平均会话互动数、Token消耗量
 */
@Data
@Schema(description = "智能体时序统计数据")
public class BotTimeSeriesResponseDto {

    @Schema(description = "累计会话数")
    private List<TimeSeriesItem> chatMessages;

    @Schema(description = "活跃用户数")
    private List<TimeSeriesItem> activityUser;

    @Schema(description = "平均会话互动数")
    private List<TimeSeriesItem> avgChatMessages;

    @Schema(description = "Token消耗量")
    private List<TimeSeriesItem> tokenUsed;

    @Data
    @Schema(description = "时间序列数据项")
    public static class TimeSeriesItem {
        @Schema(description = "日期", example = "2024-01-15")
        private String date;

        @Schema(description = "统计数量", example = "10")
        private Integer count;

        public TimeSeriesItem(String date, Integer count) {
            this.date = date;
            this.count = count;
        }

        public TimeSeriesItem() {}
    }
}
