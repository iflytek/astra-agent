package com.iflytek.astra.console.commons.service.data.impl;

import com.iflytek.astra.console.commons.entity.dataset.DatasetStats;
import com.iflytek.astra.console.commons.mapper.dataset.BotDatasetMaasMapper;
import com.iflytek.astra.console.commons.service.data.IDatasetFileService;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.stereotype.Service;

import java.util.*;

@Service
@Slf4j
public class DatasetFileServiceImpl implements IDatasetFileService {

    @Resource
    private BotDatasetMaasMapper botDatasetMaasMapper;

    /**
     * 获取maas数据集下的助手信息
     * @param datasetId
     * @return
     */
    @Override
    public List<DatasetStats> getMaasDataset(Long datasetId) {
        List<Long> datasetIdList = Collections.singletonList(datasetId);
        // 查询每个数据集关联的助手列表
        return botDatasetMaasMapper.selectBotStatsMaps(datasetIdList);
    }

}

