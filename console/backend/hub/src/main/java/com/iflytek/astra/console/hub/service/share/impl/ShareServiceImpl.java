package com.iflytek.astra.console.hub.service.share.impl;

import com.iflytek.astra.console.commons.constant.ResponseEnum;
import com.iflytek.astra.console.commons.entity.bot.BotDetail;
import com.iflytek.astra.console.commons.entity.space.AgentShareRecord;
import com.iflytek.astra.console.commons.exception.BusinessException;
import com.iflytek.astra.console.commons.service.bot.ChatBotDataService;
import com.iflytek.astra.console.hub.data.ShareDataService;
import com.iflytek.astra.console.hub.service.share.ShareService;
import com.iflytek.astra.console.hub.util.Md5Util;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.Objects;

/**
 * @author yingpeng
 */
@Service
@Slf4j
public class ShareServiceImpl implements ShareService {

    @Autowired
    private ChatBotDataService chatBotDataService;

    @Autowired
    private ShareDataService shareDataService;


    @Override
    public int getBotStatus(Long relatedId) {
        BotDetail detail = chatBotDataService.getBotDetail(relatedId);
        if (Objects.isNull(detail)) {
            throw new BusinessException(ResponseEnum.BOT_STATUS_INVALID);
        }
        return detail.getBotStatus();
    }

    /**
     * 生产智能体分享的密钥
     *
     * @param uid         uid
     * @param relatedType type
     * @param relatedId   id
     * @return string
     */
    @Override
    public String getShareKey(String uid, int relatedType, Long relatedId) {
        AgentShareRecord record = shareDataService.findActiveShareRecord(uid, relatedType, relatedId);
        if (Objects.isNull(record)) {
            // 生成新的key，写到表里
            String key = Md5Util.encryption(relatedId + "_salt_" + uid + System.currentTimeMillis() / 1000);
            shareDataService.createShareRecord(uid, relatedId, key, relatedType);
            return key;
        }
        return record.getShareKey();
    }

    /**
     * 根据key 获取分享的智能体
     *
     * @param shareKey key
     * @return record
     */
    @Override
    public AgentShareRecord getShareByKey(String shareKey) {
        return shareDataService.findByShareKey(shareKey);
    }
}
