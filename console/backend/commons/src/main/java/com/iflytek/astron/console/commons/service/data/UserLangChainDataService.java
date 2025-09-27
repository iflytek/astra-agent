package com.iflytek.astron.console.commons.service.data;

import com.alibaba.fastjson2.JSONObject;
import com.iflytek.astron.console.commons.entity.bot.UserLangChainInfo;

import java.util.List;
import java.util.Set;

/**
 * @author wowo_zZ
 * @since 2025/9/11 10:03
 **/

public interface UserLangChainDataService {

    List<JSONObject> findByBotIdSet(Set<Integer> idSet);

    UserLangChainInfo insertUserLangChainInfo(UserLangChainInfo userLangChainInfo);

    /**
     * Query single workflow configuration information by agent ID
     *
     * @param botId Agent ID
     * @return Workflow configuration information, returns null when not exists
     */
    UserLangChainInfo findOneByBotId(Integer botId);

    List<UserLangChainInfo> findListByBotId(Integer botId);

    String findFlowIdByBotId(Integer botId);

    UserLangChainInfo selectByFlowId(String flowId);

    UserLangChainInfo selectByMaasId(Long maasId);

    List<UserLangChainInfo> findByMaasId(Long maasId);

    /**
     * Update UserLangChainInfo by botId
     *
     * @param botId Bot ID
     * @param userLangChainInfo Updated information
     * @return Updated UserLangChainInfo
     */
    UserLangChainInfo updateByBotId(Integer botId, UserLangChainInfo userLangChainInfo);
}
