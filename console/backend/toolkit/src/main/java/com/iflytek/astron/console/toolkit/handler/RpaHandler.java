package com.iflytek.astron.console.toolkit.handler;

import com.alibaba.fastjson2.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

import com.iflytek.astron.console.commons.constant.ResponseEnum;
import com.iflytek.astron.console.commons.exception.BusinessException;
import com.iflytek.astron.console.toolkit.config.properties.ApiUrl;
import com.iflytek.astron.console.toolkit.util.OkHttpUtil;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Handler for interacting with RPA APIs.
 *
 * <p>
 * This class provides methods to query the RPA workflow list and handles HTTP calls, response
 * parsing, and error handling.
 * </p>
 *
 * @author clliu19
 * @date 2025/9/23 09:54
 */
@Component
@Slf4j
public class RpaHandler {
    @Resource
    private ApiUrl apiUrl;
    private static final String RPA_ROBOT_LIST = "/api/rpa-openapi/workflows/get";

    /**
     * Get RPA workflow list from downstream API.
     *
     * <p>
     * Performs parameter validation, constructs request URL, invokes HTTP call, parses the response,
     * and returns the workflow list data.
     * </p>
     *
     * @param pageNo page number (>= 1, default 1 if null)
     * @param pageSize page size (1~1000, default 20 if null; values outside the range will be trimmed)
     * @param key secret/token used to generate Bearer Token (must not be blank)
     * @return {@link JSONObject} containing workflow list data
     * @throws BusinessException if parameters are invalid, HTTP call fails, response parsing fails, or
     *         downstream returns a non-zero code
     */
    public JSONObject getRpaList(Integer pageNo, Integer pageSize, String key) {
        // 1) Validate and normalize parameters
        if (key == null || key.isBlank()) {
            throw new BusinessException(ResponseEnum.RESPONSE_FAILED, "Bearer key must not be blank");
        }
        int safePageNo = Math.max(1, Objects.requireNonNullElse(pageNo, 1));
        int safePageSize = Math.min(Math.max(Objects.requireNonNullElse(pageSize, 20), 1), 1000);

        final String base = apiUrl.getRpaUrl();
        if (base == null || base.isBlank()) {
            throw new BusinessException(ResponseEnum.RESPONSE_FAILED, "RPA base url is not configured");
        }

        // 2) Build request URL and headers
        final String url = String.format("%s%s?pageNo=%d&pageSize=%d", base, RPA_ROBOT_LIST, safePageNo, safePageSize);
        final Map<String, String> headers = Map.of(
                "Authorization", "Bearer " + key,
                "Accept", "application/json; charset=utf-8");

        log.info("getRpaList -> url: {}, headers: {}", url, headers);

        // 3) Call downstream API and parse response
        final String resp;
        try {
            resp = OkHttpUtil.get(url, headers);
        } catch (Exception httpEx) {
            log.warn("getRpaList http error, url: {}, ex: {}", url, httpEx.toString());
            throw new BusinessException(ResponseEnum.RESPONSE_FAILED, httpEx, "Failed to call RPA api");
        }

        log.debug("getRpaList <- raw response: {}", abbreviate(resp, 2000));

        try {
            JSONObject obj = JSON.parseObject(resp);
            String code = obj.getString("code");
            if ("0000".equals(code)) {
                JSONObject data = obj.getJSONObject("data");
                if (data == null) {
                    log.warn("getRpaList data is null, treat as empty list. resp: {}", abbreviate(resp, 1000));
                    return new JSONObject();
                }
                return data;
            }
            String message = obj.getString("message");
            throw new BusinessException(
                    ResponseEnum.RESPONSE_FAILED,
                    "RPA api returned non-zero code: " + code + ", message: " + message);
        } catch (BusinessException be) {
            throw be;
        } catch (Exception parseEx) {
            log.warn("getRpaList parse error, resp: {}", abbreviate(resp, 1000), parseEx);
            throw new BusinessException(ResponseEnum.RESPONSE_FAILED, parseEx, "Failed to parse RPA response");
        }
    }

    /**
     * Abbreviate a string when printing logs to avoid overly long log entries.
     *
     * <p>
     * If the input exceeds {@code max} bytes (UTF-8), it will be truncated with a suffix indicating the
     * original length.
     * </p>
     *
     * @param s input string
     * @param max maximum number of bytes to keep in logs
     * @return abbreviated string with suffix if truncated, otherwise the original string
     */
    private static String abbreviate(String s, int max) {
        if (s == null)
            return null;
        byte[] bytes = s.getBytes(StandardCharsets.UTF_8);
        if (bytes.length <= max)
            return s;
        // Try to truncate on character boundary
        String cut = new String(bytes, 0, max, StandardCharsets.UTF_8);
        return cut + "...(" + bytes.length + "B)";
    }
}
