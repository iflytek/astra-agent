package com.iflytek.astra.console.commons.service.bot.impl;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.convert.Convert;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.iflytek.astra.console.commons.constant.ResponseEnum;
import com.iflytek.astra.console.commons.data.UserInfoDataService;
import com.iflytek.astra.console.commons.dto.bot.BotFavoriteItemDto;
import com.iflytek.astra.console.commons.dto.bot.BotFavoritePageDto;
import com.iflytek.astra.console.commons.dto.bot.BotFavoriteQueryDto;
import com.iflytek.astra.console.commons.entity.bot.*;
import com.iflytek.astra.console.commons.entity.user.ChatUser;
import com.iflytek.astra.console.commons.exception.BusinessException;
import com.iflytek.astra.console.commons.mapper.bot.BotFavoriteMapper;
import com.iflytek.astra.console.commons.mapper.bot.ChatBotBaseMapper;
import com.iflytek.astra.console.commons.mapper.bot.ChatBotMarketMapper;
import com.iflytek.astra.console.commons.mapper.user.ChatUserMapper;
import com.iflytek.astra.console.commons.service.bot.BotFavoriteService;
import com.iflytek.astra.console.commons.util.BotUtil;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * @author cherry
 */
@Service
@Slf4j
public class BotFavoriteServiceImpl implements BotFavoriteService {

    @Autowired
    private BotFavoriteMapper botFavoriteMapper;

    @Autowired
    private ChatUserMapper chatUserMapper;

    @Autowired
    private ChatBotBaseMapper chatBotBaseMapper;

    @Autowired
    private ChatBotMarketMapper chatBotMarketMapper;
    @Autowired
    private UserInfoDataService userInfoDataService;

    @Override
    public BotFavoritePageDto selectPage(BotMarketForm botMarketForm, String uid, String langCode) {
        BotFavoriteQueryDto queryDto = createQueryDto(botMarketForm, uid);
        Long count = botFavoriteMapper.countBotPage(queryDto);

        if (count.intValue() == 0) {
            log.info("------Assistant not found, bot_type: {}, searchValue: {}", botMarketForm.getBotType(), botMarketForm.getSearchValue());
            return new BotFavoritePageDto(count, new ArrayList<>());
        }

        LinkedList<ChatBotMarketPage> botList = queryBotPages(queryDto, botMarketForm);
        Map<String, ChatUser> userMap = buildUserMap(botList, botMarketForm);
        List<BotFavoriteItemDto> resultList = buildResultList(botList, userMap, uid, langCode);

        return new BotFavoritePageDto(count, resultList);
    }

    private BotFavoriteQueryDto createQueryDto(BotMarketForm botMarketForm, String uid) {
        BotFavoriteQueryDto queryDto = new BotFavoriteQueryDto(uid, null, null);
        List<Integer> botStatuses = botMarketForm.getBotStatus();
        if (!Objects.isNull(botStatuses) && botStatuses.contains(1)) {
            botStatuses.add(4);
        }
        return queryDto;
    }

    private LinkedList<ChatBotMarketPage> queryBotPages(BotFavoriteQueryDto queryDto, BotMarketForm botMarketForm) {
        int pageNum = botMarketForm.getPageIndex();
        int pageSize = Math.min(botMarketForm.getPageSize(), 50);
        int offset = (pageNum - 1) * pageSize;
        queryDto.setOffset(offset);
        queryDto.setPageSize(pageSize);
        LinkedList<ChatBotMarketPage> result = botFavoriteMapper.selectBotPage(queryDto);
        for (ChatBotMarketPage chatBotMarketPage : result) {
            userInfoDataService.findByUid(chatBotMarketPage.getUid())
                    .ifPresent(userInfo -> chatBotMarketPage.setCreatorName(userInfo.getNickname()));
        }
        return result;
    }

    private Map<String, ChatUser> buildUserMap(LinkedList<ChatBotMarketPage> botList, BotMarketForm botMarketForm) {
        Set<String> uidSet = extractUidSet(botList);
        if (uidSet.isEmpty()) {
            log.info("------Creator not found, bot_type: {}, searchValue: {}", botMarketForm.getBotType(), botMarketForm.getSearchValue());
            uidSet.add("1");
        }

        LambdaQueryWrapper<ChatUser> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.in(ChatUser::getUid, uidSet);
        List<ChatUser> userList = chatUserMapper.selectList(queryWrapper);
        return userList.stream().collect(Collectors.toMap(ChatUser::getUid, user -> user));
    }

    private Set<String> extractUidSet(LinkedList<ChatBotMarketPage> botList) {
        Set<String> uidSet = new HashSet<>();
        for (ChatBotMarketPage bot : botList) {
            uidSet.add(bot.getUid());
        }
        return uidSet;
    }

    private List<BotFavoriteItemDto> buildResultList(LinkedList<ChatBotMarketPage> botList,
                    Map<String, ChatUser> userMap,
                    String uid,
                    String langCode) {
        List<BotFavoriteItemDto> resultList = new ArrayList<>();
        try {
            for (ChatBotMarketPage market : botList) {
                BotFavoriteItemDto item = buildBotFavoriteItem(market, userMap, uid, langCode);
                resultList.add(item);
            }
        } catch (Exception e) {
            log.error("[Assistant Favorite] Assembly failed", e);
        }
        return resultList;
    }

    private BotFavoriteItemDto buildBotFavoriteItem(ChatBotMarketPage market,
                    Map<String, ChatUser> userMap,
                    String uid,
                    String langCode) {
        // Handle popularity value display
        processHotNum(market, langCode);

        BotFavoriteItemDto item = new BotFavoriteItemDto();
        item.setAddStatus(0); // Default not added

        // Set creator information
        setCreatorInfo(item, market, userMap);

        // Set add status
        setAddStatus(item, market);

        // Handle bot information
        processBotInfo(market, uid, langCode);

        item.setBot(market);
        return item;
    }

    private void processHotNum(ChatBotMarketPage market, String langCode) {
        int hotNum = Convert.toInt(market.getHotNum(), 0);
        String numStr = BotUtil.convertNumToStr(hotNum, langCode);
        market.setHotNum(numStr);
    }

    private void setCreatorInfo(BotFavoriteItemDto item, ChatBotMarketPage market, Map<String, ChatUser> userMap) {
        String creatorUid = market.getUid();
        if (Objects.equals(creatorUid, "1")) {
            item.setCreator("");
            return;
        }

        ChatUser creator = userMap.get(creatorUid);
        if (creator == null) {
            item.setCreator("");
            return;
        }

        String creatorName = getCreatorDisplayName(creator);
        item.setCreator(creatorName);
    }

    private String getCreatorDisplayName(ChatUser creator) {
        if (StringUtils.isNotBlank(creator.getNickname())) {
            return creator.getNickname();
        }

        String mobile = creator.getMobile();
        if (StringUtils.isNotBlank(mobile) && mobile.length() > 8) {
            return mobile.substring(0, 3) + "****" + mobile.substring(7);
        }

        return StringUtils.isNotBlank(mobile) ? mobile : "";
    }

    private void setAddStatus(BotFavoriteItemDto item, ChatBotMarketPage market) {
        if (market.getChatId() != null) {
            item.setAddStatus(1);
            item.setChatId(market.getChatId());
            item.setEnableStatus(market.getEnable());
        }
    }

    private void processBotInfo(ChatBotMarketPage market, String uid, String langCode) {
        if (uid.equals(market.getUid())) {
            market.setMine(true);
        }
        market.setIsFavorite(1);
        market.setUid(null); // Hide sensitive data

        if ("en".equals(langCode) && market.getBotNameEn() != null) {
            market.setBotName(market.getBotNameEn());
        }
    }

    @Override
    public void create(String uid, Integer botId) {
        QueryWrapper<ChatBotMarket> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq("bot_id", botId);
        ChatBotMarket chatBotMarket = chatBotMarketMapper.selectOne(queryWrapper);
        // Bot not on shelf, and current uid is not equal to author uid, no permission to access
        if (chatBotMarket != null && (chatBotMarket.getBotStatus() != 1 && chatBotMarket.getBotStatus() != 2 && chatBotMarket.getBotStatus() != 4 && !Objects.equals(chatBotMarket.getUid(), uid))) {
            throw new BusinessException(ResponseEnum.BOT_BELONG_ERROR);
        }
        if (chatBotMarket == null) {
            ChatBotBase botBase = chatBotBaseMapper.selectOne(Wrappers.lambdaQuery(ChatBotBase.class).eq(ChatBotBase::getId, botId).eq(ChatBotBase::getUid, uid));
            if (botBase == null) {
                throw new BusinessException(ResponseEnum.BOT_BELONG_ERROR);
            }
        }

        BotFavorite botFavorite = botFavoriteMapper.selectOne(Wrappers.lambdaQuery(BotFavorite.class).eq(BotFavorite::getUid, uid).eq(BotFavorite::getBotId, botId));
        if (botFavorite != null) {
            log.error("[Assistant Favorite] User {} has already favorited assistant {}", uid, botId);
            return;
        }

        BotFavorite entity = BotFavorite.builder().uid(uid).botId(botId).createTime(LocalDateTime.now()).updateTime(LocalDateTime.now()).build();
        botFavoriteMapper.insert(entity);
    }

    @Override
    public void delete(String uid, Integer botId) {
        BotFavorite botFavorite = botFavoriteMapper.selectOne(Wrappers.lambdaQuery(BotFavorite.class).eq(BotFavorite::getUid, uid).eq(BotFavorite::getBotId, botId));
        if (botFavorite == null) {
            log.error("[Assistant Favorite] User {} has not favorited assistant {}", uid, botId);
            return;
        }

        botFavoriteMapper.deleteById(botFavorite.getId());
    }

    @Override
    public int getFavoriteNumByBotId(Integer botId) {
        return botFavoriteMapper.selectCount(Wrappers.lambdaQuery(BotFavorite.class)
                        .eq(BotFavorite::getBotId, botId)).intValue();
    }

    @Override
    public List<Integer> list(String uid) {
        List<BotFavorite> list = botFavoriteMapper.selectList(
                        Wrappers.lambdaQuery(BotFavorite.class)
                                        .select(BotFavorite::getBotId)
                                        .eq(BotFavorite::getUid, uid));
        if (CollUtil.isEmpty(list)) {
            return new ArrayList<>();
        }
        return list.stream().map(BotFavorite::getBotId).collect(Collectors.toList());
    }
}
