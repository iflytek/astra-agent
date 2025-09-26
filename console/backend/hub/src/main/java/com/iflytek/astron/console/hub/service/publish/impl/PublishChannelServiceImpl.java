package com.iflytek.astron.console.hub.service.publish.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.iflytek.astron.console.commons.entity.wechat.BotOffiaccount;
import com.iflytek.astron.console.commons.mapper.wechat.BotOffiaccountMapper;
import com.iflytek.astron.console.hub.service.publish.PublishChannelService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * Publish Channel Service Implementation
 *
 * Dynamically calculates bot publish channel status
 *
 * @author Omuigix
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PublishChannelServiceImpl implements PublishChannelService {

    private final BotOffiaccountMapper botOffiaccountMapper;

    @Override
    public List<String> parsePublishChannels(String publishChannels) {
        List<String> channels = new ArrayList<>();

        if (publishChannels != null && !publishChannels.trim().isEmpty()) {
            String[] channelArray = publishChannels.split(",");
            for (String channel : channelArray) {
                String trimmedChannel = channel.trim();
                if (!trimmedChannel.isEmpty()) {
                    channels.add(trimmedChannel);
                }
            }
        }

        log.debug("Parse publish channels: {} -> {}", publishChannels, channels);
        return channels;
    }

    @Override
    public String updatePublishChannels(String currentChannels, String channel, boolean add) {
        List<String> channels = new ArrayList<>();

        // Parse current channels
        if (currentChannels != null && !currentChannels.trim().isEmpty()) {
            String[] channelArray = currentChannels.split(",");
            for (String ch : channelArray) {
                String trimmed = ch.trim();
                if (!trimmed.isEmpty()) {
                    channels.add(trimmed);
                }
            }
        }

        // Add or remove channel
        if (add) {
            if (!channels.contains(channel)) {
                channels.add(channel);
            }
        } else {
            channels.remove(channel);
        }

        // Convert back to string
        String result = channels.isEmpty() ? null : String.join(",", channels);
        log.debug("Update publish channels: {} {} {} -> {}", currentChannels, add ? "add" : "remove", channel, result);
        return result;
    }

    @Override
    public String[] getWechatInfo(String uid, Integer botId) {
        log.debug("Retrieving WeChat binding info for bot: {}, uid: {}", botId, uid);

        QueryWrapper<BotOffiaccount> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq("uid", uid)
                .eq("bot_id", botId)
                .eq("status", 1); // 1 = bound status

        BotOffiaccount botOffiaccount = botOffiaccountMapper.selectOne(queryWrapper);

        if (botOffiaccount != null) {
            log.debug("Found WeChat binding: botId={}, appid={}", botId, botOffiaccount.getAppid());
            return new String[] {"1", botOffiaccount.getAppid()}; // Bound with appId
        } else {
            log.debug("No WeChat binding found for bot: {}", botId);
            return new String[] {"0", null}; // Unbound
        }
    }
}
