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

    // Bot category
    private Integer botType;

    /**
     * Support multiple type queries
     */
    private String botTypeMulti;

    // Audit status, empty means all
    private List<Integer> botStatus;

    // Version, 1 is agent, 3 is workflow
    private Integer version;

    private int status;

    private int pageIndex = 1;

    private int pageSize = 15;

    // Default is domestic, 1 is domestic, 2 is overseas
    private Integer showType;

    // Official assistants only
    private int official;

    private List<Integer> excludeBot = new ArrayList<>();

    /**
     * Sort field
     */
    private String sort;

    /**
     * Get botTypes based on botType (lowest cost change)
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
