package com.iflytek.astra.console.toolkit.handler;

import com.alibaba.fastjson2.JSON;
import com.iflytek.astra.console.toolkit.config.properties.RepoAuthorizedConfig;
import com.iflytek.astra.console.toolkit.config.properties.ApiUrl;
import com.iflytek.astra.console.toolkit.entity.core.knowledge.*;
import com.iflytek.astra.console.toolkit.tool.http.HttpAuthTool;
import com.iflytek.astra.console.toolkit.util.OkHttpUtil;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Component
@Slf4j
public class KnowledgeV2ServiceCallHandler {
    @Resource
    private ApiUrl apiUrl;
    @Resource
    private RepoAuthorizedConfig repoAuthorizedConfig;

    /**
     * Document parsing and chunking
     *
     * @param request
     * @return
     */
    public KnowledgeResponse documentSplit(SplitRequest request) {
        String url = apiUrl.getKnowledgeUrl().concat("/v1/document/split");
        String reqBody = JSON.toJSONString(request);
        log.info("documentSplit url = {}, request = {}", url, reqBody);
        String post = OkHttpUtil.post(url, reqBody);
        log.info("documentSplit response = {}", post);
        return JSON.parseObject(post, KnowledgeResponse.class);
    }

    public KnowledgeResponse saveChunk(KnowledgeRequest request) {
        String url = apiUrl.getKnowledgeUrl().concat("/v1/chunks/save");
        String reqBody = JSON.toJSONString(request);
        log.info("saveChunk url = {}, request = {}", url, reqBody);
        String post = OkHttpUtil.post(url, reqBody);
        log.info("saveChunk response = {}", post);
        return JSON.parseObject(post, KnowledgeResponse.class);
    }

    public KnowledgeResponse updateChunk(KnowledgeRequest request) {
        String url = apiUrl.getKnowledgeUrl().concat("/v1/chunk/update");
        String reqBody = JSON.toJSONString(request);
        log.info("updateChunk url = {}, request = {}", url, reqBody);
        String post = OkHttpUtil.post(url, reqBody);
        log.info("updateChunk response = {}", post);
        return JSON.parseObject(post, KnowledgeResponse.class);
    }

    public KnowledgeResponse deleteDocOrChunk(KnowledgeRequest request) {
        String url = apiUrl.getKnowledgeUrl().concat("/v1/chunk/delete");
        String reqBody = JSON.toJSONString(request);
        log.info("deleteDocOrChunk url = {}, request = {}", url, reqBody);
        String post = OkHttpUtil.post(url, reqBody);
        log.info("deleteDocOrChunk response = {}", post);
        return JSON.parseObject(post, KnowledgeResponse.class);
    }

    public KnowledgeResponse knowledgeQuery(QueryRequest request) {
        String url = apiUrl.getKnowledgeUrl().concat("/v1/chunk/query");
        url = HttpAuthTool.assembleRequestUrl(url, "POST", repoAuthorizedConfig.getApiKey(), repoAuthorizedConfig.getApiSecret());
        String reqBody = JSON.toJSONString(request);
        log.info("knowledgeQuery request url:{}\ndata:{}", url, reqBody);
        String respData = OkHttpUtil.post(url, reqBody);
        log.info("knowledgeQuery response data:{}", respData);
        return JSON.parseObject(respData, KnowledgeResponse.class);
    }
}
