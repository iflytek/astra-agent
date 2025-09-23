package com.iflytek.astron.console.commons.service.workflow.impl;

import com.alibaba.fastjson2.JSONObject;
import com.iflytek.astron.console.commons.constant.ResponseEnum;
import com.iflytek.astron.console.commons.entity.bot.ChatBotBase;
import com.iflytek.astron.console.commons.entity.bot.UserLangChainInfo;
import com.iflytek.astron.console.commons.entity.workflow.CloneSynchronize;
import com.iflytek.astron.console.commons.exception.BusinessException;
import com.iflytek.astron.console.commons.service.bot.ChatBotDataService;
import com.iflytek.astron.console.commons.service.data.UserLangChainDataService;
import com.iflytek.astron.console.commons.service.workflow.WorkflowBotService;
import com.iflytek.astron.console.commons.util.MaasUtil;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RedissonClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Objects;

@Slf4j
@Service
public class WorkflowServiceImpl implements WorkflowBotService {

    @Autowired
    private UserLangChainDataService userLangChainDataService;

    @Autowired
    private RedissonClient redissonClient;

    @Autowired
    private ChatBotDataService chatBotDataService;

    @Override
    public Integer massCopySynchronize(CloneSynchronize synchronize) {
        String uid = synchronize.getUid();
        String originId = synchronize.getOriginId();
        Long maasId = synchronize.getCurrentId();
        String flowId = synchronize.getFlowId();
        Long spaceId = synchronize.getSpaceId();
        UserLangChainInfo info = userLangChainDataService.selectByFlowId(originId);
        if (Objects.isNull(info)) {
            log.error("----- unable to find workflow: {}", JSONObject.toJSONString(synchronize));
            throw new BusinessException(ResponseEnum.DATA_NOT_FOUND);
        }
        Integer botId = info.getBotId();
        // If maasId already exists, end directly
        if (redissonClient.getBucket(MaasUtil.generatePrefix(uid, botId)).isExists()) {
            log.info("----- Xinghuo has obtained this workflow, ending task: {}", JSONObject.toJSONString(synchronize));
            redissonClient.getBucket(MaasUtil.generatePrefix(uid, botId)).delete();
            return botId;
        }
        ChatBotBase base = chatBotDataService.copyBot(uid, botId, spaceId);
        Long currentBotId = Long.valueOf(base.getId());
        UserLangChainInfo userLangChainInfo = UserLangChainInfo.builder()
                .id(currentBotId)
                .botId(Math.toIntExact(currentBotId))
                .maasId(maasId)
                .flowId(flowId)
                .uid(uid)
                .updateTime(LocalDateTime.now())
                .build();
        userLangChainDataService.insertUserLangChainInfo(userLangChainInfo);
        log.info("----- Astron workflow synchronization successful, original massId: {}, flowId: {}, new assistant: {}", originId, flowId, currentBotId);
        return Math.toIntExact(currentBotId);
    }
}
