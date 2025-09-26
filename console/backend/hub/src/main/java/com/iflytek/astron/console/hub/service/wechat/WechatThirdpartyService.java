package com.iflytek.astron.console.hub.service.wechat;

import com.iflytek.astron.console.hub.dto.wechat.WechatAuthCallbackDto;

/**
 * WeChat第三方平台服务接口
 *
 * @author Omuigix
 */
public interface WechatThirdpartyService {

    /**
     * 获取预authorization码
     *
     * @param botId botID
     * @param appid 微信official accountAppID
     * @param uid userID
     * @return 预authorization码
     */
    String getPreAuthCode(Integer botId, String appid, String uid);

    /**
     * 构建微信authorization链接
     *
     * @param preAuthCode 预authorization码
     * @param appid 微信official accountAppID
     * @param redirectUrl callback地址
     * @return authorization链接
     */
    String buildAuthUrl(String preAuthCode, String appid, String redirectUrl);

    /**
     * 处理微信授权成功callback
     *
     * @param callbackData callback数据
     */
    void handleAuthorizedCallback(WechatAuthCallbackDto callbackData);

    /**
     * 处理微信授权更新callback
     *
     * @param callbackData callback数据
     */
    void handleUpdateAuthorizedCallback(WechatAuthCallbackDto callbackData);

    /**
     * 处理微信取消授权callback
     *
     * @param callbackData callback数据
     */
    void handleUnauthorizedCallback(WechatAuthCallbackDto callbackData);

    /**
     * 刷新validation票据
     *
     * @param componentVerifyTicket validation票据
     */
    void refreshVerifyTicket(String componentVerifyTicket);

    /**
     * get第三方平台访问令牌
     *
     * @return 访问令牌
     */
    String getComponentAccessToken();
}
