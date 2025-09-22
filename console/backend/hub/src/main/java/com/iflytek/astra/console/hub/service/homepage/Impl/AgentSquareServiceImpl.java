package com.iflytek.astra.console.hub.service.homepage.Impl;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.iflytek.astra.console.commons.data.UserInfoDataService;
import com.iflytek.astra.console.commons.entity.bot.ChatBotMarket;
import com.iflytek.astra.console.commons.entity.chat.ChatList;
import com.iflytek.astra.console.commons.service.bot.BotFavoriteService;
import com.iflytek.astra.console.commons.service.bot.BotTypeListService;
import com.iflytek.astra.console.commons.service.bot.ChatBotMarketService;
import com.iflytek.astra.console.commons.service.data.ChatListDataService;
import com.iflytek.astra.console.commons.util.RequestContextUtil;
import com.iflytek.astra.console.hub.dto.homepage.BotInfoDto;
import com.iflytek.astra.console.hub.dto.homepage.BotListPageDto;
import com.iflytek.astra.console.hub.dto.homepage.BotTypeDto;
import com.iflytek.astra.console.hub.service.homepage.AgentSquareService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * @author yun-zhi-ztl
 */
@Service
@Slf4j
public class AgentSquareServiceImpl implements AgentSquareService {

    @Autowired
    private BotTypeListService botTypeListService;

    @Autowired
    private ChatBotMarketService chatBotMarketService;

    @Autowired
    private UserInfoDataService userInfoDataService;

    @Autowired
    private BotFavoriteService botFavoriteService;

    @Autowired
    private ChatListDataService chatListDataService;

    @Override
    public List<BotTypeDto> getBotTypeList() {
        return botTypeListService.getBotTypeList()
                        .stream()
                        .map(item -> new BotTypeDto(
                                        item.getTypeKey(),
                                        item.getTypeName(),
                                        item.getIcon(),
                                        item.getTypeNameEn()))
                        .toList();
    }

    @Override
    public BotListPageDto getBotPageByType(Integer type, String search, Integer pageSize, Integer page) {
        // Get paginated assistant list
        Page<ChatBotMarket> marketPage = chatBotMarketService.getBotPage(type, search, pageSize, page);
        // Get current user's UID
        String uid = RequestContextUtil.getUID();
        // Get user's favorite assistant ID set
        Set<Integer> favoriteIds = new HashSet<>(botFavoriteService.list(uid));

        // Use Stream to process each assistant, convert to DTO
        List<BotInfoDto> botInfoList = marketPage.getRecords()
                        .stream()
                        .map(market -> {
                            String creatorName = userInfoDataService.findNickNameByUid(market.getUid()).orElse(null);
                            ChatList latestChat = chatListDataService.findLatestEnabledChatByUserAndBot(uid, market.getBotId());
                            Long chatId = latestChat != null ? latestChat.getId() : null;
                            return new BotInfoDto(
                                            market.getBotId(),
                                            chatId,
                                            market.getBotName(),
                                            type,
                                            market.getAvatar(),
                                            market.getPrompt(),
                                            market.getBotDesc(),
                                            favoriteIds.contains(market.getBotId()),
                                            creatorName);
                        })
                        .collect(Collectors.toList());

        return new BotListPageDto(
                        botInfoList,
                        Math.toIntExact(marketPage.getTotal()),
                        Math.toIntExact(marketPage.getSize()),
                        Math.toIntExact(marketPage.getCurrent()),
                        Math.toIntExact(marketPage.getPages()));
    }

}
