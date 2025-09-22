package com.iflytek.astra.console.commons.entity.bot;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class BotMarketForm {

    private String searchValue;

    private Integer botId;

    private Long marketBotId;

    private Long uid;

    // bot分类
    private Integer botType;

    /**
     * 支持多个类型的查询
     */
    private String botTypeMulti;

    // 审核状态，传空就是全部
    private List<Integer> botStatus;

    // 版本,1 是智能体,3 是工作流
    private Integer version;

    private int status;

    private int pageIndex = 1;

    private int pageSize = 15;

    // 默认是国内，1是国内，2是海外
    private Integer showType;

    // 只有官方助手
    private int official;

    private List<Integer> excludeBot = new ArrayList<>();

    /**
     * 排序字段
     */
    private String sort;

    /**
     * 根据botType获取botTypes (最低成本的改动)
     */
    public String getBotTypeMulti() {
        if (botType == null) {
            return null;
        }
        if (botType == 10) {
            return "10,11,37,16,18";
        }
        if (botType == 13) {
            return "13,12,23,21";
        }
        if (botType == 15) {
            return "15,19,22,20,39";
        }
        return botType.toString();
    }
}
