package com.iflytek.astron.console.hub.service.publish;

import java.util.List;

/**
 * Publish Channel Service Interface
 *
 * Responsible for calculating and managing bot publish channel status
 *
 * @author Omuigix
 */
public interface PublishChannelService {

    /**
     * Parse bot publish channels list from database Directly retrieves from publish_channels field in
     * chat_bot_market table
     *
     * @param publishChannels Comma-separated publish channels string from database
     * @return List of publish channels (MARKET, API, WECHAT, MCP)
     */
    List<String> parsePublishChannels(String publishChannels);

    /**
     * Update publish channels string by adding or removing specified channel
     *
     * @param currentChannels Current publish channels string
     * @param channel Channel to operate on (MARKET, API, WECHAT, MCP)
     * @param add true to add, false to remove
     * @return Updated publish channels string
     */
    String updatePublishChannels(String currentChannels, String channel, boolean add);

    /**
     * Get WeChat official account binding information
     *
     * @param uid User ID
     * @param botId Bot ID
     * @return WeChat binding information [status, AppID]
     */
    String[] getWechatInfo(String uid, Integer botId);
}
