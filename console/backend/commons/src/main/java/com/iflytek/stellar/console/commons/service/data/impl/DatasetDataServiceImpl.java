package com.iflytek.astra.console.commons.service.data.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.iflytek.astra.console.commons.entity.bot.BotDataset;
import com.iflytek.astra.console.commons.entity.bot.DatasetFile;
import com.iflytek.astra.console.commons.entity.bot.DatasetInfo;
import com.iflytek.astra.console.commons.mapper.bot.BotDatasetMapper;
import com.iflytek.astra.console.commons.mapper.bot.DatasetFileMapper;
import com.iflytek.astra.console.commons.mapper.bot.DatasetInfoMapper;
import com.iflytek.astra.console.commons.service.data.DatasetDataService;
import java.util.List;
import java.util.Optional;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class DatasetDataServiceImpl implements DatasetDataService {

    @Autowired
    private DatasetInfoMapper datasetInfoMapper;

    @Autowired
    private DatasetFileMapper datasetFileMapper;

    @Autowired
    private BotDatasetMapper botDatasetMapper;

    @Override
    public Optional<DatasetInfo> findById(Long datasetId) {
        DatasetInfo datasetInfo = datasetInfoMapper.selectById(datasetId);
        return Optional.ofNullable(datasetInfo);
    }

    @Override
    public List<DatasetInfo> findByUid(String uid) {
        LambdaQueryWrapper<DatasetInfo> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(DatasetInfo::getUid, uid);
        wrapper.ne(DatasetInfo::getStatus, -1);
        wrapper.orderByDesc(DatasetInfo::getUpdateTime);
        return datasetInfoMapper.selectList(wrapper);
    }

    @Override
    public List<DatasetInfo> findByStatus(Integer status) {
        LambdaQueryWrapper<DatasetInfo> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(DatasetInfo::getStatus, status);
        wrapper.orderByDesc(DatasetInfo::getUpdateTime);
        return datasetInfoMapper.selectList(wrapper);
    }

    @Override
    public List<DatasetInfo> searchByName(String uid, String name) {
        LambdaQueryWrapper<DatasetInfo> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(DatasetInfo::getUid, uid);
        wrapper.ne(DatasetInfo::getStatus, -1);

        if (StringUtils.hasText(name)) {
            wrapper.like(DatasetInfo::getName, name);
        }

        wrapper.orderByDesc(DatasetInfo::getUpdateTime);
        return datasetInfoMapper.selectList(wrapper);
    }

    @Override
    public DatasetInfo createDataset(DatasetInfo datasetInfo) {
        datasetInfoMapper.insert(datasetInfo);
        return datasetInfo;
    }

    @Override
    public DatasetInfo updateDataset(DatasetInfo datasetInfo) {
        datasetInfoMapper.updateById(datasetInfo);
        return datasetInfo;
    }

    @Override
    public boolean deleteDataset(Long datasetId) {
        DatasetInfo datasetInfo = new DatasetInfo();
        datasetInfo.setId(datasetId);
        datasetInfo.setStatus(-1);
        return datasetInfoMapper.updateById(datasetInfo) > 0;
    }

    @Override
    public boolean updateDatasetStatus(Long datasetId, Integer status) {
        DatasetInfo datasetInfo = new DatasetInfo();
        datasetInfo.setId(datasetId);
        datasetInfo.setStatus(status);
        return datasetInfoMapper.updateById(datasetInfo) > 0;
    }

    @Override
    public List<DatasetFile> findFilesByDatasetId(Long datasetId) {
        LambdaQueryWrapper<DatasetFile> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(DatasetFile::getDatasetId, datasetId);
        wrapper.ne(DatasetFile::getStatus, -1);
        wrapper.orderByDesc(DatasetFile::getCreateTime);
        return datasetFileMapper.selectList(wrapper);
    }

    @Override
    public List<DatasetFile> findFilesByStatus(Long datasetId, Integer status) {
        LambdaQueryWrapper<DatasetFile> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(DatasetFile::getDatasetId, datasetId);
        wrapper.eq(DatasetFile::getStatus, status);
        wrapper.orderByDesc(DatasetFile::getCreateTime);
        return datasetFileMapper.selectList(wrapper);
    }

    @Override
    public DatasetFile addFileToDataset(DatasetFile datasetFile) {
        datasetFileMapper.insert(datasetFile);
        return datasetFile;
    }

    @Override
    public boolean deleteDatasetFile(Long fileId) {
        DatasetFile datasetFile = new DatasetFile();
        datasetFile.setId(fileId);
        datasetFile.setStatus(-1);
        return datasetFileMapper.updateById(datasetFile) > 0;
    }

    @Override
    public boolean updateFileStatus(Long fileId, Integer status) {
        DatasetFile datasetFile = new DatasetFile();
        datasetFile.setId(fileId);
        datasetFile.setStatus(status);
        return datasetFileMapper.updateById(datasetFile) > 0;
    }

    @Override
    public boolean batchUpdateFileStatus(List<Long> fileIds, Integer status) {
        if (fileIds == null || fileIds.isEmpty()) {
            return false;
        }

        DatasetFile datasetFile = new DatasetFile();
        datasetFile.setStatus(status);

        LambdaQueryWrapper<DatasetFile> wrapper = new LambdaQueryWrapper<>();
        wrapper.in(DatasetFile::getId, fileIds);

        return datasetFileMapper.update(datasetFile, wrapper) > 0;
    }

    @Override
    public List<BotDataset> findDatasetsByBotId(Long botId) {
        LambdaQueryWrapper<BotDataset> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(BotDataset::getBotId, botId);
        wrapper.orderByDesc(BotDataset::getCreateTime);
        return botDatasetMapper.selectList(wrapper);
    }

    @Override
    public List<BotDataset> findActiveBotDatasets(Long botId) {
        LambdaQueryWrapper<BotDataset> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(BotDataset::getBotId, botId);
        wrapper.eq(BotDataset::getIsAct, 1);
        wrapper.orderByDesc(BotDataset::getCreateTime);
        return botDatasetMapper.selectList(wrapper);
    }

    @Override
    public BotDataset associateBotWithDataset(BotDataset botDataset) {
        botDatasetMapper.insert(botDataset);
        return botDataset;
    }

    @Override
    public boolean disassociateBotFromDataset(Long botId, Long datasetId) {
        LambdaQueryWrapper<BotDataset> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(BotDataset::getBotId, botId);
        wrapper.eq(BotDataset::getDatasetId, datasetId);
        return botDatasetMapper.delete(wrapper) > 0;
    }

    @Override
    public boolean updateBotDatasetStatus(Long botId, Long datasetId, Integer isAct) {
        LambdaQueryWrapper<BotDataset> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(BotDataset::getBotId, botId);
        wrapper.eq(BotDataset::getDatasetId, datasetId);

        BotDataset botDataset = new BotDataset();
        botDataset.setIsAct(isAct);

        return botDatasetMapper.update(botDataset, wrapper) > 0;
    }

    @Override
    public long countDatasetsByUid(String uid) {
        LambdaQueryWrapper<DatasetInfo> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(DatasetInfo::getUid, uid);
        wrapper.ne(DatasetInfo::getStatus, -1);
        return datasetInfoMapper.selectCount(wrapper);
    }

    @Override
    public long countFilesByDatasetId(Long datasetId) {
        LambdaQueryWrapper<DatasetFile> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(DatasetFile::getDatasetId, datasetId);
        wrapper.ne(DatasetFile::getStatus, -1);
        return datasetFileMapper.selectCount(wrapper);
    }

    @Override
    public long countProcessingFiles(Long datasetId) {
        LambdaQueryWrapper<DatasetFile> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(DatasetFile::getDatasetId, datasetId);
        wrapper.eq(DatasetFile::getStatus, 1);
        return datasetFileMapper.selectCount(wrapper);
    }


    @Override
    public List<DatasetInfo> selectDatasetListByBotId(Integer botId) {
        return botDatasetMapper.selectDatasetListByBotId(botId);
    }
}
