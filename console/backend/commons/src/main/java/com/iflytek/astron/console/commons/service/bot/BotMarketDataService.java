package com.iflytek.astron.console.commons.service.bot;

import com.iflytek.astron.console.commons.entity.bot.BotMarketForm;
import jakarta.servlet.http.HttpServletRequest;

import java.util.List;
import java.util.Map;

public interface BotMarketDataService {

    /**
     * When space deletes assistant, unbind the relationship between assistant and space
     *
     * @param uid
     * @param spaceId
     * @param spaceBotIdList
     */
    void removeBotForDeleteSpace(String uid, Long spaceId, List<Integer> spaceBotIdList);

    boolean botsOnMarket(List<Long> bots);

    Map<String, Object> getBotListCheckNextPage(HttpServletRequest request, BotMarketForm botMarketForm, String uid, Long spaceId);
}
