package com.iflytek.astra.console.toolkit.tool;

import cn.hutool.core.io.FileUtil;
import cn.hutool.core.lang.Assert;
import cn.hutool.core.util.IdUtil;
import com.alibaba.fastjson2.*;

import com.iflytek.astra.console.commons.constant.ResponseEnum;
import com.iflytek.astra.console.commons.exception.BusinessException;
import com.iflytek.astra.console.toolkit.common.constant.CommonConst;
import com.iflytek.astra.console.toolkit.common.constant.LLMConstant;
import com.iflytek.astra.console.toolkit.entity.biz.modelconfig.*;
import com.iflytek.astra.console.toolkit.entity.botConfigProtocol.*;
import com.iflytek.astra.console.toolkit.mapper.ConfigInfoMapper;
import com.iflytek.astra.console.toolkit.util.*;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

import java.io.*;
import java.nio.file.Files;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Common utility tool providing various helper methods for the application
 */
@Slf4j
public class CommonTool {
    static ConfigInfoMapper configInfoMapper = SpringUtils.getBean(ConfigInfoMapper.class);


    /**
     * Generate session ID with snowflake algorithm
     *
     * @return Session ID string with "sws@" prefix
     */
    public static String genSid() {
        return "sws@".concat(IdUtil.getSnowflakeNextIdStr());
    }

    /**
     * Extract raw filename without extension
     *
     * @param filename The full filename with extension
     * @return Filename without extension, or null if input is null
     */
    public static String getFileRawName(String filename) {
        if (filename == null) {
            return null;
        }
        return filename.replace("." + FileUtil.getSuffix(filename), "");
    }


    /**
     * Print error response for debugging purposes
     *
     * @param resp The response string to check and log
     */
    public static void printErrResp(String resp) {
        log.debug("resp = {}", resp);
        try {
            JSONObject jsonObject = JSON.parseObject(resp);
            if (jsonObject.getInteger("code") != 0) {
                log.error("resp code not 0, resp = {}", resp);
            }
        } catch (JSONException je) {
            log.error("resp parse to json err, resp = {}", resp);
        }
    }

    /**
     * Check system call response and throw exception if failed
     *
     * @param resp The response string to validate
     * @return The data object from response if successful
     * @throws BusinessException if response code is not 0
     */
    public static Object checkSystemCallResponse(String resp) {
        JSONObject jsonObject = JSON.parseObject(resp);
        if (jsonObject.getInteger("code") != 0) {
            throw new BusinessException(ResponseEnum.RESPONSE_FAILED, jsonObject.getInteger("code"), jsonObject.getString("message"));
        }
        return jsonObject.get("data");
    }

    public static ModelConfigProtocolDto getModelConfig(String s) {
        return JSON.parseObject(s).getObject("modelConfig", ModelConfigProtocolDto.class);
    }

    public static ModelConfigProtocolDto getModelConfig(JSONObject jsonObject) {
        return jsonObject.getObject("modelConfig", ModelConfigProtocolDto.class);
    }

    public static List<String> getToolIds(List<Tool> tools) {
        if (tools.isEmpty()) {
            return new ArrayList<>();
        }
        return tools.stream().map(Tool::getToolId).collect(Collectors.toList());
    }

    public static List<String> getFlowIds(List<Flow> tools) {
        if (tools.isEmpty()) {
            return new ArrayList<>();
        }
        return tools.stream().map(Flow::getFlowId).collect(Collectors.toList());
    }

    public static void checkModelConfig(ModelConfigProtocolDto config) {
        Model plan = config.getModels().getPlan();
        Assert.notNull(plan.getLlmId());
        Assert.notBlank(plan.getApi());
        Assert.notBlank(plan.getDomain());
        Assert.notBlank(plan.getServiceId());
        Assert.notEmpty(plan.getPatchId());

        Model summary = config.getModels().getSummary();
        Assert.notNull(summary.getLlmId());
        Assert.notBlank(summary.getApi());
        Assert.notBlank(summary.getDomain());
        Assert.notBlank(summary.getServiceId());
        Assert.notEmpty(summary.getPatchId());
    }

    @Deprecated
    public static BotConfigOld getBotConfigOld(String appId, String botId, ModelConfigProtocolDto protocolDto) {

        BotConfigOld botConfigOld = new BotConfigOld();
        botConfigOld.setAppId(appId);
        botConfigOld.setBotId(botId);

        // set model
        Model model = protocolDto.getModels().getSummary();
        botConfigOld.setLlm(llmCapMapper(model.getDomain()));
        // botConfigOld.setPrompt(protocolDto.getPrePrompt());
        botConfigOld.setDomain(model.getDomain());
        if (CollectionUtils.isNotEmpty(model.getPatchId())) {
            // botConfigOld.setPatchId(model.getPatchId().stream().map(Long::valueOf).collect(Collectors.toList()));
            botConfigOld.setPatchId(model.getPatchId());
        }

        // set llm params
        CompletionParams completionParams = model.getCompletionParams();
        botConfigOld.setTemperature(completionParams.getTemperature());
        botConfigOld.setMaxTokens(completionParams.getMaxTokens());
        botConfigOld.setTopP(completionParams.getTopK());

        // set repo params
        RepoConfigs repoConfigs = protocolDto.getRepoConfigs();
        Integer topK = repoConfigs.getTopK();
        if (topK != null) {
            botConfigOld.setTopK(topK);
        }
        Double scoreThreshold = repoConfigs.getScoreThreshold();
        if (scoreThreshold != null) {
            botConfigOld.setScore(scoreThreshold);
        }
        boolean suggestedQuestionsAfterAnswerEnabled = protocolDto.getSuggestedQuestionsAfterAnswer().getEnabled();
        if (suggestedQuestionsAfterAnswerEnabled) {
            botConfigOld.setIsCorrelation(1);
        }
        boolean retrieverResourceEnabled = protocolDto.getRetrieverResource().getEnabled();
        if (retrieverResourceEnabled) {
            botConfigOld.setIsLocation(1);
        }
        if (CollectionUtils.isNotEmpty(protocolDto.getTools())) {
            botConfigOld.setTools(CommonTool.getToolIds(protocolDto.getTools()));
        }
        if (CollectionUtils.isNotEmpty(protocolDto.getFlows())) {
            botConfigOld.setFlows(CommonTool.getFlowIds(protocolDto.getFlows()));
        }

        botConfigOld.setApiUrl(model.getApi());

        return botConfigOld;
    }

    private static String llmCapMapper(String llm) {
        if (llm == null) {
            log.warn("llm = null");
            return LLMConstant.DOMAIN_SPARK_1_5;
        }
        switch (llm) {
            case LLMConstant.DOMAIN_SPARK_3_0:
                return "spark_V3";
            case LLMConstant.DOMAIN_SPARK_3_5:
                return "spark_V3.5";
            default:
                return llm;
        }
    }

    private static String patchMapper(String domain) {
        switch (domain) {
            case LLMConstant.DOMAIN_SPARK_1_5:
                return "patch";
            case LLMConstant.DOMAIN_SPARK_3_0:
                return "patchv3";
            // case LLMConstant.DOMAIN_SPARK_3_5:
            // return "patchv3.5";
            default:
                return domain;
        }
    }

    public static String getMultipartFileInfoStr(MultipartFile file) {
        return new JSONObject()
                .fluentPut("OriginalFilename", file.getOriginalFilename())
                .fluentPut("Size", file.getSize())
                .fluentPut("Name", file.getName())
                .fluentPut("ContentType", file.getContentType())
                .fluentPut("Resource", new JSONObject()
                        .fluentPut("Filename", file.getResource().getFilename())
                        .fluentPut("Description", file.getResource().getDescription()))
                .toString();
    }

    public static MultipartFile getMultipartFile(File file) {
        try (InputStream input = Files.newInputStream(file.toPath())) {
            // Try to detect contentType, returning null if detection fails is also fine
            String contentType = Files.probeContentType(file.toPath());
            return new MockMultipartFile(
                    "file",
                    file.getName(),
                    contentType,
                    input);
        } catch (IOException e) {
            throw new IllegalArgumentException("Invalid file: " + e, e);
        }
    }

    public static String getAppTypeName(Integer appType) {
        switch (appType) {
            case CommonConst.ApplicationType.AGENT:
                return "Bot";
            case CommonConst.ApplicationType.WORKFLOW:
                return "Workflow";
            default:
                throw new IllegalArgumentException();
        }
    }

    public static String getWorkflowNodeType(String nodeId) {
        return nodeId.substring(0, nodeId.indexOf(":"));
    }

    public static void wsServiceExceptionThrow(WebSocketSession session, Throwable t) {
        try {
            if (t.getMessage() == null) {
                session.sendMessage(new TextMessage("Service is temporarily unavailable, please try again later~"));
            } else {
                session.sendMessage(new TextMessage(t.getMessage()));
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public static String threeSerialNum(Number i) {
        String s = String.valueOf(i);
        if (s.length() == 1) {
            return "00" + s;
        }
        if (s.length() == 2) {
            return "0" + s;
        }
        if (s.length() == 3) {
            return s;
        }
        return null;
    }

}
