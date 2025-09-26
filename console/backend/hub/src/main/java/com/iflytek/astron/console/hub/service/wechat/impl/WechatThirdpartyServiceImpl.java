package com.iflytek.astron.console.hub.service.wechat.impl;

import com.iflytek.astron.console.hub.dto.wechat.WechatAuthCallbackDto;
import com.iflytek.astron.console.hub.service.wechat.BotOffiaccountService;
import com.iflytek.astron.console.hub.service.wechat.WechatThirdpartyService;
import com.iflytek.astron.console.commons.exception.BusinessException;
import com.iflytek.astron.console.commons.constant.ResponseEnum;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RBucket;
import org.redisson.api.RedissonClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import com.iflytek.astron.console.toolkit.util.OkHttpUtil;
import com.alibaba.fastjson2.JSONObject;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.Duration;

/**
 * WeChat第三方平台服务实现
 *
 * 优化点： 1. 使用Redisson替代RedisUtil 2. 统一exception处理 3. 优化缓存键management 4. 增强日志记录 5. 提取常量配置
 *
 * @author Omuigix
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class WechatThirdpartyServiceImpl implements WechatThirdpartyService {

    private final BotOffiaccountService botOffiaccountService;
    private final RedissonClient redissonClient;

    @Value("${wechat.thirdparty.component-appid}")
    private String componentAppid;

    @Value("${wechat.thirdparty.component-secret}")
    private String componentSecret;

    // Redis缓存键前缀
    private static final String REDIS_KEY_PREFIX = "wechat:thirdparty:";
    private static final String PRE_AUTH_CODE_KEY = REDIS_KEY_PREFIX + "pre_auth_code:";
    private static final String PRE_BIND_KEY = REDIS_KEY_PREFIX + "pre_bind:";
    private static final String COMPONENT_ACCESS_TOKEN_KEY = REDIS_KEY_PREFIX + "component_access_token";
    private static final String COMPONENT_VERIFY_TICKET_KEY = REDIS_KEY_PREFIX + "component_verify_ticket";
    private static final String AUTHORIZATION_ACCESS_TOKEN_KEY = REDIS_KEY_PREFIX + "authorization_access_token:";
    private static final String AUTHORIZATION_REFRESH_TOKEN_KEY = REDIS_KEY_PREFIX + "authorization_refresh_token:";

    // 缓存过期时间
    private static final Duration PRE_AUTH_CODE_EXPIRE = Duration.ofSeconds(1800);
    private static final Duration PRE_BIND_EXPIRE = Duration.ofSeconds(1800);
    private static final Duration ACCESS_TOKEN_EXPIRE = Duration.ofSeconds(7200);
    private static final Duration VERIFY_TICKET_EXPIRE = Duration.ofSeconds(43200);

    @Override
    public String getPreAuthCode(Integer botId, String appid, String uid) {
        log.info("获取预授权码: botId={}, appid={}, uid={}", botId, appid, uid);

        String preAuthCodeKey = PRE_AUTH_CODE_KEY + botId;
        RBucket<String> bucket = redissonClient.getBucket(preAuthCodeKey);

        String preAuthCode;
        if (bucket.isExists()) {
            preAuthCode = bucket.get();
            log.info("使用缓存的预授权码: botId={}, appid={}", botId, appid);
        } else {
            // get第三方平台访问令牌
            String componentAccessToken = getComponentAccessToken();

            // 调用WeChatAPIget预authorization码
            preAuthCode = requestPreAuthCodeFromWechat(componentAccessToken);

            // 缓存预authorization码（短时间缓存，防止重复request）
            bucket.set(preAuthCode, Duration.ofSeconds(5));
            log.info("获取新的预授权码: botId={}, appid={}", botId, appid);
        }

        // set预bindstatus，防止official account被bind到多个bot
        setPreBindStatus(appid, botId, uid);

        return preAuthCode;
    }

    @Override
    public String buildAuthUrl(String preAuthCode, String appid, String redirectUrl) {
        if (!StringUtils.hasText(preAuthCode) || !StringUtils.hasText(redirectUrl)) {
            throw new BusinessException(ResponseEnum.PARAMS_ERROR);
        }

        String authUrl = String.format(
                "https://mp.weixin.qq.com/cgi-bin/componentloginpage?" +
                        "component_appid=%s&pre_auth_code=%s&redirect_uri=%s&auth_type=1",
                componentAppid, preAuthCode, redirectUrl);

        log.info("构建微信授权链接: appid={}, redirectUrl={}", appid, redirectUrl);
        return authUrl;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void handleAuthorizedCallback(WechatAuthCallbackDto callbackData) {
        log.info("处理微信授权成功回调: authorizerAppid={}", callbackData.getAuthorizerAppid());

        String authorizerAppid = callbackData.getAuthorizerAppid();
        if (!StringUtils.hasText(authorizerAppid)) {
            log.error("微信授权成功回调失败：公众号AppID为空");
            return;
        }

        // get预bind信息
        Integer botId = getPreBindBotId(authorizerAppid);
        if (botId == null) {
            log.error("微信授权成功回调失败：未找到预绑定信息，authorizerAppid={}", authorizerAppid);
            return;
        }

        try {
            // 初始化authorization令牌
            initAuthorizationToken(authorizerAppid, callbackData.getAuthorizationCode());

            // 建立bind关系
            // 注意：这里需要从预bind信息中getuserID，暂时使用占位符
            String uid = getUidFromPreBindInfo(authorizerAppid, botId);
            botOffiaccountService.bind(botId, authorizerAppid, uid);

            // 清理缓存
            cleanupPreBindCache(authorizerAppid, botId);

            log.info("微信授权成功回调处理完成: botId={}, authorizerAppid={}", botId, authorizerAppid);
        } catch (Exception e) {
            log.error("微信授权成功回调处理失败: botId={}, authorizerAppid={}", botId, authorizerAppid, e);
            throw new BusinessException(ResponseEnum.WECHAT_AUTH_FAILED);
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void handleUpdateAuthorizedCallback(WechatAuthCallbackDto callbackData) {
        log.info("处理微信授权更新回调: authorizerAppid={}", callbackData.getAuthorizerAppid());

        // authorizationupdate的处理逻辑与authorizationsuccess类似
        handleAuthorizedCallback(callbackData);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void handleUnauthorizedCallback(WechatAuthCallbackDto callbackData) {
        log.info("处理微信取消授权回调: authorizerAppid={}", callbackData.getAuthorizerAppid());

        String authorizerAppid = callbackData.getAuthorizerAppid();
        if (StringUtils.hasText(authorizerAppid)) {
            botOffiaccountService.unbind(authorizerAppid);
            log.info("微信取消授权回调处理完成: authorizerAppid={}", authorizerAppid);
        } else {
            log.error("微信取消授权回调失败：公众号AppID为空");
        }
    }

    @Override
    public void refreshVerifyTicket(String componentVerifyTicket) {
        if (!StringUtils.hasText(componentVerifyTicket)) {
            log.error("刷新验证票据失败：票据为空");
            return;
        }

        RBucket<String> bucket = redissonClient.getBucket(COMPONENT_VERIFY_TICKET_KEY);
        bucket.set(componentVerifyTicket, VERIFY_TICKET_EXPIRE);

        log.info("验证票据刷新成功");
    }

    @Override
    public String getComponentAccessToken() {
        RBucket<String> bucket = redissonClient.getBucket(COMPONENT_ACCESS_TOKEN_KEY);

        if (bucket.isExists()) {
            return bucket.get();
        }

        // getvalidation票据
        RBucket<String> ticketBucket = redissonClient.getBucket(COMPONENT_VERIFY_TICKET_KEY);
        String componentVerifyTicket = ticketBucket.get();

        if (!StringUtils.hasText(componentVerifyTicket)) {
            throw new BusinessException(ResponseEnum.WECHAT_VERIFY_TICKET_MISSING);
        }

        // 调用WeChatAPIget访问令牌
        String accessToken = requestComponentAccessTokenFromWechat(componentVerifyTicket);

        // 缓存访问令牌
        bucket.set(accessToken, ACCESS_TOKEN_EXPIRE);

        log.info("第三方平台访问令牌获取成功");
        return accessToken;
    }

    /**
     * 设置预bind状态
     */
    private void setPreBindStatus(String appid, Integer botId, String uid) {
        String preBindKey = PRE_BIND_KEY + appid;
        RBucket<String> bucket = redissonClient.getBucket(preBindKey);

        // 存储 botId:uid 格式的信息
        String preBindInfo = botId + ":" + uid;
        bucket.set(preBindInfo, PRE_BIND_EXPIRE);

        log.debug("设置预绑定状态: appid={}, botId={}, uid={}", appid, botId, uid);
    }

    /**
     * 获取预bind的智能体ID
     */
    private Integer getPreBindBotId(String appid) {
        String preBindKey = PRE_BIND_KEY + appid;
        RBucket<String> bucket = redissonClient.getBucket(preBindKey);

        if (bucket.isExists()) {
            String preBindInfo = bucket.get();
            try {
                // 解析 botId:uid 格式
                String[] parts = preBindInfo.split(":");
                if (parts.length >= 1) {
                    return Integer.valueOf(parts[0]);
                }
            } catch (NumberFormatException e) {
                log.warn("预绑定信息格式错误: appid={}, preBindInfo={}", appid, preBindInfo);
            }
        }

        return null;
    }

    /**
     * 获取预bind信息中的用户ID
     */
    private String getUidFromPreBindInfo(String appid, Integer botId) {
        String preBindKey = PRE_BIND_KEY + appid;
        RBucket<String> bucket = redissonClient.getBucket(preBindKey);

        if (bucket.isExists()) {
            String preBindInfo = bucket.get();
            try {
                // 解析 botId:uid 格式
                String[] parts = preBindInfo.split(":");
                if (parts.length >= 2) {
                    return parts[1];
                }
            } catch (Exception e) {
                log.warn("解析预绑定用户ID失败: appid={}, preBindInfo={}", appid, preBindInfo, e);
            }
        }

        log.warn("未找到预绑定用户ID: appid={}, botId={}", appid, botId);
        return null;
    }

    /**
     * 清理预bind缓存
     */
    private void cleanupPreBindCache(String appid, Integer botId) {
        // 清理预bindstatus
        String preBindKey = PRE_BIND_KEY + appid;
        redissonClient.getBucket(preBindKey).delete();

        // 清理预authorization码
        String preAuthCodeKey = PRE_AUTH_CODE_KEY + botId;
        redissonClient.getBucket(preAuthCodeKey).delete();

        log.debug("清理预绑定缓存: appid={}, botId={}", appid, botId);
    }

    /**
     * 从微信API获取预authorization码
     */
    private String requestPreAuthCodeFromWechat(String componentAccessToken) {
        String url = "https://api.weixin.qq.com/cgi-bin/component/api_create_preauthcode?component_access_token=" + componentAccessToken;

        JSONObject requestBody = new JSONObject();
        requestBody.put("component_appid", componentAppid);

        try {
            log.info("调用微信API获取预授权码: url={}", url);
            String response = OkHttpUtil.post(url, requestBody.toJSONString());
            log.info("微信API返回预授权码响应: {}", response);

            JSONObject responseJson = JSONObject.parseObject(response);
            String preAuthCode = responseJson.getString("pre_auth_code");

            if (StringUtils.hasText(preAuthCode)) {
                return preAuthCode;
            } else {
                log.error("获取预授权码失败: {}", response);
                throw new BusinessException(ResponseEnum.WECHAT_AUTH_FAILED);
            }
        } catch (Exception e) {
            log.error("调用微信API获取预授权码异常", e);
            throw new BusinessException(ResponseEnum.WECHAT_AUTH_FAILED);
        }
    }

    /**
     * 从WeChatAPI获取第三方平台访问令牌
     */
    private String requestComponentAccessTokenFromWechat(String componentVerifyTicket) {
        String url = "https://api.weixin.qq.com/cgi-bin/component/api_component_token";

        JSONObject requestBody = new JSONObject();
        requestBody.put("component_appid", componentAppid);
        requestBody.put("component_appsecret", componentSecret);
        requestBody.put("component_verify_ticket", componentVerifyTicket);

        try {
            log.info("调用微信API获取第三方平台访问令牌: componentVerifyTicket={}", componentVerifyTicket);
            String response = OkHttpUtil.post(url, requestBody.toJSONString());
            log.info("微信API返回访问令牌响应: {}", response);

            JSONObject responseJson = JSONObject.parseObject(response);
            String componentAccessToken = responseJson.getString("component_access_token");

            if (StringUtils.hasText(componentAccessToken)) {
                return componentAccessToken;
            } else {
                log.error("获取第三方平台访问令牌失败: {}", response);
                throw new BusinessException(ResponseEnum.WECHAT_AUTH_FAILED);
            }
        } catch (Exception e) {
            log.error("调用微信API获取第三方平台访问令牌异常", e);
            throw new BusinessException(ResponseEnum.WECHAT_AUTH_FAILED);
        }
    }

    /**
     * 初始化authorization令牌
     */
    private void initAuthorizationToken(String authorizerAppid, String authorizationCode) {
        String componentAccessToken = getComponentAccessToken();
        String url = "https://api.weixin.qq.com/cgi-bin/component/api_query_auth?component_access_token=" + componentAccessToken;

        JSONObject requestBody = new JSONObject();
        requestBody.put("component_appid", componentAppid);
        requestBody.put("authorization_code", authorizationCode);

        try {
            log.info("初始化授权令牌: authorizerAppid={}, authorizationCode={}", authorizerAppid, authorizationCode);
            String response = OkHttpUtil.post(url, requestBody.toJSONString());
            log.info("微信API返回授权信息: {}", response);

            JSONObject responseJson = JSONObject.parseObject(response);
            if (responseJson.containsKey("errcode")) {
                log.error("初始化授权令牌失败: {}", response);
                throw new BusinessException(ResponseEnum.WECHAT_AUTH_FAILED);
            }

            JSONObject authorizationInfo = responseJson.getJSONObject("authorization_info");
            if (authorizationInfo == null) {
                log.error("授权信息为空: {}", response);
                throw new BusinessException(ResponseEnum.WECHAT_AUTH_FAILED);
            }

            String authorizationAccessToken = authorizationInfo.getString("authorizer_access_token");
            String authorizationRefreshToken = authorizationInfo.getString("authorizer_refresh_token");

            if (StringUtils.hasText(authorizationAccessToken) && StringUtils.hasText(authorizationRefreshToken)) {
                String accessTokenKey = AUTHORIZATION_ACCESS_TOKEN_KEY + authorizerAppid;
                String refreshTokenKey = AUTHORIZATION_REFRESH_TOKEN_KEY + authorizerAppid;

                redissonClient.getBucket(accessTokenKey).set(authorizationAccessToken, ACCESS_TOKEN_EXPIRE);
                redissonClient.getBucket(refreshTokenKey).set(authorizationRefreshToken);

                log.info("授权令牌初始化成功: authorizerAppid={}", authorizerAppid);
            } else {
                log.error("授权令牌信息不完整: accessToken={}, refreshToken={}",
                        authorizationAccessToken, authorizationRefreshToken);
                throw new BusinessException(ResponseEnum.WECHAT_AUTH_FAILED);
            }
        } catch (Exception e) {
            log.error("初始化授权令牌异常: authorizerAppid={}", authorizerAppid, e);
            throw new BusinessException(ResponseEnum.WECHAT_AUTH_FAILED);
        }
    }
}
