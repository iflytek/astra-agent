package com.iflytek.astra.console.hub.data.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.iflytek.astra.console.commons.entity.space.AgentShareRecord;
import com.iflytek.astra.console.commons.mapper.AgentShareRecordMapper;
import com.iflytek.astra.console.hub.data.ShareDataService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
@Slf4j
public class ShareDataServiceImpl implements ShareDataService {

    @Autowired
    private AgentShareRecordMapper shareRecordMapper;

    @Override
    public AgentShareRecord findActiveShareRecord(String uid, int shareType, Long baseId) {
        return shareRecordMapper.selectOne(Wrappers.lambdaQuery(AgentShareRecord.class)
                .eq(AgentShareRecord::getUid, uid)
                .eq(AgentShareRecord::getShareType, shareType)
                .eq(AgentShareRecord::getBaseId, baseId)
                .eq(AgentShareRecord::getIsAct, 1));
    }

    @Override
    public AgentShareRecord createShareRecord(String uid, Long baseId, String shareKey, int shareType) {
        AgentShareRecord record = new AgentShareRecord();
        record.setUid(uid);
        record.setBaseId(baseId);
        record.setShareKey(shareKey);
        record.setShareType(shareType);
        record.setIsAct(1);
        shareRecordMapper.insert(record);
        return record;
    }

    @Override
    public AgentShareRecord findByShareKey(String shareKey) {
        return shareRecordMapper.selectOne(Wrappers.lambdaQuery(AgentShareRecord.class)
                .eq(AgentShareRecord::getShareKey, shareKey)
                .eq(AgentShareRecord::getIsAct, 1));
    }
}
