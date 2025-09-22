package com.iflytek.astra.console.hub.service.user.impl;

import cn.hutool.core.convert.Convert;
import com.alibaba.fastjson2.JSONObject;
import com.baomidou.mybatisplus.core.toolkit.CollectionUtils;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.iflytek.astra.console.commons.dto.bot.ChatBotApi;
import com.iflytek.astra.console.commons.entity.model.McpData;
import com.iflytek.astra.console.commons.enums.bot.ReleaseTypeEnum;
import com.iflytek.astra.console.commons.mapper.bot.ChatBotListMapper;
import com.iflytek.astra.console.commons.service.bot.BotFavoriteService;
import com.iflytek.astra.console.commons.service.bot.BotService;
import com.iflytek.astra.console.commons.service.data.UserLangChainDataService;
import com.iflytek.astra.console.commons.service.mcp.McpDataService;
import com.iflytek.astra.console.commons.util.BotUtil;
import com.iflytek.astra.console.commons.util.RequestContextUtil;
import com.iflytek.astra.console.commons.util.space.SpaceInfoUtil;
import com.iflytek.astra.console.hub.dto.user.MyBotPageDTO;
import com.iflytek.astra.console.hub.dto.user.MyBotParamDTO;
import com.iflytek.astra.console.hub.dto.user.MyBotResponseDTO;
import com.iflytek.astra.console.hub.entity.ApplicationForm;
import com.iflytek.astra.console.hub.entity.BotOffiaccount;
import com.iflytek.astra.console.hub.mapper.ApplicationFormMapper;
import com.iflytek.astra.console.hub.service.bot.BotOffiaccountService;
import com.iflytek.astra.console.hub.service.chat.ChatBotApiService;
import com.iflytek.astra.console.hub.service.user.UserBotService;
import com.iflytek.astra.console.hub.util.BotPermissionUtil;
import jakarta.servlet.http.HttpServletRequest;
import org.apache.commons.lang3.StringUtils;
import org.redisson.api.RBucket;
import org.redisson.api.RedissonClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * @author wowo_zZ
 * @since 2025/9/9 19:26
 **/
@Service
public class UserBotServiceImpl implements UserBotService {

    @Autowired
    private ChatBotListMapper chatBotListMapper;

    @Autowired
    private BotOffiaccountService botOffiaccountService;

    @Autowired
    private UserLangChainDataService userLangChainDataService;

    @Autowired
    private BotFavoriteService botFavoriteService;

    @Autowired
    private ChatBotApiService chatBotApiService;

    @Autowired
    private McpDataService mcpDataService;

    @Autowired
    private RedissonClient redissonClient;

    @Autowired
    private BotPermissionUtil botPermissionUtil;

    @Autowired
    private ApplicationFormMapper applicationFormMapper;

    @Autowired
    private BotService botService;

    public static final String RECORD_BOT_ID = "recordFormBotId_";

    @Override
    public MyBotPageDTO listMyBots(MyBotParamDTO myBotParamDTO) {
        String uid = RequestContextUtil.getUID();
        Long spaceId = SpaceInfoUtil.getSpaceId();

        // Build query parameters
        Map<String, Object> param = buildQueryParams(myBotParamDTO, uid, spaceId);

        // Get count and setup pagination
        Long count = chatBotListMapper.countCheckBotList(param);
        setupPagination(param, myBotParamDTO);

        // Get release information
        ReleaseInfo releaseInfo = getReleaseInfo(uid);

        // Get bot list and process
        LinkedList<Map<String, Object>> list = chatBotListMapper.getCheckBotList(param);
        Set<Integer> botIdSet = processBotList(list, releaseInfo);

        // Process chain information if needed
        if (CollectionUtils.isNotEmpty(botIdSet)) {
            processChainInformation(list, botIdSet);
        }

        // Convert to DTOs and return
        Page<MyBotResponseDTO> myBotResponsesPage = createPageResult(list, count);
        return new MyBotPageDTO(
                myBotResponsesPage.getRecords(),
                Math.toIntExact(myBotResponsesPage.getTotal()),
                Math.toIntExact(myBotResponsesPage.getSize()),
                Math.toIntExact(myBotResponsesPage.getCurrent()),
                Math.toIntExact(myBotResponsesPage.getPages()));
    }

    @Override
    public Boolean deleteBot(Integer botId) {
        // Permission validation
        botPermissionUtil.checkBot(botId);
        return botService.deleteBot(botId);
    }

    private Map<String, Object> buildQueryParams(MyBotParamDTO myBotParamDTO, String uid, Long spaceId) {
        Map<String, Object> param = getBotCheckParam(myBotParamDTO, uid);
        param.put("spaceId", spaceId);

        if (myBotParamDTO.getVersion() != null) {
            param.put("version", myBotParamDTO.getVersion());
        }
        if (StringUtils.isNotBlank(myBotParamDTO.getSearchValue())) {
            param.put("botName", myBotParamDTO.getSearchValue());
        }
        if (myBotParamDTO.getSort() != null) {
            if (("createTime").equals(myBotParamDTO.getSort())) {
                param.put("sort", "a.create_time desc");
            }
            if (("updateTime").equals(myBotParamDTO.getSort())) {
                param.put("sort", "a.update_time desc");
            }
        }
        if (CollectionUtils.isNotEmpty(myBotParamDTO.getBotStatus())) {
            List<Integer> botStatus = myBotParamDTO.getBotStatus();
            param.put("status", botStatus);
            if (botStatus.contains(0)) {
                param.put("flag", 1);
            }
        }
        return param;
    }

    private void setupPagination(Map<String, Object> param, MyBotParamDTO myBotParamDTO) {
        int pageNum = myBotParamDTO.getPageIndex();
        int pageSize = Math.min(myBotParamDTO.getPageSize(), 200);
        int offset = (pageNum - 1) * pageSize;
        param.put("offset", offset);
        param.put("pageSize", pageSize);
    }

    private ReleaseInfo getReleaseInfo(String uid) {
        List<Integer> favoriteBotIdList = botFavoriteService.list(uid);

        Set<Long> wechatBotId = botOffiaccountService.getAccountList(uid)
                .stream()
                .map(BotOffiaccount::getBotId)
                .collect(Collectors.toSet());

        Set<Integer> apiBotId = chatBotApiService.getBotApiList(uid)
                .stream()
                .map(ChatBotApi::getBotId)
                .collect(Collectors.toSet());

        Set<Integer> botIdMcpSet = mcpDataService.getMcpByUid(uid)
                .stream()
                .map(McpData::getBotId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());

        return new ReleaseInfo(favoriteBotIdList, wechatBotId, apiBotId, botIdMcpSet);
    }

    private Set<Integer> processBotList(LinkedList<Map<String, Object>> list, ReleaseInfo releaseInfo) {
        Set<Integer> botIdSet = new HashSet<>();

        for (Map<String, Object> map : list) {
            Long botId = Convert.toLong(map.get("botId"));

            // Process release types
            List<Integer> botRelease = processReleaseTypes(map, botId, releaseInfo);
            map.put("releaseType", botRelease);

            // Process application form status
            processApplicationFormStatus(map, botId);

            // Process hot number
            processHotNumber(map);

            // Process favorite status
            processFavoriteStatus(map, botId, releaseInfo.favoriteBotIdList);

            botIdSet.add((Integer) map.get("botId"));
        }

        return botIdSet;
    }

    private List<Integer> processReleaseTypes(Map<String, Object> map, Long botId, ReleaseInfo releaseInfo) {
        List<Integer> botRelease = new ArrayList<>();

        if (map.get("botStatus").equals(1L) || map.get("botStatus").equals(4L) || map.get("botStatus").equals(2L)) {
            botRelease.add(ReleaseTypeEnum.MARKET.getCode());
        }

        if (releaseInfo.wechatBotId.contains(botId)) {
            botRelease.add(ReleaseTypeEnum.WECHAT.getCode());
        }

        if (releaseInfo.apiBotId.contains(botId.intValue())) {
            botRelease.add(ReleaseTypeEnum.BOT_API.getCode());
        }

        if (releaseInfo.botIdMcpSet.contains(botId.intValue())) {
            botRelease.add(ReleaseTypeEnum.MCP.getCode());
        }

        return botRelease;
    }

    private void processApplicationFormStatus(Map<String, Object> map, Long botId) {
        RBucket<String> bucket = redissonClient.getBucket(RECORD_BOT_ID + botId);
        if (bucket.isExists()) {
            map.put("af", "1");
        } else {
            ApplicationForm applicationForm = applicationFormMapper.selectOne(
                    Wrappers.lambdaQuery(ApplicationForm.class)
                            .eq(ApplicationForm::getBotId, botId)
                            .last("limit 1"));
            map.put("af", applicationForm != null ? "1" : "0");
        }
    }

    private void processHotNumber(Map<String, Object> map) {
        int hotNum = Convert.toInt(map.get("hotNum") == null ? 0 : map.get("hotNum"), 0);
        String langCode = "";
        HttpServletRequest request = RequestContextUtil.getCurrentRequest();
        if (request != null && request.getHeader("Lang-Code") != null) {
            langCode = request.getHeader("Lang-Code");
        }
        map.put("hotNum", BotUtil.convertNumToStr(hotNum, langCode));
    }

    private void processFavoriteStatus(Map<String, Object> map, Long botId, List<Integer> favoriteBotIdList) {
        map.put("isFavorite", favoriteBotIdList.contains(botId.intValue()) ? 1 : 0);
    }

    private void processChainInformation(LinkedList<Map<String, Object>> list, Set<Integer> botIdSet) {
        List<JSONObject> chainList = userLangChainDataService.findByBotIdSet(botIdSet);
        Map<Integer, JSONObject> chainMap = chainList.stream()
                .collect(Collectors.toMap(
                        json -> json.getInteger("botId"),
                        Function.identity(),
                        (existing, newValue) -> newValue));

        Map<Integer, Boolean> multiInputMap = chainList.stream()
                .collect(Collectors.toMap(
                        json -> json.getInteger("botId"),
                        json -> {
                            if (json.containsKey("extraInputs") && json.get("extraInputs") != null) {
                                JSONObject extraInputs = JSONObject.parseObject(json.getString("extraInputs"));
                                int size = extraInputs.size();
                                if (extraInputs.containsValue("image")) {
                                    size -= 2;
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

    private Page<MyBotResponseDTO> createPageResult(LinkedList<Map<String, Object>> list, Long count) {
        List<MyBotResponseDTO> myBotResponseDTOList = list.stream().map(this::mapToMyBotDTO).collect(Collectors.toList());

        Page<MyBotResponseDTO> page = new Page<>();
        page.setTotal(count);
        page.setRecords(myBotResponseDTOList);
        return page;
    }

    private static class ReleaseInfo {
        final List<Integer> favoriteBotIdList;
        final Set<Long> wechatBotId;
        final Set<Integer> apiBotId;
        final Set<Integer> botIdMcpSet;

        ReleaseInfo(List<Integer> favoriteBotIdList, Set<Long> wechatBotId,
                    Set<Integer> apiBotId, Set<Integer> botIdMcpSet) {
            this.favoriteBotIdList = favoriteBotIdList;
            this.wechatBotId = wechatBotId;
            this.apiBotId = apiBotId;
            this.botIdMcpSet = botIdMcpSet;
        }
    }

    private MyBotResponseDTO mapToMyBotDTO(Map<String, Object> map) {
        MyBotResponseDTO dto = new MyBotResponseDTO();
        dto.setBotId(Convert.toLong(map.get("botId")));
        dto.setUid(Convert.toStr(map.get("uid")));
        dto.setMarketBotId(Convert.toLong(map.get("marketBotId")));
        dto.setBotName(Convert.toStr(map.get("botName")));
        dto.setBotDesc(Convert.toStr(map.get("botDesc")));
        dto.setAvatar(Convert.toStr(map.get("avatar")));
        dto.setPrompt(Convert.toStr(map.get("prompt")));
        dto.setBotType(Convert.toInt(map.get("botType")));
        dto.setVersion(Convert.toInt(map.get("version")));
        dto.setSupportContext(Convert.toBool(map.get("supportContext")));
        dto.setMultiInput(map.get("multiInput"));
        dto.setBotStatus(Convert.toInt(map.get("botStatus")));
        dto.setBlockReason(Convert.toStr(map.get("blockReason")));
        dto.setReleaseType((List<Object>) map.get("releaseType"));
        dto.setHotNum(Convert.toStr(map.get("hotNum")));
        dto.setIsFavorite(Convert.toInt(map.get("isFavorite")));
        dto.setAf(Convert.toStr(map.get("af")));
        dto.setMaasId(Convert.toLong(map.get("maasId")));
        dto.setCreateTime((LocalDateTime) map.get("createTime"));
        return dto;
    }

    private static Map<String, Object> getBotCheckParam(MyBotParamDTO myBotParamDTO, String uid) {
        Map<String, Object> param = new HashMap<>();
        param.put("uid", uid);
        List<Integer> botStatuses = myBotParamDTO.getBotStatus();
        if (!Objects.isNull(botStatuses) && botStatuses.contains(1)) {
            botStatuses.add(4);
        }
        param.put("botStatus", botStatuses);
        if (Objects.nonNull(botStatuses) && botStatuses.size() == 1 && botStatuses.get(0) == -9) {
            param.put("flag", 1);
        }
        return param;
    }

}
