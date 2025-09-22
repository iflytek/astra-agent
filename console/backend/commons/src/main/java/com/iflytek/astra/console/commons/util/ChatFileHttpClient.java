package com.iflytek.astra.console.commons.util;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.HashMap;

@Slf4j
@Component
public class ChatFileHttpClient {

    @Value("${spark.app-id")
    private String appId;

    @Value("${spark.api-secret}")
    private String apiSecret;

    /**
     *
     * @description: 此方法专用于获取星火知识库服务，写死appid，区分插件
     * @date: 2024/09/25 14:16
     */
    public HashMap<String, String> getSignForXinghuoDs() {
        HashMap<String, String> signMap = new HashMap<>(8);
        long timestamp = System.currentTimeMillis() / 1000;
        signMap.put("signature", AuthStringUtil.getSignature(appId, apiSecret, timestamp));
        signMap.put("appId", appId);
        signMap.put("timestamp", String.valueOf(timestamp));
        return signMap;
    }
}
