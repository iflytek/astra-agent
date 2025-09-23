package com.iflytek.astra.console.hub.service.workflow.impl;

import com.alibaba.fastjson2.JSONObject;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.iflytek.astra.console.commons.constant.ResponseEnum;
import com.iflytek.astra.console.commons.entity.bot.BotInfoDto;
import com.iflytek.astra.console.commons.entity.bot.ChatBotBase;
import com.iflytek.astra.console.commons.entity.bot.UserLangChainInfo;
import com.iflytek.astra.console.commons.entity.workflow.CloneSynchronize;
import com.iflytek.astra.console.commons.exception.BusinessException;
import com.iflytek.astra.console.commons.service.bot.BotService;
import com.iflytek.astra.console.commons.service.data.UserLangChainDataService;
import com.iflytek.astra.console.commons.util.MaasUtil;
import com.iflytek.astra.console.commons.util.space.SpaceInfoUtil;
import com.iflytek.astra.console.hub.entity.maas.MaasDuplicate;
import com.iflytek.astra.console.hub.entity.maas.MaasTemplate;
import com.iflytek.astra.console.hub.entity.maas.WorkflowTemplateQueryDto;
import com.iflytek.astra.console.hub.mapper.MaasTemplateMapper;
import com.iflytek.astra.console.hub.service.workflow.BotMaasService;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RedissonClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;

/**
 * @Author cherry
 */
@Service
@Slf4j
public class BotMaasServiceImpl implements BotMaasService {

    @Autowired
    private MaasUtil maasUtil;

    @Autowired
    private UserLangChainDataService userLangChainDataService;

    @Autowired
    private RedissonClient redissonClient;

    @Autowired
    private BotService botService;

    @Autowired
    private MaasTemplateMapper maasTemplateMapper;

    @Override
    public BotInfoDto createFromTemplate(String uid, MaasDuplicate massDuplicate) {
        Long spaceId = SpaceInfoUtil.getSpaceId();
        // Create an event, consumed by /massCopySynchronize
        Long maasId = massDuplicate.getMaasId();
        UserLangChainInfo userLangChainInfo = userLangChainDataService.selectByMaasId(maasId);
        if (Objects.isNull(userLangChainInfo)) {
            log.info("----- Xinghuo did not find Astra workflow: {}", JSONObject.toJSONString(userLangChainInfo));
            throw new BusinessException(ResponseEnum.BOT_NOT_EXIST);
        }
        redissonClient.getBucket(MaasUtil.generatePrefix(uid, Math.toIntExact(userLangChainInfo.getId()))).set(userLangChainInfo.getId().toString(), Duration.ofSeconds(60));
        BotInfoDto botInfoDto = botService.insertWorkflowBot(uid, massDuplicate, spaceId);
        // Check if response is successful
        if (botInfoDto == null) {
            throw new BusinessException(ResponseEnum.CREATE_BOT_FAILED);
        }
        // Copy a new workflow for the assistant
        JSONObject res = maasUtil.copyWorkFlow(massDuplicate.getMaasId(), uid);
        if (Objects.isNull(res) || res.isEmpty()) {
            throw new BusinessException(ResponseEnum.CREATE_BOT_FAILED);
        }
        Integer botId = botInfoDto.getBotId();
        botService.addMaasInfo(uid, res, botId, spaceId);
        botInfoDto.setFlowId((Long) res.getByPath("data.id"));
        return botInfoDto;
    }

    @Override
    public Integer massCopySynchronize(CloneSynchronize synchronize) {
        log.info("------ Astra workflow copy synchronization: {}", JSONObject.toJSONString(synchronize));
        String uid = synchronize.getUid();
        String originId = synchronize.getOriginId();
        Long maasId = synchronize.getCurrentId();
        String flowId = synchronize.getFlowId();
        Long spaceId = synchronize.getSpaceId();
        UserLangChainInfo userLangChainInfo = userLangChainDataService.selectByMaasId(maasId);
        if (Objects.isNull(userLangChainInfo)) {
            log.info("----- Xinghuo did not find Astra workflow: {}", JSONObject.toJSONString(synchronize));
            throw new BusinessException(ResponseEnum.BOT_NOT_EXIST);
        }
        Integer botId = userLangChainInfo.getBotId();
        // If massId already exists, end directly
        if (redissonClient.getBucket(MaasUtil.generatePrefix(uid, botId)).isExists()) {
            log.info("----- Xinghuo has obtained this workflow, ending task: {}", JSONObject.toJSONString(synchronize));
            redissonClient.getBucket(MaasUtil.generatePrefix(uid, botId)).delete();
            return botId;
        }
        ChatBotBase base = botService.copyBot(uid, botId, spaceId);
        Long currentBotId = Long.valueOf(base.getId());
        UserLangChainInfo userLangChainInfoNew = UserLangChainInfo.builder()
                .id(currentBotId)
                .botId(Math.toIntExact(currentBotId))
                .maasId(maasId)
                .flowId(flowId)
                .uid(uid)
                .updateTime(LocalDateTime.now())
                .build();
        userLangChainDataService.insertUserLangChainInfo(userLangChainInfo);
        log.info("----- Astra workflow synchronization successful, original massId: {}, flowId: {}, new assistant: {}", originId, flowId, currentBotId);
        return base.getId();
    }

    @Override
    public List<MaasTemplate> templateList(WorkflowTemplateQueryDto queryDto) {
        int pageIndex = queryDto.getPageIndex();
        int pageSize = queryDto.getPageSize();
        pageSize = Math.min(pageSize, 50);
        Page<MaasTemplate> page = new Page<>(pageIndex, pageSize);

        LambdaQueryWrapper<MaasTemplate> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(MaasTemplate::getIsAct, 1);
        // Query by groupId
        if (queryDto.getGroupId() != null) {
            queryWrapper.eq(MaasTemplate::getGroupId, queryDto.getGroupId());
        }
        queryWrapper.orderByDesc(MaasTemplate::getOrderIndex);

        Page<MaasTemplate> resultPage = maasTemplateMapper.selectPage(page, queryWrapper);

        // 4. Return data list of current page
        return resultPage.getRecords();
    }
}
