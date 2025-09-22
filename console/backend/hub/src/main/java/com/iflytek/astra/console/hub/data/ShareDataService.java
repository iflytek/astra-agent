package com.iflytek.astra.console.hub.data;

import com.iflytek.astra.console.commons.entity.space.AgentShareRecord;

public interface ShareDataService {

    /**
     * 根据用户ID、分享类型、关联ID查找活跃的分享记录
     */
    AgentShareRecord findActiveShareRecord(String uid, int shareType, Long baseId);

    /**
     * 创建新的分享记录
     */
    AgentShareRecord createShareRecord(String uid, Long baseId, String shareKey, int shareType);

    /**
     * 根据分享密钥查找活跃的分享记录
     */
    AgentShareRecord findByShareKey(String shareKey);
}
