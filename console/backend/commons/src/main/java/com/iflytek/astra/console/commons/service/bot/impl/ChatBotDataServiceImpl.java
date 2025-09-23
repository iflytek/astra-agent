package com.iflytek.astra.console.commons.service.bot.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.collection.CollectionUtil;
import cn.hutool.core.date.DateUtil;
import cn.hutool.core.util.RandomUtil;
import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.core.toolkit.support.SFunction;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.iflytek.astra.console.commons.dto.bot.ChatBotApi;
import com.iflytek.astra.console.commons.dto.vcn.CustomV2VCNDTO;
import com.iflytek.astra.console.commons.entity.bot.*;
import com.iflytek.astra.console.commons.entity.chat.ChatList;
import com.iflytek.astra.console.commons.entity.model.McpData;
import com.iflytek.astra.console.commons.enums.bot.BotStatusEnum;
import com.iflytek.astra.console.commons.enums.bot.ReleaseTypeEnum;
import com.iflytek.astra.console.commons.mapper.bot.*;
import com.iflytek.astra.console.commons.mapper.chat.ChatListMapper;
import com.iflytek.astra.console.commons.mapper.vcn.CustomVCNMapper;
import com.iflytek.astra.console.commons.service.bot.BotFavoriteService;
import com.iflytek.astra.console.commons.service.bot.ChatBotDataService;
import com.iflytek.astra.console.commons.service.data.IDatasetInfoService;
import com.iflytek.astra.console.commons.service.mcp.McpDataService;
import com.iflytek.astra.console.commons.util.MaasUtil;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.*;
import java.util.function.Consumer;
import java.util.function.Supplier;

@Slf4j
@Service
public class ChatBotDataServiceImpl implements ChatBotDataService {

    @Autowired
    private ChatBotBaseMapper chatBotBaseMapper;

    @Autowired
    private ChatBotListMapper chatBotListMapper;

    @Autowired
    private ChatBotMarketMapper chatBotMarketMapper;

    @Autowired
    private BotDatasetMapper botDatasetMapper;

    @Autowired
    private MaasUtil maasUtil;

    @Autowired
    private ChatListMapper chatListMapper;

    @Autowired
    private ChatBotPromptStructMapper promptStructMapper;

    @Autowired
    private BotFavoriteService botFavoriteService;

    @Autowired
    private IDatasetInfoService datasetInfoService;

    @Autowired
    private CustomVCNMapper customVCNMapper;

    @Autowired
    private ChatBotApiMapper botApiMapper;

    @Autowired
    private McpDataService mcpDataService;

    public static final String BOT_INPUT_EXAMPLE_SPLIT = "%%split%%";

    @Override
    public Optional<ChatBotBase> findById(Integer botId) {
        ChatBotBase chatBot = chatBotBaseMapper.selectById(botId);
        return Optional.ofNullable(chatBot);
    }

    @Override
    public Optional<ChatBotBase> findByIdAndSpaceId(Integer botId, Long spaceId) {
        LambdaQueryWrapper<ChatBotBase> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(ChatBotBase::getId, botId);
        wrapper.eq(ChatBotBase::getSpaceId, spaceId);
        wrapper.eq(ChatBotBase::getIsDelete, 0);
        ChatBotBase chatBot = chatBotBaseMapper.selectOne(wrapper);
        return Optional.ofNullable(chatBot);
    }

    @Override
    public List<ChatBotBase> findByUid(String uid) {
        LambdaQueryWrapper<ChatBotBase> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(ChatBotBase::getUid, uid);
        return chatBotBaseMapper.selectList(wrapper);
    }

    @Override
    public List<ChatBotBase> findByUidAndSpaceId(String uid, Long spaceId) {
        LambdaQueryWrapper<ChatBotBase> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(ChatBotBase::getUid, uid);
        wrapper.eq(ChatBotBase::getSpaceId, spaceId);
        wrapper.eq(ChatBotBase::getIsDelete, 0);
        return chatBotBaseMapper.selectList(wrapper);
    }

    @Override
    public List<ChatBotBase> findBySpaceId(Long spaceId) {
        LambdaQueryWrapper<ChatBotBase> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(ChatBotBase::getSpaceId, spaceId);
        wrapper.eq(ChatBotBase::getIsDelete, 0);
        return chatBotBaseMapper.selectList(wrapper);
    }

    @Override
    public List<ChatBotBase> findByBotType(Integer botType) {
        LambdaQueryWrapper<ChatBotBase> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(ChatBotBase::getBotType, botType);
        wrapper.eq(ChatBotBase::getIsDelete, 0);
        return chatBotBaseMapper.selectList(wrapper);
    }

    @Override
    public List<ChatBotBase> findByBotTypeAndSpaceId(Integer botType, Long spaceId) {
        LambdaQueryWrapper<ChatBotBase> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(ChatBotBase::getBotType, botType);
        wrapper.eq(ChatBotBase::getSpaceId, spaceId);
        wrapper.eq(ChatBotBase::getIsDelete, 0);
        return chatBotBaseMapper.selectList(wrapper);
    }

    @Override
    public List<ChatBotBase> findActiveBotsBy(String uid) {
        LambdaQueryWrapper<ChatBotBase> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(ChatBotBase::getUid, uid);
        wrapper.eq(ChatBotBase::getIsDelete, 0);
        wrapper.orderByDesc(ChatBotBase::getUpdateTime);
        return chatBotBaseMapper.selectList(wrapper);
    }

    @Override
    public List<ChatBotBase> findActiveBotsBy(String uid, Long spaceId) {
        LambdaQueryWrapper<ChatBotBase> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(ChatBotBase::getUid, uid);
        wrapper.eq(ChatBotBase::getSpaceId, spaceId);
        wrapper.eq(ChatBotBase::getIsDelete, 0);
        wrapper.orderByDesc(ChatBotBase::getUpdateTime);
        return chatBotBaseMapper.selectList(wrapper);
    }

    @Override
    public ChatBotBase createBot(ChatBotBase chatBotBase) {
        chatBotBaseMapper.insert(chatBotBase);
        return chatBotBase;
    }

    @Override
    public ChatBotBase updateBot(ChatBotBase chatBotBase) {
        chatBotBaseMapper.updateById(chatBotBase);
        return chatBotBase;
    }

    @Override
    public boolean deleteBot(Integer botId) {
        ChatBotBase chatBot = new ChatBotBase();
        chatBot.setId(botId);
        chatBot.setIsDelete(1);
        return chatBotBaseMapper.updateById(chatBot) > 0;
    }

    @Override
    public boolean deleteBot(Integer botId, String uid) {
        return updateEntity(chatBotBaseMapper, ChatBotBase::getId, ChatBotBase::getUid, botId, uid, ChatBotBase::new, entity -> entity.setIsDelete(1)) &&
                updateEntity(chatBotListMapper, ChatBotList::getRealBotId, ChatBotList::getUid, botId, uid, ChatBotList::new, entity -> entity.setIsAct(0)) &&
                updateEntity(chatListMapper, ChatList::getBotId, ChatList::getUid, botId, uid, ChatList::new, entity -> entity.setIsDelete(1)) &&
                updateEntity(chatBotMarketMapper, ChatBotMarket::getBotId, ChatBotMarket::getUid, botId, uid, ChatBotMarket::new, entity -> entity.setIsDelete(1));
    }

    /**
     * Generic update method to update entity status based on two condition fields.
     *
     * @param <T> Entity type
     * @param mapper Corresponding MyBatis Plus Mapper
     * @param field1 First query condition field (usually ID related)
     * @param field2 Second query condition field (such as user ID)
     * @param value1 Value of the first field
     * @param value2 Value of the second field
     * @param entitySupplier // Supplier for creating new entity
     * @param configurator Configuration on how to set updated entity attributes
     * @return Returns true if update is successful and affected rows > 0; otherwise returns false
     */
    private <T> boolean updateEntity(
            BaseMapper<T> mapper,
            SFunction<T, ?> field1,
            SFunction<T, ?> field2,
            Object value1,
            Object value2,
            Supplier<T> entitySupplier,
            Consumer<T> configurator) {
        LambdaQueryWrapper<T> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(field1, value1)
                .eq(field2, value2);

        // Create new entity instance using supplier
        T entity = entitySupplier.get();
        // Set entity attributes according to the passed configurator
        configurator.accept(entity);

        int rowsAffected = mapper.update(entity, queryWrapper);
        return rowsAffected > 0;
    }

    @Override
    public boolean deleteBot(Integer botId, Long spaceId) {
        LambdaQueryWrapper<ChatBotBase> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(ChatBotBase::getId, botId);
        wrapper.eq(ChatBotBase::getSpaceId, spaceId);

        ChatBotBase chatBot = new ChatBotBase();
        chatBot.setIsDelete(1);
        return chatBotBaseMapper.update(chatBot, wrapper) > 0;
    }

    @Override
    public boolean deleteBotsByIds(List<Integer> botIds) {
        if (botIds == null || botIds.isEmpty()) {
            return false;
        }

        ChatBotBase chatBot = new ChatBotBase();
        chatBot.setIsDelete(1);

        LambdaQueryWrapper<ChatBotBase> wrapper = new LambdaQueryWrapper<>();
        wrapper.in(ChatBotBase::getId, botIds);

        return chatBotBaseMapper.update(chatBot, wrapper) > 0;
    }

    @Override
    public boolean deleteBotsByIds(List<Integer> botIds, Long spaceId) {
        if (botIds == null || botIds.isEmpty()) {
            return false;
        }

        ChatBotBase chatBot = new ChatBotBase();
        chatBot.setIsDelete(1);

        LambdaQueryWrapper<ChatBotBase> wrapper = new LambdaQueryWrapper<>();
        wrapper.in(ChatBotBase::getId, botIds);
        wrapper.eq(ChatBotBase::getSpaceId, spaceId);

        return chatBotBaseMapper.update(chatBot, wrapper) > 0;
    }

    @Override
    public long countBotsByUid(String uid) {
        LambdaQueryWrapper<ChatBotBase> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(ChatBotBase::getUid, uid);
        wrapper.eq(ChatBotBase::getIsDelete, 0);
        return chatBotBaseMapper.selectCount(wrapper);
    }

    @Override
    public long countBotsByUid(String uid, Long spaceId) {
        LambdaQueryWrapper<ChatBotBase> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(ChatBotBase::getUid, uid);
        wrapper.eq(ChatBotBase::getSpaceId, spaceId);
        wrapper.eq(ChatBotBase::getIsDelete, 0);
        return chatBotBaseMapper.selectCount(wrapper);
    }

    @Override
    public List<ChatBotList> findUserBotList(String uid) {
        LambdaQueryWrapper<ChatBotList> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(ChatBotList::getUid, uid);
        wrapper.eq(ChatBotList::getIsAct, 1);
        wrapper.orderByDesc(ChatBotList::getUpdateTime);
        return chatBotListMapper.selectList(wrapper);
    }

    @Override
    public ChatBotList addBotToUserList(ChatBotList chatBotList) {
        chatBotListMapper.insert(chatBotList);
        return chatBotList;
    }

    @Override
    public boolean removeBotFromUserList(String uid, Integer marketBotId) {
        LambdaQueryWrapper<ChatBotList> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(ChatBotList::getUid, uid);
        wrapper.eq(ChatBotList::getMarketBotId, marketBotId);

        ChatBotList chatBotList = new ChatBotList();
        chatBotList.setIsAct(0);

        return chatBotListMapper.update(chatBotList, wrapper) > 0;
    }

    @Override
    public List<ChatBotMarket> findMarketBots(Integer botStatus, int page, int size) {
        LambdaQueryWrapper<ChatBotMarket> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(ChatBotMarket::getIsDelete, 0);
        if (botStatus != null) {
            wrapper.eq(ChatBotMarket::getBotStatus, botStatus);
        }
        wrapper.orderByDesc(ChatBotMarket::getCreateTime);

        Page<ChatBotMarket> pageParam = new Page<>(page, size);
        Page<ChatBotMarket> result = chatBotMarketMapper.selectPage(pageParam, wrapper);
        return result.getRecords();
    }

    @Override
    public List<ChatBotMarket> findMarketBotsByHot(int limit) {
        LambdaQueryWrapper<ChatBotMarket> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(ChatBotMarket::getIsDelete, 0);
        wrapper.eq(ChatBotMarket::getBotStatus, 2);
        wrapper.orderByDesc(ChatBotMarket::getHotNum);
        wrapper.last("LIMIT " + limit);
        return chatBotMarketMapper.selectList(wrapper);
    }

    @Override
    public List<ChatBotMarket> searchMarketBots(String keyword, Integer botType) {
        LambdaQueryWrapper<ChatBotMarket> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(ChatBotMarket::getIsDelete, 0);
        wrapper.eq(ChatBotMarket::getBotStatus, 2);

        if (StringUtils.hasText(keyword)) {
            wrapper.and(w -> w.like(ChatBotMarket::getBotName, keyword).or().like(ChatBotMarket::getBotDesc, keyword));
        }

        if (botType != null) {
            wrapper.eq(ChatBotMarket::getBotType, botType);
        }

        wrapper.orderByDesc(ChatBotMarket::getHotNum);
        return chatBotMarketMapper.selectList(wrapper);
    }

    /**
     * Query whether assistant is deleted
     *
     * @param botId
     */
    @Override
    public boolean botIsDeleted(Long botId) {
        if (null == botId) {
            return false;
        }
        ChatBotBase chatBotBase = chatBotBaseMapper.selectOne(Wrappers.lambdaQuery(ChatBotBase.class)
                .eq(ChatBotBase::getId, botId)
                .eq(ChatBotBase::getIsDelete, 1));

        ChatBotMarket chatBotMarket = chatBotMarketMapper.selectOne(Wrappers.lambdaQuery(ChatBotMarket.class)
                .eq(ChatBotMarket::getBotId, botId)
                .eq(ChatBotMarket::getIsDelete, 1));
        return chatBotBase != null || chatBotMarket != null;
    }

    @Override
    public ChatBotMarket findMarketBotByBotId(Integer botId) {
        if (botId == null) {
            return null;
        }

        LambdaQueryWrapper<ChatBotMarket> wrapper = Wrappers.lambdaQuery(ChatBotMarket.class)
                .eq(ChatBotMarket::getBotId, botId)
                .eq(ChatBotMarket::getIsDelete, 0);

        return chatBotMarketMapper.selectOne(wrapper);
    }

    @Override
    public Boolean checkRepeatBotName(String uid, Integer botId, String botName, Long spaceId) {
        // Cannot have the same name as own bot, excluding deleted ones
        QueryWrapper<ChatBotBase> wrapper = new QueryWrapper<>();
        if (spaceId == null) {
            wrapper.eq("uid", uid);
            wrapper.isNull("space_id");
        } else {
            wrapper.eq("space_id", spaceId);
        }
        wrapper.eq("bot_name", botName);
        wrapper.eq("is_delete", 0);
        if (!Objects.isNull(botId)) {
            wrapper.ne("id", botId);
        }
        if (chatBotBaseMapper.exists(wrapper)) {
            // Bot name duplication
            return Boolean.TRUE;
        }
        return Boolean.FALSE;
    }

    @Override
    public void deleteBotForDeleteSpace(String uid, Long spaceId, HttpServletRequest request) {
        if (spaceId == null) {
            log.error("deleteBotForDeleteSpace-failed, spaceId is empty, uid={}", uid);
            return;
        }
        // Query botId based on spaceId
        List<Integer> spaceBotIdList = chatBotBaseMapper.selectList(Wrappers.lambdaQuery(ChatBotBase.class)
                .eq(ChatBotBase::getSpaceId, spaceId)
                .eq(ChatBotBase::getIsDelete, 0)
                .select(ChatBotBase::getId))
                .stream()
                .map(ChatBotBase::getId)
                .toList();
        log.info("deleteBotForDeleteSpace-start to remove assistants, uid={}, spaceId={}, spaceBotIdList={}", uid, spaceId, spaceBotIdList);
        // Remove assistants
        removeBotForDeleteSpace(uid, spaceId, spaceBotIdList);
        log.info("deleteBotForDeleteSpace-start to delete assistants, uid={}, spaceId={}", uid, spaceId);
        // Delete bot
        chatBotBaseMapper.update(Wrappers.lambdaUpdate(ChatBotBase.class)
                .eq(ChatBotBase::getSpaceId, spaceId)
                .eq(ChatBotBase::getIsDelete, 0)
                .set(ChatBotBase::getIsDelete, 1));
        log.info("deleteBotForDeleteSpace-start to maintain botDataSet, uid={}, spaceId={}", uid, spaceId);
        // Update status of datasets associated with assistant
        LambdaUpdateWrapper<BotDataset> updateWrapper = new LambdaUpdateWrapper<>();
        updateWrapper.in(BotDataset::getBotId, spaceBotIdList)
                .set(BotDataset::getIsAct, 0)
                .set(BotDataset::getUpdateTime, LocalDateTime.now());
        botDatasetMapper.update(null, updateWrapper);
        // If version = 3, sync to engineering institute
        for (Integer botId : spaceBotIdList) {
            maasUtil.deleteSynchronize(botId, spaceId, request);
        }
    }

    private void removeBotForDeleteSpace(String uid, Long spaceId, List<Integer> spaceBotIdList) {
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

    @Override
    public ChatBotList findByUidAndBotId(String uid, Integer botId) {
        return chatBotListMapper.selectOne(new LambdaQueryWrapper<>(ChatBotList.class)
                .eq(ChatBotList::getUid, uid)
                .eq(ChatBotList::getRealBotId, botId)
                .orderByDesc(ChatBotList::getCreateTime)
                .last("limit 1"));
    }

    @Override
    public ChatBotList createUserBotList(ChatBotList chatBotList) {
        chatBotListMapper.insert(chatBotList);
        return chatBotList;
    }

    @Override
    public ChatBotBase copyBot(String uid, Integer botId, Long spaceId) {
        // Create new assistant with same name
        BotDetail botDetail = chatBotBaseMapper.botDetail(Math.toIntExact(botId));
        botDetail.setId(null);
        ChatBotBase base = new ChatBotBase();
        // Set a new assistant name as differentiation
        base.setUid(uid);
        base.setSpaceId(spaceId);
        base.setBotName(base.getBotName() + RandomUtil.randomString(6));
        base.setUpdateTime(LocalDateTime.now());
        base.setCreateTime(LocalDateTime.now());
        chatBotBaseMapper.insert(base);
        return base;
    }

    @Override
    public Boolean takeoffBot(String uid, Long spaceId, TakeoffList takeoffList) {
        int botId = takeoffList.getBotId();
        UpdateWrapper<ChatBotMarket> wrapper = new UpdateWrapper<>();
        wrapper.eq("bot_id", botId);
        if (!chatBotMarketMapper.exists(wrapper)) {
            return Boolean.TRUE;
        }
        // Directly remove assistant from shelf, no need for comprehensive management review
        wrapper.set("bot_status", 0);
        chatBotMarketMapper.update(null, wrapper);
        botFavoriteService.delete(uid, botId);
        return Boolean.TRUE;
    }

    @Override
    public boolean updateBotBasicInfo(Integer botId, String botDesc, String prologue, String inputExamples) {
        LambdaUpdateWrapper<ChatBotBase> wrapper = new LambdaUpdateWrapper<>(ChatBotBase.class);
        wrapper.set(ChatBotBase::getBotDesc, botDesc);
        wrapper.set(ChatBotBase::getPrologue, prologue);
        wrapper.set(ChatBotBase::getInputExample, inputExamples);
        wrapper.eq(ChatBotBase::getId, botId);
        return chatBotBaseMapper.update(null, wrapper) > 0;
    }

    @Override
    public BotDetail getBotDetail(Long botId) {
        return chatBotBaseMapper.botDetail(Math.toIntExact(botId));
    }

    @Override
    public PromptBotDetail getPromptBotDetail(Integer botId, String uid) {
        BotDetail botBase = chatBotBaseMapper.botDetail(botId);

        PromptBotDetail promptBotDetail = new PromptBotDetail();
        BeanUtils.copyProperties(botBase, promptBotDetail);
        Integer supportUpload = botBase.getSupportUpload();
        promptBotDetail.setSupportUploadList(Collections.singletonList(supportUpload));

        List<ChatBotPromptStruct> promptStructList = promptStructMapper.selectList(
                Wrappers.lambdaQuery(ChatBotPromptStruct.class).eq(ChatBotPromptStruct::getBotId, botId));
        if (CollectionUtil.isNotEmpty(promptStructList)) {
            promptBotDetail.setPromptStructList(promptStructList);
        } else {
            promptBotDetail.setPromptStructList(new ArrayList<>());
        }
        if (promptBotDetail.getInputExample() != null) {
            String inputExample = promptBotDetail.getInputExample();
            if (!StrUtil.contains(inputExample, BOT_INPUT_EXAMPLE_SPLIT)) {
                inputExample = inputExample.replace(",", BOT_INPUT_EXAMPLE_SPLIT);
            }
            List<String> inputExampleList = Arrays.asList(inputExample.split(BOT_INPUT_EXAMPLE_SPLIT));
            promptBotDetail.setInputExampleList(inputExampleList);
        } else {
            promptBotDetail.setInputExampleList(new ArrayList<>());
        }

        List<DatasetInfo> datasetInfoList = datasetInfoService.getDatasetByBot(uid, botId);
        promptBotDetail.setDatasetList(datasetInfoList);

        // Convert bot_type to parent_type_key value
        Integer botType = promptBotDetail.getBotType();
        if (botType == null) {
            botType = 0;
        }
        promptBotDetail.setBotType(BotTypeList.getParentTypeKey(botType));
        String vcnCn = promptBotDetail.getVcnCn();
        if (org.apache.commons.lang3.StringUtils.isNotBlank(vcnCn) && getVcnDetail(vcnCn) == null) {
            promptBotDetail.setVcnCn(null);
        }
        if (DateUtil.parseLocalDateTime(promptBotDetail.getCreateTime().toString(), "yyyy-MM-dd HH:mm:ss.S").isBefore(LocalDateTime.of(2025, 2, 24, 10, 00))) {
            promptBotDetail.setEditable(false);
        } else {
            promptBotDetail.setEditable(true);
        }
        // Get assistant release channels
        promptBotDetail.setReleaseType(getReleaseChannel(uid, botId));
        return promptBotDetail;
    }

    @Override
    public Map<String, Object> getVcnDetail(String vcnCode) {
        CustomV2VCNDTO detail = customVCNMapper.getVcnByCode(vcnCode);
        if (detail == null) {
            return null;
        }
        String uid = detail.getUid();
        if (uid != null) {
            Map<String, Object> map = new HashMap<>();
            map.put("id", detail.getVcnId());
            map.put("name", detail.getName());
            map.put("vcn", vcnCode);
            map.put("mode", uid);
            map.put("imgUrl", detail.getAvatar());
            map.put("audioUrl", detail.getTryVCNUrl());
            return map;
        }

        return BeanUtil.beanToMap(detail);
    }

    @Override
    public List<Integer> getReleaseChannel(String uid, Integer botId) {
        List<Integer> releaseList = new ArrayList<>();
        boolean marketExist = chatBotMarketMapper.exists(Wrappers.lambdaQuery(ChatBotMarket.class)
                .eq(ChatBotMarket::getUid, uid)
                .eq(ChatBotMarket::getBotId, botId)
                .in(ChatBotMarket::getBotStatus, BotStatusEnum.shelves()));
        if (marketExist) {
            releaseList.add(ReleaseTypeEnum.MARKET.getCode());
        }
        boolean apiExist = botApiMapper.exists(Wrappers.lambdaQuery(ChatBotApi.class)
                .eq(ChatBotApi::getUid, uid)
                .eq(ChatBotApi::getBotId, botId)
                .orderByDesc(ChatBotApi::getUpdateTime));
        if (apiExist) {
            releaseList.add(ReleaseTypeEnum.BOT_API.getCode());
        }
        // MCP channel processing
        McpData mcp = mcpDataService.getMcp(botId.longValue());
        if (Objects.nonNull(mcp) && "1".equals(mcp.getReleased())) {
            releaseList.add(ReleaseTypeEnum.MCP.getCode());
        }
        return releaseList;
    }
}
