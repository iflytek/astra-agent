package com.iflytek.astra.console.commons.service.bot.impl;

import cn.hutool.core.convert.Convert;
import com.alibaba.fastjson2.JSONObject;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.toolkit.CollectionUtils;
import com.iflytek.astra.console.commons.entity.bot.BotMarketForm;
import com.iflytek.astra.console.commons.entity.bot.ChatBotMarket;
import com.iflytek.astra.console.commons.enums.bot.BotStatusEnum;
import com.iflytek.astra.console.commons.enums.bot.ReleaseTypeEnum;
import com.iflytek.astra.console.commons.mapper.bot.ChatBotListMapper;
import com.iflytek.astra.console.commons.mapper.bot.ChatBotMarketMapper;
import com.iflytek.astra.console.commons.service.bot.BotFavoriteService;
import com.iflytek.astra.console.commons.service.bot.BotMarketDataService;
import com.iflytek.astra.console.commons.service.bot.BotService;
import com.iflytek.astra.console.commons.service.data.UserLangChainDataService;
import com.iflytek.astra.console.commons.util.BotUtil;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@Slf4j
public class BotMarketDataServiceImpl implements BotMarketDataService {

    @Autowired
    private ChatBotMarketMapper chatBotMarketMapper;

    @Autowired
    private ChatBotListMapper chatBotListMapper;

    @Autowired
    private BotFavoriteService botFavoriteService;

    @Autowired
    private BotService botService;
    @Autowired
    private UserLangChainDataService userLangChainDataService;

    @Override
    public void removeBotForDeleteSpace(String uid, Long spaceId, List<Integer> spaceBotIdList) {
        if (spaceId == null) {
            log.error("removeBotForDeleteSpace-failed, spaceId is null, uid={}", uid);
            return;
        }
        if (spaceBotIdList.isEmpty()) {
            // If empty, it means no maintenance is needed
            return;
        }
        // Take down assistants
        LambdaUpdateWrapper<ChatBotMarket> updateWrapper = new LambdaUpdateWrapper<>();
        updateWrapper.in(ChatBotMarket::getBotId, spaceBotIdList)
                .set(ChatBotMarket::getBotStatus, 0)
                .set(ChatBotMarket::getIsDelete, 1);

        chatBotMarketMapper.update(null, updateWrapper);
    }

    /**
     * Query whether assistant is on market shelf
     *
     * @param bots
     * @return
     */
    @Override
    public boolean botsOnMarket(List<Long> bots) {
        // Query all status by botId at once
        List<ChatBotMarket> chatBotMarkets = chatBotMarketMapper.selectByBotIds(bots);
        if (chatBotMarkets.isEmpty()) {
            return false;
        }
        for (ChatBotMarket chatBotMarket : chatBotMarkets) {
            if (!(chatBotMarket.getBotStatus().equals(BotStatusEnum.PUBLISHED.getCode()))) {
                return false;
            }
        }
        return true;
    }

    /**
     * Get my added dropdown pagination records
     *
     * @param botMarketForm
     * @param uid
     * @param spaceId
     * @return
     */
    @Override
    public Map<String, Object> getBotListCheckNextPage(HttpServletRequest request, BotMarketForm botMarketForm, String uid, Long spaceId) {
        String langCode = request.getHeader("Lang-Code") == null ? "" : request.getHeader("Lang-Code");
        Map<String, Object> param = getBotCheckParam(botMarketForm, uid);
        param.put("spaceId", spaceId);
        if (botMarketForm.getVersion() != null) {
            param.put("version", botMarketForm.getVersion());
        }
        if (StringUtils.isNotBlank(botMarketForm.getSearchValue())) {
            param.put("botName", botMarketForm.getSearchValue());
        }
        if (botMarketForm.getSort() != null) {
            if (("createTime").equals(botMarketForm.getSort())) {
                param.put("sort", "a.create_time desc");
            }
            if (("updateTime").equals(botMarketForm.getSort())) {
                param.put("sort", "a.update_time desc");
            }
        }
        if (CollectionUtils.isNotEmpty(botMarketForm.getBotStatus())) {
            List<Integer> botStatus = botMarketForm.getBotStatus();
            param.put("status", botStatus);
            if (botStatus.contains(0)) {
                param.put("flag", 1);
            }
        }
        Long count = chatBotListMapper.countCheckBotList(param);
        // Execute pagination query
        int pageNum = botMarketForm.getPageIndex();
        int pageSize = Math.min(botMarketForm.getPageSize(), 200);
        int offset = (pageNum - 1) * pageSize;
        param.put("offset", offset);
        param.put("pageSize", pageSize);

        List<Integer> favoriteBotIdList = botFavoriteService.list(uid);

        LinkedList<Map<String, Object>> list = chatBotListMapper.getCheckBotList(param);
        Set<Integer> botIdSet = new HashSet<>();
        for (Map<String, Object> map : list) {
            List<Integer> botRelease = new ArrayList<>();
            if (map.get("botStatus").equals(1L) || map.get("botStatus").equals(4L) || map.get("botStatus").equals(2L)) {
                botRelease.add(ReleaseTypeEnum.MARKET.getCode());
            }
            Long botId = Convert.toLong(map.get("botId"));

            int hotNum = Convert.toInt(map.get("hotNum") == null ? 0 : map.get("hotNum"), 0);
            String numStr = BotUtil.convertNumToStr(hotNum, langCode);
            map.put("hotNum", numStr);

            map.put("isFavorite", 0);
            if (favoriteBotIdList.contains(botId.intValue())) {
                map.put("isFavorite", 1);
            }

            map.put("releaseType", botRelease);
            botIdSet.add((Integer) map.get("botId"));
        }
        if (CollectionUtils.isNotEmpty(botIdSet)) {
            List<JSONObject> chainList = userLangChainDataService.findByBotIdSet(botIdSet);
            // <botId, chain>map
            Map<Integer, JSONObject> chainMap = chainList.stream()
                    .collect(Collectors.toMap(
                            json -> json.getInteger("botId"), Function.identity(), (existing, newValue) -> newValue));
            Map<Integer, Boolean> multiInputMap = chainList.stream()
                    .collect(Collectors.toMap(
                            json -> json.getInteger("botId"),
                            json -> {
                                // Process extraInputs
                                if (json.containsKey("extraInputs") && json.get("extraInputs") != null) {
                                    JSONObject extraInputs = JSONObject.parseObject(json.getString("extraInputs"));
                                    int size = extraInputs.size();
                                    if (extraInputs.containsValue("image")) {
                                        size -= 2; // image needs to subtract two
                                    }
                                    return size > 0;
                                } else {
                                    return false;
                                }
                            }));
            list.stream()
                    .filter(map -> chainMap.containsKey((Integer) map.get("botId")))
                    .forEach(map -> map.put("maasId", chainMap.get(map.get("botId")).get("maasId")));
            list.forEach(map -> map.put("multiInput", multiInputMap.get(map.get("botId"))));
        }

        Map<String, Object> resultMap = new HashMap<>();
        resultMap.put("total", count);
        resultMap.put("pageList", list);
        return resultMap;
    }

    private static Map<String, Object> getBotCheckParam(BotMarketForm botMarketForm, String uid) {
        Map<String, Object> param = new HashMap<>();
        param.put("uid", uid);
        List<Integer> botStatuses = botMarketForm.getBotStatus();
        if (!Objects.isNull(botStatuses) && botStatuses.contains(1)) {
            botStatuses.add(4);
        }
        param.put("botType", botMarketForm.getBotType());
        param.put("botStatus", botStatuses);
        if (Objects.nonNull(botStatuses) && botStatuses.size() == 1 && botStatuses.getFirst() == -9) {
            param.put("flag", 1);
        }
        return param;
    }

}
