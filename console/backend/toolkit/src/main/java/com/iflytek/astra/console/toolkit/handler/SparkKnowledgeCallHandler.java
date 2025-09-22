package com.iflytek.astra.console.toolkit.handler;

import cn.hutool.core.util.StrUtil;
import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import com.iflytek.astra.console.toolkit.config.properties.RepoAuthorizedConfig;
import com.iflytek.astra.console.toolkit.config.properties.ApiUrl;
import com.iflytek.astra.console.toolkit.entity.core.knowledge.SplitRequest;
import com.iflytek.astra.console.toolkit.entity.dto.RelatedDocDto;
import com.iflytek.astra.console.toolkit.util.OkHttpUtil;
import jakarta.annotation.Resource;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;

import java.util.List;

@Slf4j
public class SparkKnowledgeCallHandler {
    // global: system uses unified knowledge base
    @Resource
    private ApiUrl apiUrl;
    @Resource
    private RepoAuthorizedConfig repoAuthorizedConfig;

    @Value("${spring.profiles.active}")
    String env;

    /**
     * Document parsing and chunking
     *
     * @param datasetId
     * @return
     */

    public List<RelatedDocDto> sparkDeskRepoFileGet(String datasetId) {
        String url = "";
        if (StrUtil.equalsAny(env, "pre", "prod")) {
            url = "https://agent.xfyun.cn";
        } else {
            url = "http://dev-xinghuo.xfyun.cn";
        }

        url = url.concat("dataset/getDatasetFiles?datasetId=").concat(datasetId);
        log.info("sparkDeskRepoFileGet request url:{}", url);
        String resp = OkHttpUtil.get(url);
        JSONObject respObject = JSON.parseObject(resp);
        log.info("sparkDeskRepoFileGet response data:{}", resp);

        if (respObject.getBooleanValue("flag") && respObject.getInteger("code") == 0) {
            return JSON.parseArray(respObject.getString("data"), RelatedDocDto.class);
        }
        return null;
    }


    @Data
    public class KnowledgeResponse {
        Integer code;
        String sid;
        String message;
        Object data;
    }

    public KnowledgeResponse documentSplit(SplitRequest request) {
        String url = apiUrl.getKnowledgeUrl().concat("/v1/document/split");
        String reqBody = JSON.toJSONString(request);
        log.info("documentSplit url = {}, request = {}", url, reqBody);
        String post = OkHttpUtil.post(url, reqBody);
        log.info("documentSplit response = {}", post);
        return JSON.parseObject(post, KnowledgeResponse.class);
    }
}
