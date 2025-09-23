package com.iflytek.astron.console.hub.service.share;

import com.iflytek.astron.console.commons.entity.space.AgentShareRecord;

/**
 * @author yingpeng
 */
public interface ShareService {

    int getBotStatus(Long relatedId);

    /**
     * Generate a share key for the agent
     *
     * @param uid uid
     * @param relatedType type
     * @param relatedId id
     * @return string
     */
    String getShareKey(String uid, int relatedType, Long relatedId);

    /**
     * Get shared agent by key
     *
     * @param shareKey key
     * @return record
     */
    AgentShareRecord getShareByKey(String shareKey);
}
