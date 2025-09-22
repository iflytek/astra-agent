package com.iflytek.astra.console.commons.service.data;


import com.iflytek.astra.console.commons.entity.bot.DatasetInfo;

import java.util.List;

public interface IDatasetInfoService {

    /**
     * 查询助手名下的数据集
     *
     * @param uid
     * @param botId
     * @return
     */
    List<DatasetInfo> getDatasetByBot(String uid, Integer botId);

}
