package com.iflytek.astron.console.toolkit.handler;

import com.alibaba.fastjson2.JSON;
import com.iflytek.astron.console.commons.constant.ResponseEnum;
import com.iflytek.astron.console.commons.exception.BusinessException;
import com.iflytek.astron.console.toolkit.common.constant.core.ToolErrorStatus;
import com.iflytek.astron.console.toolkit.config.properties.ApiUrl;
import com.iflytek.astron.console.toolkit.entity.tool.*;
import com.iflytek.astron.console.toolkit.util.OkHttpUtil;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;


@Component
@Slf4j
public class ToolServiceCallHandler {
    @Resource
    private ApiUrl apiUrl;

    private static final String TOOL_MANAGE_URL = "/api/v1/tools";
    private static final String TOOL_VERSIONS_URL = "/api/v1/tools/versions";

    public ToolProtocolDto toolRun(ToolProtocolDto req) {
        String url = apiUrl.getToolUrl() + TOOL_MANAGE_URL + "/http_run";
        log.info("toolRun request url:{}\ndata:{}", url, JSON.toJSONString(req));
        String resp = OkHttpUtil.post(url, JSON.toJSONString(req));
        log.info("toolRun response data:{}", resp);
        return JSON.parseObject(resp, ToolProtocolDto.class);
    }

    public ToolProtocolDto toolDebug(ToolDebugRequest req) {
        String url = apiUrl.getToolUrl() + TOOL_MANAGE_URL + "/tool_debug";
        log.info("toolDebug request url:{}\ndata:{}", url, JSON.toJSONString(req));
        String resp = OkHttpUtil.post(url, JSON.toJSONString(req));
        log.info("toolDebug response data:{}", resp);
        return JSON.parseObject(resp, ToolProtocolDto.class);
    }

    public void dealResult(ToolResp respData) {
        if (respData == null) {
            throw new BusinessException(ResponseEnum.COMMON_REMOTE_CALLER_FAILED);
        }
        if (respData.getCode() != 0) {
            String message = respData.getMessage();
            if (ToolErrorStatus.find(respData.getCode()) == null) {
                message = "The tool is temporarily unavailable, please try again later";
            }
            throw new BusinessException(ResponseEnum.RESPONSE_FAILED, message);
        }
    }


    public ToolResp toolCreate(ToolProtocolDto req) {
        String url = apiUrl.getToolUrl() + TOOL_VERSIONS_URL;
        log.info("toolCreate request url:{}\ndata:{}", url, JSON.toJSONString(req));
        String resp = OkHttpUtil.post(url, JSON.toJSONString(req));
        log.info("toolCreate response data:{}", resp);
        return JSON.parseObject(resp, ToolResp.class);
    }

    public ToolResp toolUpdate(ToolProtocolDto req) {
        String url = apiUrl.getToolUrl() + TOOL_VERSIONS_URL;
        log.info("toolAddVersion request url:{}\ndata:{}", url, JSON.toJSONString(req));
        String resp = OkHttpUtil.put(url, JSON.toJSONString(req));
        log.info("toolUpdate response data:{}", resp);
        return JSON.parseObject(resp, ToolResp.class);
    }

    public ToolResp toolDelete(String paramStr) {
        String url = apiUrl.getToolUrl() + TOOL_VERSIONS_URL + paramStr;
        log.info("toolDelete request url:{}", url);
        String resp = OkHttpUtil.delete(url);
        log.info("toolDelete response data:{}", resp);
        return JSON.parseObject(resp, ToolResp.class);
    }
}
