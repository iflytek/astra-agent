package com.iflytek.astron.console.hub.controller.extra;

import cn.xfyun.util.CryptTools;
import com.iflytek.astron.console.commons.annotation.RateLimit;
import com.iflytek.astron.console.commons.response.ApiResult;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;

import java.net.URLEncoder;
import java.util.HashMap;
import java.util.Map;

/**
 * Real-time Speech Recognition Controller
 *
 * @author yingpeng
 */
@Slf4j
@Tag(name = "Real-time Speech Recognition Capability")
@RestController
@RequestMapping(value = "/rtasr")
public class RtasrController {

    @Value("${spark.lfasr-appId}")
    private String appId;

    @Value("${spark.lfasr-key}")
    private String lfasrApikey;

    private static final String RTASR_URL = "wss://rtasr.xfyun.cn/v1/ws";

    /**
     * Get authorization token for speech recognition
     */
    @Operation(summary = "Get authorization token for real-time speech recognition")
    @RequestMapping(value = "/rtasr-sign", method = RequestMethod.POST)
    @RateLimit
    public ApiResult<Object> rtasrSign() {
        // Get signature and other prerequisite parameters
        String ts = String.valueOf(System.currentTimeMillis() / 1000L);
        // Package return result
        Map<String, String> resultMap = new HashMap<>(6);
        resultMap.put("appid", appId);
        resultMap.put("ts", ts);
        resultMap.put("signa", getSign(ts, lfasrApikey, appId));
        resultMap.put("url", RTASR_URL);
        return ApiResult.success(resultMap);
    }

    public String getSign(String ts, String lfasrApikey, String appId) {
        try {
            String sign = CryptTools.hmacEncrypt(CryptTools.HMAC_SHA1, CryptTools.md5Encrypt(appId + ts), lfasrApikey);
            return URLEncoder.encode(sign, "UTF-8");
        } catch (Exception e) {
            log.error("Exception occurred while getting authorization token for real-time speech recognition", e);
        }
        return "";
    }

}
