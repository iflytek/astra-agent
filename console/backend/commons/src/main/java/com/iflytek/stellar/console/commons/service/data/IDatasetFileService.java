package com.iflytek.astra.console.commons.service.data;

import com.iflytek.astra.console.commons.entity.dataset.DatasetStats;

import java.io.IOException;
import java.util.List;
import java.util.Map;

public interface IDatasetFileService {

    List<DatasetStats> getMaasDataset(Long datasetId);

}
