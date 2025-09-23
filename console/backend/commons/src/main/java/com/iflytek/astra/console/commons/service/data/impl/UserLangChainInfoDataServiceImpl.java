package com.iflytek.astra.console.commons.service.data.impl;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.iflytek.astra.console.commons.entity.bot.UserLangChainInfo;
import com.iflytek.astra.console.commons.mapper.UserLangChainInfoMapper;
import com.iflytek.astra.console.commons.service.data.UserLangChainDataService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * @author wowo_zZ
 * @since 2025/9/11 10:04
 **/

@Service
@RequiredArgsConstructor
public class UserLangChainInfoDataServiceImpl implements UserLangChainDataService {

    private final UserLangChainInfoMapper userLangChainInfoMapper;

    @Override
    public List<JSONObject> findByBotIdSet(Set<Integer> idSet) {
        // Check if input parameters are null or invalid
        if (idSet == null || idSet.isEmpty()) {
            return Collections.emptyList();
        }

        // Execute database query to get UserLangChainInfo list
        List<UserLangChainInfo> records = userLangChainInfoMapper.selectList(
                Wrappers.<UserLangChainInfo>lambdaQuery()
                        .in(UserLangChainInfo::getBotId, idSet));

        // Transform using Stream API
        return records.stream()
                .map(record -> JSON.parseObject(JSON.toJSONString(record)))
                .collect(Collectors.toList());
    }

    @Override
    public UserLangChainInfo insertUserLangChainInfo(UserLangChainInfo userLangChainInfo) {
        userLangChainInfoMapper.insert(userLangChainInfo);
        return userLangChainInfo;
    }

    @Override
    public UserLangChainInfo findOneByBotId(Integer botId) {
        if (botId == null) {
            return null;
        }

        return userLangChainInfoMapper.selectOne(
                new LambdaQueryWrapper<UserLangChainInfo>()
                        .eq(UserLangChainInfo::getBotId, botId)
                        .last("LIMIT 1"));
    }

    @Override
    public List<UserLangChainInfo> findListByBotId(Integer botId) {
        if (botId == null) {
            return new ArrayList<>();
        }

        return userLangChainInfoMapper.selectList(
                new LambdaQueryWrapper<UserLangChainInfo>()
                        .eq(UserLangChainInfo::getBotId, botId));
    }

    @Override
    public String findFlowIdByBotId(Integer botId) {
        UserLangChainInfo userLangChainInfo = userLangChainInfoMapper.selectOne(
                new LambdaQueryWrapper<UserLangChainInfo>()
                        .eq(UserLangChainInfo::getBotId, botId)
                        .orderByDesc(UserLangChainInfo::getUpdateTime)
                        .last("LIMIT 1"));
        return userLangChainInfo.getFlowId();
    }

    @Override
    public UserLangChainInfo selectByFlowId(String flowId) {
        if (flowId == null) {
            return null;
        }

        return userLangChainInfoMapper.selectOne(
                new LambdaQueryWrapper<UserLangChainInfo>()
                        .eq(UserLangChainInfo::getFlowId, flowId)
                        .last("LIMIT 1"));
    }

    @Override
    public UserLangChainInfo selectByMaasId(Long maasId) {
        if (maasId == null) {
            return null;
        }

        return userLangChainInfoMapper.selectOne(
                new LambdaQueryWrapper<UserLangChainInfo>()
                        .eq(UserLangChainInfo::getMaasId, maasId)
                        .last("LIMIT 1"));
    }

    @Override
    public List<UserLangChainInfo> findByMaasId(Long maasId) {
        if (maasId == null) {
            return null;
        }

        return userLangChainInfoMapper.selectList(
                new LambdaQueryWrapper<UserLangChainInfo>()
                        .eq(UserLangChainInfo::getMaasId, maasId));
    }

    @Override
    public UserLangChainInfo updateByBotId(Integer botId, UserLangChainInfo userLangChainInfo) {
        if (botId == null || userLangChainInfo == null) {
            return null;
        }

        userLangChainInfoMapper.update(userLangChainInfo,
                new LambdaQueryWrapper<UserLangChainInfo>()
                        .eq(UserLangChainInfo::getBotId, botId));

        return userLangChainInfo;
    }

}
