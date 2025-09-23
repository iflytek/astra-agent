package com.iflytek.astron.console.toolkit.service.extra;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import com.iflytek.astron.console.commons.constant.ResponseEnum;
import com.iflytek.astron.console.commons.entity.workflow.CloneSynchronize;
import com.iflytek.astron.console.commons.exception.BusinessException;
import com.iflytek.astron.console.commons.service.workflow.WorkflowBotService;
import com.iflytek.astron.console.toolkit.config.properties.ApiUrl;
import com.iflytek.astron.console.toolkit.entity.common.FlagResponseEntity;
import com.iflytek.astron.console.toolkit.tool.OpenPlatformTool;
import com.iflytek.astron.console.toolkit.util.OkHttpUtil;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.*;

/**
 * Service for integrating with the Open Platform API Provides functionality for repository
 * management, workflow synchronization, and dataset operations with external platform services
 */
@Slf4j
@Service
public class OpenPlatformService {
    public static final String MAAS1024APP = "mass1024";

    @Resource
    ApiUrl apiUrl;
    @Autowired
    private WorkflowBotService botMassService;

    @Value("${xfyun.api.auth.secret}")
    String secret;


    /**
     * Synchronizes workflow cloning operation with the open platform Creates a copy of a workflow from
     * origin to current with proper synchronization
     *
     * @param uid User identifier for ownership and authorization
     * @param originId The source workflow ID to clone from
     * @param currentId The target workflow ID for the clone
     * @param flowId The workflow identifier for tracking
     * @param spaceId The space/workspace ID for organization
     * @return Synchronization result data from the platform
     * @throws BusinessException if synchronization fails
     */
    public Integer syncWorkflowClone(String uid, Long originId, Long currentId, String flowId, Long spaceId) {
        CloneSynchronize cloneSynchronize = new CloneSynchronize();
        cloneSynchronize.setUid(uid);
        cloneSynchronize.setFlowId(flowId);
        cloneSynchronize.setOriginId(originId.toString());
        cloneSynchronize.setCurrentId(currentId);
        cloneSynchronize.setSpaceId(spaceId);
        log.info("OpenPlatformService syncWorkflowClonereqBody = {}", cloneSynchronize);
        Integer botId = botMassService.massCopySynchronize(cloneSynchronize);
        log.info("OpenPlatformService syncWorkflowClone response = {}", botId);
        return botId;
    }

    /**
     * Synchronizes workflow updates with the open platform Updates workflow metadata including
     * description, prologue, and input examples
     *
     * @param id The unique identifier of the workflow to update
     * @param description Updated description text for the workflow
     * @param prologue Updated prologue/introduction text
     * @param inputExample List of example inputs for the workflow
     * @return Update result data from the platform
     * @throws BusinessException if the update synchronization fails
     */
    public Object syncWorkflowUpdate(Long id, String description, String prologue, List<String> inputExample) {
        String url = apiUrl.getOpenPlatform().concat("/workflow/updateSynchronize");

        Map<String, String> headers = buildHeader();
        String reqBody = new JSONObject()
                .fluentPut("massId", id)
                .fluentPut("botDesc", description)
                .fluentPut("prologue", prologue)
                .fluentPut("inputExample", inputExample)
                .toString();

        log.info("OpenPlatformService syncWorkflowUpdate, url = {}, headers = {}, reqBody = {}", url, headers, reqBody);
        String response = OkHttpUtil.post(url, headers, reqBody);
        log.info("OpenPlatformService syncWorkflowUpdate response = {}", response);
        FlagResponseEntity responseEntity = JSON.parseObject(response, FlagResponseEntity.class);
        if (responseEntity.getCode() != 0) {
            throw new BusinessException(ResponseEnum.RESPONSE_FAILED, responseEntity.getDesc());
        }
        return responseEntity.getData();
    }

    /**
     * Builds authentication headers for open platform API requests Generates timestamp and signature
     * for secure API communication
     *
     * @return Map containing authentication headers (timestamp, signature, appId)
     */
    private Map<String, String> buildHeader() {
        Map<String, String> headers = new HashMap<>();
        long timestamp = System.currentTimeMillis() / 1000;
        headers.put("timestamp", String.valueOf(timestamp));
        headers.put("signature", OpenPlatformTool.getSignature(MAAS1024APP, secret, timestamp));
        headers.put("appId", MAAS1024APP);

        return headers;
    }

}
