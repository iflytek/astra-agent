package com.iflytek.astra.console.commons.service.data.impl;

import com.iflytek.astra.console.commons.entity.dataset.DatasetStats;
import com.iflytek.astra.console.commons.mapper.dataset.BotDatasetMaasMapper;
import com.iflytek.astra.console.commons.service.data.IDatasetFileService;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.*;

@Service
@Slf4j
public class DatasetFileServiceImpl implements IDatasetFileService {

    @Resource
    private BotDatasetMaasMapper botDatasetMaasMapper;

    /**
     * Get assistant information under MAAS dataset
     *
     * @param datasetId
     * @return
     */
    @Override
    public List<DatasetStats> getMaasDataset(Long datasetId) {
        List<Long> datasetIdList = Collections.singletonList(datasetId);
        // Query the list of assistants associated with each dataset
        return botDatasetMaasMapper.selectBotStatsMaps(datasetIdList);
    }

}
