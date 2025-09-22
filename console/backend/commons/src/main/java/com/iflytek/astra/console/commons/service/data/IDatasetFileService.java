package com.iflytek.astra.console.commons.service.data;

import com.iflytek.astra.console.commons.entity.dataset.DatasetStats;

import java.util.List;

public interface IDatasetFileService {

    List<DatasetStats> getMaasDataset(Long datasetId);

}
