package com.iflytek.astron.console.commons.service.data;


import com.iflytek.astron.console.commons.entity.bot.DatasetInfo;

import java.util.List;

public interface IDatasetInfoService {

    /**
     * Query datasets under the assistant
     *
     * @param uid
     * @param botId
     * @return
     */
    List<DatasetInfo> getDatasetByBot(String uid, Integer botId);

}
