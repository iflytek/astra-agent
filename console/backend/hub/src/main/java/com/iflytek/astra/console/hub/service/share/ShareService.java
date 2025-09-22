package com.iflytek.astra.console.hub.service.share;

import com.iflytek.astra.console.commons.entity.space.AgentShareRecord;

/**
 * @author yingpeng
 */
public interface ShareService {

    int getBotStatus(Long relatedId);

    /**
     * 生产智能体分享的密钥
     *
     * @param uid         uid
     * @param relatedType type
     * @param relatedId   id
     * @return string
     */
    String getShareKey(String uid, int relatedType, Long relatedId);

    /**
     * 根据key 获取分享的智能体
     *
     * @param shareKey key
     * @return record
     */
    AgentShareRecord getShareByKey(String shareKey);
}
