package com.iflytek.astron.console.hub.service.wechat;

import com.iflytek.astron.console.commons.entity.wechat.BotOffiaccount;

import java.util.List;

/**
 * 智能体与微信公众号bind服务接口
 *
 * @author Omuigix
 */
public interface BotOffiaccountService {

    /**
     * 建立智能体与微信公众号的bind关系
     *
     * @param botId botID
     * @param appid 微信official accountAppID
     * @param uid userID
     */
    void bind(Integer botId, String appid, String uid);

    /**
     * 解除微信公众号bind
     *
     * @param appid 微信official accountAppID
     */
    void unbind(String appid);

    /**
     * 根据用户ID获取已bind的微信公众号列表
     *
     * @param uid userID
     * @return bind列表
     */
    List<BotOffiaccount> getAccountList(String uid);

    /**
     * 根据微信AppID获取bind的智能体信息
     *
     * @param appid 微信official accountAppID
     * @return bind信息
     */
    BotOffiaccount getByAppid(String appid);

    /**
     * 根据智能体ID获取bind的微信公众号信息
     *
     * @param botId botID
     * @return bind信息
     */
    BotOffiaccount getByBotId(Integer botId);
}
