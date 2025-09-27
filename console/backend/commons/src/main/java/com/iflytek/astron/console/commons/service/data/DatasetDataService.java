package com.iflytek.astron.console.commons.service.data;

import com.iflytek.astron.console.commons.entity.bot.BotDataset;
import com.iflytek.astron.console.commons.entity.bot.DatasetFile;
import com.iflytek.astron.console.commons.entity.bot.DatasetInfo;

import java.util.List;
import java.util.Optional;

public interface DatasetDataService {

    /** Query dataset by ID */
    Optional<DatasetInfo> findById(Long datasetId);

    /** Query dataset list by user ID */
    List<DatasetInfo> findByUid(String uid);

    /** Query dataset by status */
    List<DatasetInfo> findByStatus(Integer status);

    /** Search dataset by name */
    List<DatasetInfo> searchByName(String uid, String name);

    /** Create dataset */
    DatasetInfo createDataset(DatasetInfo datasetInfo);

    /** Update dataset information */
    DatasetInfo updateDataset(DatasetInfo datasetInfo);

    /** Delete dataset */
    boolean deleteDataset(Long datasetId);

    /** Update dataset status */
    boolean updateDatasetStatus(Long datasetId, Integer status);

    /** Query file list by dataset ID */
    List<DatasetFile> findFilesByDatasetId(Long datasetId);

    /** Query dataset files by status */
    List<DatasetFile> findFilesByStatus(Long datasetId, Integer status);

    /** Add file to dataset */
    DatasetFile addFileToDataset(DatasetFile datasetFile);

    /** Delete dataset file */
    boolean deleteDatasetFile(Long fileId);

    /** Update file processing status */
    boolean updateFileStatus(Long fileId, Integer status);

    /** Batch update file status */
    boolean batchUpdateFileStatus(List<Long> fileIds, Integer status);

    /** Query datasets associated with bot */
    List<BotDataset> findDatasetsByBotId(Long botId);

    /** Query active bot-dataset associations */
    List<BotDataset> findActiveBotDatasets(Long botId);

    /** Associate bot with dataset */
    BotDataset associateBotWithDataset(BotDataset botDataset);

    /** Disassociate bot from dataset */
    boolean disassociateBotFromDataset(Long botId, Long datasetId);

    /** Update bot-dataset association status */
    boolean updateBotDatasetStatus(Long botId, Long datasetId, Integer isAct);

    /** Count datasets by user ID */
    long countDatasetsByUid(String uid);

    /** Count files by dataset ID */
    long countFilesByDatasetId(Long datasetId);

    /** Count processing files */
    long countProcessingFiles(Long datasetId);

    /**
     * Select dataset list by agent ID
     *
     * @param botId Agent ID
     * @return Dataset information list
     */
    List<DatasetInfo> selectDatasetListByBotId(Integer botId);
}
