package com.iflytek.astron.console.toolkit.handler;

import com.alibaba.fastjson2.*;
import com.iflytek.astron.console.commons.constant.ResponseEnum;
import com.iflytek.astron.console.commons.exception.BusinessException;
import com.iflytek.astron.console.toolkit.config.properties.ApiUrl;
import com.iflytek.astron.console.toolkit.entity.vo.model.ModelDeployVo;
import com.iflytek.astron.console.toolkit.entity.vo.model.ModelFileVo;
import com.iflytek.astron.console.toolkit.util.OkHttpUtil;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * @Author clliu19
 * @Date: 2025/9/13 14:15
 */
@Component
@Slf4j
public class LocalModelHandler {
    public static final String MODEL_FILE_LIST = "/api/v1/modserv/list";
    public static final String MODEL_DEPLOY = "/api/v1/modserv/deploy";
    public static final String MODEL_DEPLOY_OPTION = "/api/v1/modserv/";
    @Resource
    private ApiUrl apiUrl;

    /**
     * Get local model file list
     *
     * @return
     */
    public List<ModelFileVo> getLocalModelList() {
        String url = apiUrl.getLocalModel() + MODEL_FILE_LIST;
        log.info("getLocalModelList request url:{}", url);
        String resp = OkHttpUtil.get(url);
        log.info("getLocalModelList response data:{}", resp);
        try {
            JSONObject respObj = JSONObject.parseObject(resp);
            if (respObj.getInteger("code") == 0) {
                JSONArray data = respObj.getJSONArray("data");
                return JSON.parseArray(data.toJSONString(), ModelFileVo.class);
            } else {
                throw new BusinessException(ResponseEnum.RESPONSE_FAILED, "Failed to get model file list: " + respObj.getString("message"));
            }
        } catch (Exception e) {
            throw new BusinessException(ResponseEnum.RESPONSE_FAILED, "Failed to get model file list");
        }
    }

    /**
     * Publish/deploy model service
     *
     * @param deployVo
     * @return
     */
    public String deployModel(ModelDeployVo deployVo) {
        try {
            String url = apiUrl.getLocalModel() + MODEL_DEPLOY;
            log.info("deployModel request url:{}", url);
            String resp = OkHttpUtil.post(url, JSON.toJSONString(deployVo));
            log.info("deployModel response data:{}", resp);
            JSONObject respObj = JSONObject.parseObject(resp);
            if (respObj.getInteger("code") == 0) {
                JSONObject data = respObj.getJSONObject("data");
                return data.getString("serviceId");
            } else {
                throw new BusinessException(ResponseEnum.RESPONSE_FAILED, "Service deployment failed: " + respObj.getString("message"));
            }
        } catch (Exception e) {
            throw new BusinessException(ResponseEnum.RESPONSE_FAILED, "Service deployment failed");
        }
    }

    /**
     * Service deployment update
     *
     * @param deployVo
     * @param serviceId
     * @return
     */
    public String deployModelUpdate(ModelDeployVo deployVo, String serviceId) {
        String url = apiUrl.getLocalModel() + MODEL_DEPLOY_OPTION + serviceId;
        log.info("deployModelUpdate request url:{}", url);
        String resp = OkHttpUtil.put(url, JSON.toJSONString(deployVo));
        log.info("deployModelUpdate response data:{}", resp);
        try {
            JSONObject respObj = JSONObject.parseObject(resp);
            if (respObj.getInteger("code") == 0) {
                JSONObject data = respObj.getJSONObject("data");
                return data.getString("serviceId");
            } else {
                throw new BusinessException(ResponseEnum.RESPONSE_FAILED, "Service update deployment failed: " + respObj.getString("message"));
            }
        } catch (Exception e) {
            throw new BusinessException(ResponseEnum.RESPONSE_FAILED, "Service update deployment failed");
        }
    }

    /**
     * Get service deployment status
     *
     * @param serviceId
     * @return object { "serviceId": "xdeepseekv3-<UUID>", "modelName": "xdeepseekv3", "status":
     *         "running/pending/failed/initializing/notExsit/terminating", //
     *         running/blocked/failed/initializing/not exist/terminating "endpoint":
     *         "http://xxxx:xxxx/xx", // openai like endpoint "updateTime": "2025-09-01 14:30" }
     */
    public JSONObject checkDeployStatus(String serviceId) {
        String url = apiUrl.getLocalModel() + MODEL_DEPLOY_OPTION + serviceId;
        log.info("checkDeployStatus request url:{}", url);
        String resp = OkHttpUtil.get(url);
        log.info("checkDeployStatus response data:{}", resp);
        try {
            JSONObject respObj = JSONObject.parseObject(resp);
            if (respObj.getInteger("code") == 0) {
                return respObj.getJSONObject("data");
            } else {
                throw new BusinessException(ResponseEnum.RESPONSE_FAILED, "Failed to get service deployment status: " + respObj.getString("message"));
            }
        } catch (Exception e) {
            throw new BusinessException(ResponseEnum.RESPONSE_FAILED, "Failed to get service deployment status");
        }
    }

    /**
     * Delete model service
     *
     * @param serviceId
     * @return
     */
    public Boolean deleteModel(String serviceId) {
        String url = apiUrl.getLocalModel() + MODEL_DEPLOY_OPTION + serviceId;
        log.info("deleteModel request url:{}", url);
        String resp = OkHttpUtil.delete(url);
        log.info("deleteModel response data:{}", resp);
        try {
            JSONObject respObj = JSONObject.parseObject(resp);
            if (respObj.getInteger("code") == 0) {
                return true;
            } else {
                throw new BusinessException(ResponseEnum.RESPONSE_FAILED, "Failed to delete service: " + respObj.getString("message"));
            }
        } catch (Exception e) {
            throw new BusinessException(ResponseEnum.RESPONSE_FAILED, "Failed to delete service");
        }
    }



}
