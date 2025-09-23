package com.iflytek.astron.console.commons.service.data.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.iflytek.astron.console.commons.entity.bot.BotDataset;
import com.iflytek.astron.console.commons.entity.bot.DatasetInfo;
import com.iflytek.astron.console.commons.mapper.bot.BotDatasetMapper;
import com.iflytek.astron.console.commons.mapper.bot.DatasetInfoMapper;
import com.iflytek.astron.console.commons.service.data.IDatasetInfoService;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@Slf4j
public class DatasetInfoServiceImpl implements IDatasetInfoService {

    @Resource
    private DatasetInfoMapper datasetInfoMapper;

    @Resource
    private BotDatasetMapper botDatasetMapper;

    @Override
    public List<DatasetInfo> getDatasetByBot(String uid, Integer botId) {
        List<DatasetInfo> infoList = new ArrayList<>();
        List<BotDataset> botDatasetList = botDatasetMapper.selectList(Wrappers.lambdaQuery(BotDataset.class)
                .eq(BotDataset::getBotId, botId)
                .eq(BotDataset::getIsAct, 1));
        if (Objects.isNull(botDatasetList) || botDatasetList.isEmpty()) {
            return infoList;
        }

        Set<Long> infoIdSet = botDatasetList.stream()
                .map(BotDataset::getDatasetId)
                .collect(Collectors.toSet());

        infoList = datasetInfoMapper.selectList(Wrappers.lambdaQuery(DatasetInfo.class)
                .in(DatasetInfo::getId, infoIdSet)
                .eq(DatasetInfo::getUid, uid)
                .eq(DatasetInfo::getStatus, 2));
        return infoList;
    }
}
