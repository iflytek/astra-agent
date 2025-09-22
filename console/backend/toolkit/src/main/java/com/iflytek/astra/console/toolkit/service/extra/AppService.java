package com.iflytek.astra.console.toolkit.service.extra;


import com.alibaba.fastjson2.*;
import com.iflytek.astra.console.commons.constant.ResponseEnum;
import com.iflytek.astra.console.commons.exception.BusinessException;
import com.iflytek.astra.console.toolkit.config.properties.ApiUrl;
import com.iflytek.astra.console.toolkit.config.properties.CommonConfig;
import com.iflytek.astra.console.toolkit.entity.biz.external.app.*;
import com.iflytek.astra.console.toolkit.tool.CommonTool;
import com.iflytek.astra.console.toolkit.tool.http.HeaderAuthHttpTool;
import com.iflytek.astra.console.toolkit.util.RedisUtil;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;

/**
 * Application service for managing app credentials and authentication Handles retrieval of API keys
 * and secrets for applications
 */
@Service
@Slf4j
public class AppService {

    @Resource
    ApiUrl apiUrl;
    @Resource
    RedisTemplate<String, Object> redisTemplate;

    @Resource
    RedisUtil redisUtil;
    @Autowired
    private CommonConfig commonConfig;


    /**
     * Get API key and secret for an application with caching support
     *
     * @param appId The application ID to query credentials for
     * @return AkSk object containing API key and secret
     * @throws BusinessException if credentials cannot be retrieved or app doesn't exist
     */
    public AkSk getAkSk(String appId) {
        // Handle special APPID
        AkSk akSk = specialAppHandle(appId);
        if (akSk != null) {
            return akSk;
        }

        // Get from cache
        String rKey = "app_detail_cache:" + appId;
        Object cache = redisUtil.get(rKey);
        if (cache != null) {
            PlatformAppDetail platformAppDetail = JSON.parseObject(JSON.toJSONString(cache), PlatformAppDetail.class);
            return new AkSk(platformAppDetail.getApiKey(), platformAppDetail.getApiSecret());
        }

        // Call API
        String appUrl = apiUrl.getAppUrl() + "/key/" + appId;
        String resp;
        try {
            resp = HeaderAuthHttpTool.get(appUrl, apiUrl.getApiKey(), apiUrl.getApiSecret());
            log.info("getAkSk, resp = {}", resp);
        } catch (NoSuchAlgorithmException | InvalidKeyException | IOException e) {
            throw new RuntimeException(e);
        }
        Object data = CommonTool.checkSystemCallResponse(resp);
        String errMsg = "Failed to query APPID credentials. Please check if APPID belongs to you or if APPID has been deleted, APPID=" + appId;
        if (data == null) {
            throw new BusinessException(ResponseEnum.RESPONSE_FAILED, errMsg);
        }
        JSONArray array = JSON.parseArray(data.toString());
        if (CollectionUtils.isEmpty(array)) {
            throw new BusinessException(ResponseEnum.RESPONSE_FAILED, errMsg);
        }
        return array.getObject(0, AkSk.class);
    }

    /**
     * Get API key and secret for an application via remote call (no caching)
     *
     * @param appId The application ID to query credentials for
     * @return AkSk object containing API key and secret
     * @throws BusinessException if credentials cannot be retrieved or app doesn't exist
     */
    public AkSk remoteCallAkSk(String appId) {
        AkSk akSk = specialAppHandle(appId);
        if (akSk != null) {
            return akSk;
        }

        String appUrl = apiUrl.getAppUrl() + "/key/" + appId;
        String resp;
        try {
            resp = HeaderAuthHttpTool.get(appUrl, apiUrl.getApiKey(), apiUrl.getApiSecret());
            log.info("remoteCallAkSk, resp = {}", resp);
        } catch (NoSuchAlgorithmException | InvalidKeyException | IOException e) {
            throw new RuntimeException(e);
        }
        Object data = CommonTool.checkSystemCallResponse(resp);
        String errMsg = "Failed to query APPID credentials. Please check if APPID belongs to you or if APPID has been deleted, APPID=" + appId;
        if (data == null) {
            throw new BusinessException(ResponseEnum.RESPONSE_FAILED, errMsg);
        }
        JSONArray array = JSON.parseArray(data.toString());
        if (CollectionUtils.isEmpty(array)) {
            throw new BusinessException(ResponseEnum.RESPONSE_FAILED, errMsg);
        }
        return array.getObject(0, AkSk.class);
    }

    /**
     * Handle special application IDs that have predefined credentials
     *
     * @param appId The application ID to check
     * @return AkSk object if this is a special app, null otherwise
     */
    private AkSk specialAppHandle(String appId) {
        if (appId.equals(commonConfig.getAppId())) {
            return new AkSk(commonConfig.getApiKey(), commonConfig.getApiSecret());
        }
        return null;
    }
}
