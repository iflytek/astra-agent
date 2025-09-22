package com.iflytek.astra.console.toolkit.service.model;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.StrUtil;
import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.iflytek.astra.console.commons.constant.ResponseEnum;
import com.iflytek.astra.console.commons.entity.user.UserInfo;
import com.iflytek.astra.console.commons.entity.workflow.Workflow;
import com.iflytek.astra.console.commons.exception.BusinessException;
import com.iflytek.astra.console.commons.response.ApiResult;
import com.iflytek.astra.console.commons.util.space.SpaceInfoUtil;
import com.iflytek.astra.console.toolkit.common.constant.CommonConst;
import com.iflytek.astra.console.toolkit.entity.biz.modelconfig.Config;
import com.iflytek.astra.console.toolkit.entity.biz.modelconfig.LocalModelDto;
import com.iflytek.astra.console.toolkit.entity.biz.modelconfig.ModelDto;
import com.iflytek.astra.console.toolkit.entity.biz.modelconfig.ModelValidationRequest;
import com.iflytek.astra.console.toolkit.entity.biz.workflow.BizWorkflowData;
import com.iflytek.astra.console.toolkit.entity.biz.workflow.BizWorkflowNode;
import com.iflytek.astra.console.toolkit.entity.enumVo.ModelStatusEnum;
import com.iflytek.astra.console.toolkit.entity.table.ConfigInfo;
import com.iflytek.astra.console.toolkit.entity.table.model.Model;
import com.iflytek.astra.console.toolkit.entity.table.model.ModelCategory;
import com.iflytek.astra.console.toolkit.entity.table.model.ModelCommon;
import com.iflytek.astra.console.toolkit.entity.vo.CategoryTreeVO;
import com.iflytek.astra.console.toolkit.entity.vo.LLMInfoVo;
import com.iflytek.astra.console.toolkit.entity.vo.ModelCategoryReq;
import com.iflytek.astra.console.toolkit.entity.vo.model.ModelDeployVo;
import com.iflytek.astra.console.toolkit.entity.vo.model.ModelFileVo;
import com.iflytek.astra.console.toolkit.handler.LocalModelHandler;
import com.iflytek.astra.console.toolkit.handler.UserInfoManagerHandler;
import com.iflytek.astra.console.toolkit.mapper.ConfigInfoMapper;
import com.iflytek.astra.console.toolkit.mapper.bot.SparkBotMapper;
import com.iflytek.astra.console.toolkit.mapper.model.ModelMapper;
import com.iflytek.astra.console.toolkit.mapper.workflow.WorkflowMapper;
import com.iflytek.astra.console.toolkit.util.S3Util;
import com.iflytek.astra.console.toolkit.util.idata.RSAUtil;
import com.iflytek.astra.console.toolkit.util.ssrf.SsrfParamGuard;
import com.iflytek.astra.console.toolkit.util.ssrf.SsrfProperties;
import com.iflytek.astra.console.toolkit.util.ssrf.SsrfValidators;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.jetbrains.annotations.NotNull;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.RestTemplate;

import java.net.URL;
import java.security.interfaces.RSAPrivateKey;
import java.util.*;
import java.util.stream.Collectors;

import static java.util.stream.Collectors.toList;
import static java.util.stream.Collectors.toMap;

/**
 * Model Service
 * <p>
 * This service handles model-related operations including: - Model validation and configuration -
 * Model deployment and management - Model category binding and organization - Local model
 * deployment and status monitoring - Workflow integration with models - SSRF protection for model
 * URLs
 *
 * @author clliu19
 * @since 2025/4/11
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ModelService extends ServiceImpl<ModelMapper, Model> {
    private static final ObjectMapper MAPPER = new ObjectMapper();

    // Configuration Category / Code constants
    private static final String IP_CATEGORY = "NETWORK_SEGMENT_BLACK_LIST";
    private static final String CAT_MODEL_SECRET_KEY = "MODEL_SECRET_KEY";
    private static final String CODE_PRIVATE_KEY = "private_key";
    private static final String CODE_PUBLIC_KEY = "public_key";

    private static final String CAT_LLM_FILTER = "LLM_FILTER";
    private static final String CODE_FILTER_PLAN = "plan";
    private static final String CODE_FILTER_SUMMARY = "summary";

    private static final String CAT_LLM_WORKFLOW_FILTER = "LLM_WORKFLOW_FILTER";
    private static final String CAT_LLM_WORKFLOW_FILTER_PRE = "LLM_WORKFLOW_FILTER_PRE";
    private static final String CODE_SELF_MODEL = "self-model";

    private static final String CAT_SPECIAL_MODEL = "SPECIAL_MODEL";
    private static final String CAT_NODE_PREFIX_MODEL = "NODE_PREFIX_MODEL";
    private static final String CODE_NODE_SWITCH = "switch";

    private static final String CAT_IP_BLACKLIST = "NETWORK_SEGMENT_BLACK_LIST";

    private static final String CODE_XINGCHEN = "xingchen";
    private static final String NAME_MODEL_SQUARE = "model_square";

    private final ModelMapper mapper;
    private final LLMService llmService;
    private final ConfigInfoMapper configInfoMapper;
    private final RestTemplate restTemplate;
    private final S3Util s3UtilClient;
    private final WorkflowMapper workflowMapper;
    private final SparkBotMapper sparkBotMapper;
    private final ModelCategoryService modelCategoryService;
    private final ModelCommonService modelCommonService;
    private final LocalModelHandler modelHandler;

    // ======== Environment Variables ========
    @Value("${spring.profiles.active}")
    String env;


    @Transactional(rollbackFor = Exception.class)
    public String validateModel(ModelValidationRequest request) {
        // 1) Parse apiKey (use encrypted value from database when updating unchanged; otherwise decrypt)
        final String decryptedApiKey;
        if (request.getId() != null && Boolean.FALSE.equals(request.getApiKeyMasked())) {
            Model byId = this.getById(request.getId());
            if (byId == null) {
                throw new BusinessException(ResponseEnum.MODEL_NOT_EXIST);
            }
            decryptedApiKey = byId.getApiKey();
        } else {
            decryptedApiKey = decryptApiKey(request.getApiKey());
        }

        // 2) Construct/validate URL + request body/headers
        final String url = buildModelApiUrlNew(request.getEndpoint());
        final Map<String, Object> requestBody = buildValidationPayload(request.getDomain());
        final HttpHeaders headers = buildAuthHeaders(decryptedApiKey);

        try {
            String responseBody = doPostModelApi(url, requestBody, headers);
            if (isValidModelResponse(responseBody)) {
                log.info("Model validation passed, domain={}, endpoint={}", request.getDomain(), url);
                request.setApiKey(decryptedApiKey);
                request.setEndpoint(url);
                saveOrUpdateModel(request);
                return "Model validation passed";
            }
            throw new BusinessException(ResponseEnum.MODEL_NOT_COMPATIBLE_OPENAI);
        } catch (BusinessException e) {
            log.error("Model validation failed, url={}, err={}", url, e.getMessage(), e);
            throw e;
        } catch (HttpClientErrorException | HttpServerErrorException e) {
            log.error("Model interface call failed, url={}, http={}, body={}", url, e.getStatusCode(), e.getResponseBodyAsString(), e);
            throw new BusinessException(ResponseEnum.MODEL_APIKEY_ERROR);
        } catch (Exception e) {
            log.error("Model validation failed, url={}, err={}", url, e.getMessage(), e);
            throw new BusinessException(ResponseEnum.MODEL_CHECK_FAILED);
        }
    }


    private String decryptApiKey(String apiKey) {
        ConfigInfo modelSecretKey = configInfoMapper.selectOne(Wrappers.<ConfigInfo>lambdaQuery()
                .eq(ConfigInfo::getCategory, "MODEL_SECRET_KEY")
                .eq(ConfigInfo::getCode, "private_key")
                .eq(ConfigInfo::getIsValid, 1));
        if (modelSecretKey == null) {
            throw new BusinessException(ResponseEnum.MODEL_API_KEY_NOT_FOUND);
        }

        try {
            RSAPrivateKey privateKey = RSAUtil.loadPrivateKey(modelSecretKey.getValue());
            return RSAUtil.decryptByPrivateKeyBase64(apiKey, privateKey);
        } catch (Exception e) {
            log.error("Decrypt API Key failed", e);
            throw new BusinessException(ResponseEnum.MODEL_APIKEY_LOAD_ERROR);
        }
    }

    private Map<String, Object> buildValidationPayload(String modelDomain) {
        Map<String, Object> message = new HashMap<>();
        message.put("role", "user");
        message.put("content", "Hello!");

        Map<String, Object> payload = new HashMap<>();
        payload.put("model", modelDomain);
        payload.put("messages", Collections.singletonList(message));
        payload.put("stream", false);
        return payload;
    }

    private HttpHeaders buildAuthHeaders(String apiKey) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("Authorization", "Bearer " + apiKey);
        return headers;
    }

    /**
     * Construct and validate final model API address (SSRF defense).
     *
     * <p>
     * Rules: only http/https; remove userInfo; prohibit query/fragment; complete openai compatible
     * path; dual validation for entry and final URL.
     */
    private String buildModelApiUrlNew(String baseUrl) {
        try {
            // Read IP blacklist
            List<ConfigInfo> list = configInfoMapper.getListByCategory(CAT_IP_BLACKLIST);
            String rawBlacklist = (list != null && !list.isEmpty()) ? list.getFirst().getValue() : "";
            List<String> blacklist =
                    StrUtil.isBlank(rawBlacklist)
                            ? Collections.emptyList()
                            : Arrays.stream(rawBlacklist.split(","))
                            .map(String::trim)
                            .filter(StrUtil::isNotBlank)
                            .collect(toList());

            SsrfProperties ssrfProperties = new SsrfProperties();
            // Note: The underlying object field name is ipBlaklist (third-party spelling), maintain
            // compatibility
            ssrfProperties.setIpBlaklist(blacklist);

            // 0) Remove userInfo and normalize
            String stripped = SsrfValidators.stripUserInfo(baseUrl);
            URL normalized = SsrfValidators.normalize(stripped);

            // 1) Prohibit query/fragment
            if (normalized.getQuery() != null) {
                throw new BusinessException(ResponseEnum.MODEL_URL_CHECK_FAILED);
            }

            SsrfParamGuard guard = new SsrfParamGuard(ssrfProperties);

            // 2) Only do pre-validation on host segment
            String hostOnly =
                    normalized.getProtocol()
                            + "://"
                            + normalized.getHost()
                            + (normalized.getPort() != -1 ? (":" + normalized.getPort()) : "");
            guard.validateUrlParam(hostOnly);

            // 3) Path completion
            String path = Optional.ofNullable(normalized.getPath()).orElse("");
            String cleanedPath = path.replaceAll("/+$", "");
            String finalPath;
            if (!cleanedPath.matches(".*/chat/completions/?$")) {
                if (cleanedPath.matches(".*/v\\d+$")) {
                    finalPath = cleanedPath + "/chat/completions";
                } else {
                    finalPath = cleanedPath + "/v1/chat/completions";
                }
            } else {
                finalPath = cleanedPath;
            }

            // 4) Final URL validation again
            String finalUrl = hostOnly + (finalPath.startsWith("/") ? finalPath : "/" + finalPath);
            guard.validateUrlParam(finalUrl);
            return finalUrl;
        } catch (BusinessException e) {
            throw e;
        } catch (IllegalArgumentException e) {
            throw new BusinessException(ResponseEnum.MODEL_URL_CHECK_FAILED);
        } catch (Exception e) {
            log.error("model url check failed: {}", e.getMessage(), e);
            throw new BusinessException(ResponseEnum.MODEL_CHECK_FAILED);
        }
    }

    private String doPostModelApi(String url, Map<String, Object> body, HttpHeaders headers) {
        ResponseEntity<String> response =
                restTemplate.exchange(url, HttpMethod.POST, new HttpEntity<>(body, headers), String.class);
        return response.getBody();
    }

    private boolean isValidModelResponse(String responseBody) throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        JsonNode root = mapper.readTree(responseBody);
        log.info("Model interface response: {}", root.toString());
        return root.has("choices") && root.get("choices").isArray() && root.has("usage");
    }

    private void saveOrUpdateModel(ModelValidationRequest request) {
        final boolean isNew = (request.getId() == null);
        final Long spaceId = SpaceInfoUtil.getSpaceId();

        Model model;
        if (isNew) {
            // Duplicate name validation
            Model exist =
                    this.getOne(
                            new LambdaQueryWrapper<Model>()
                                    .eq(Model::getUid, request.getUid())
                                    .eq(Model::getName, request.getModelName())
                                    .eq(Model::getIsDeleted, 0));
            if (exist != null) {
                throw new BusinessException(ResponseEnum.MODEL_NAME_EXISTED);
            }
            model = new Model();
            model.setUid(request.getUid());
            model.setDomain(request.getDomain());
            model.setCreateTime(new Date());
        } else {
            model =
                    this.getOne(
                            new LambdaQueryWrapper<Model>()
                                    .eq(Model::getId, request.getId())
                                    .eq(Model::getUid, request.getUid())
                                    .eq(Model::getIsDeleted, 0));
            if (model == null) {
                throw new BusinessException(ResponseEnum.MODEL_NOT_EXIST);
            }

            // Handle workflow cleanup triggered by config deletion
            List<Config> existConfigs =
                    Optional.ofNullable(model.getConfig()).map(s -> JSON.parseArray(s, Config.class)).orElse(null);
            List<Config> updateConfigs = request.getConfig();
            Set<String> updateKeys =
                    Optional.ofNullable(updateConfigs)
                            .orElse(Collections.emptyList())
                            .stream()
                            .map(Config::getKey)
                            .collect(Collectors.toSet());
            List<Config> removedConfigs =
                    Optional.ofNullable(existConfigs)
                            .orElse(Collections.emptyList())
                            .stream()
                            .filter(cfg -> !updateKeys.contains(cfg.getKey()))
                            .collect(toList());
            if (!removedConfigs.isEmpty()) {
                log.info("Model ID={} following configs were deleted: {}", model.getId(), removedConfigs);
                checkParamWorkflow(model, removedConfigs);
            }

            // Exclude self from duplicate name validation
            Model exist =
                    this.getOne(
                            new LambdaQueryWrapper<Model>()
                                    .eq(Model::getUid, request.getUid())
                                    .eq(Model::getName, request.getModelName())
                                    .ne(Model::getId, request.getId())
                                    .eq(Model::getIsDeleted, 0));
            if (exist != null) {
                throw new BusinessException(ResponseEnum.MODEL_NAME_EXISTED);
            }

            // Check if domain/URL changed to update workflow nodes
            boolean needUpdateWorkflow =
                    !Objects.equals(model.getDomain(), request.getDomain())
                            || !Objects.equals(model.getUrl(), request.getEndpoint());
            if (needUpdateWorkflow) {
                updateNodeInfo(request);
            }
        }

        // Common fields
        model.setName(request.getModelName());
        model.setDomain(request.getDomain());
        model.setUrl(request.getEndpoint());
        model.setImageUrl(request.getIcon());
        model.setContent(request.getDescription());
        model.setDesc(request.getDescription());
        model.setTag(JSONArray.toJSONString(request.getTag()));
        model.setType(1);
        model.setApiKey(request.getApiKey());
        model.setColor(request.getColor());
        model.setConfig(
                Optional.ofNullable(request.getConfig()).map(JSON::toJSONString).orElse(null));
        model.setUpdateTime(new Date());

        if (isNew) {
            model.setSpaceId(spaceId);
            mapper.insert(model);
            log.info("New model added successfully, domain={}, uid={}", request.getDomain(), request.getUid());
        } else {
            mapper.updateById(model);
            log.info("Model updated successfully, domain={}, uid={}", request.getDomain(), request.getUid());
        }

        insertTagInfo(request, model);
    }

    private void insertTagInfo(ModelValidationRequest request, Model model) {
        // Assemble category request
        ModelCategoryReq req = Optional.ofNullable(request.getModelCategoryReq())
                .orElseGet(ModelCategoryReq::new);

        // Uniformly supplement ownership and bind model ID
        req.setOwnerUid(request.getUid());
        req.setModelId(model.getId());
        modelCategoryService.saveAll(req);
    }

    /**
     * When deleting configuration, clean up corresponding keys referenced in workflow.
     */
    private void checkParamWorkflow(Model model, List<Config> removedConfigs) {
        List<Workflow> workflows =
                workflowMapper.selectList(
                        new LambdaQueryWrapper<Workflow>()
                                .eq(Workflow::getUid, model.getUid())
                                .eq(Workflow::getDeleted, false));

        ConfigInfo selfModelConfig =
                configInfoMapper.getByCategoryAndCode(CAT_LLM_WORKFLOW_FILTER, CODE_SELF_MODEL);
        List<String> prefixAllowList =
                Arrays.asList(Optional.ofNullable(selfModelConfig)
                        .map(ConfigInfo::getValue)
                        .orElse("")
                        .split(","));

        for (Workflow workflow : workflows) {
            BizWorkflowData data = JSON.parseObject(workflow.getData(), BizWorkflowData.class);
            if (data == null || CollUtil.isEmpty(data.getNodes())) {
                continue;
            }

            boolean updated = false;
            for (BizWorkflowNode node : data.getNodes()) {
                String prefix = node.getId().split("::")[0];
                if (!prefixAllowList.contains(prefix)) {
                    continue;
                }
                JSONObject nodeParam = node.getData().getNodeParam();
                if (nodeParam == null) {
                    continue;
                }

                boolean matched =
                        Objects.equals(model.getDomain(), nodeParam.getString("domain"))
                                || Objects.equals(model.getDomain(), nodeParam.getString("serviceId"))
                                || Objects.equals(model.getUrl(), nodeParam.getString("url"))
                                || Objects.equals(model.getUrl(), nodeParam.getString("serviceId"));

                if ("agent".equals(prefix) && !matched) {
                    JSONObject modelConfig = nodeParam.getJSONObject("modelConfig");
                    matched =
                            modelConfig != null
                                    && (Objects.equals(model.getDomain(), modelConfig.getString("domain"))
                                    || Objects.equals(model.getUrl(), modelConfig.getString("api")));
                }

                if (!matched) {
                    continue;
                }

                JSONObject extraParams = nodeParam.getJSONObject("extraParams");
                if (extraParams == null) {
                    continue;
                }
                for (Config removed : removedConfigs) {
                    String key = removed.getKey();
                    if (extraParams.containsKey(key)) {
                        extraParams.remove(key);
                        updated = true;
                        log.info(
                                "workflowId={}, nodeId={}, remove model config key={}", workflow.getId(), node.getId(), key);
                    }
                }
            }

            if (updated) {
                workflow.setData(JSON.toJSONString(data));
                workflow.setUpdateTime(new Date());
                workflowMapper.updateById(workflow);
            }
        }
    }


    private void updateNodeInfo(ModelValidationRequest request) {
        List<Workflow> workflows =
                workflowMapper.selectList(
                        new LambdaQueryWrapper<Workflow>()
                                .eq(Workflow::getUid, request.getUid())
                                .eq(Workflow::getDeleted, false));

        ConfigInfo selfModelConfig =
                configInfoMapper.getByCategoryAndCode(CAT_LLM_WORKFLOW_FILTER, CODE_SELF_MODEL);
        List<String> prefixAllowList =
                Arrays.asList(Optional.ofNullable(selfModelConfig)
                        .map(ConfigInfo::getValue)
                        .orElse("")
                        .split(","));

        for (Workflow workflow : workflows) {
            BizWorkflowData data = JSON.parseObject(workflow.getData(), BizWorkflowData.class);
            if (data == null || CollUtil.isEmpty(data.getNodes())) {
                continue;
            }

            boolean changed = false;
            for (BizWorkflowNode node : data.getNodes()) {
                String prefix = node.getId().split("::")[0];
                if (!prefixAllowList.contains(prefix)) {
                    continue;
                }
                changed |=
                        updateNodeParam(node, prefix, request.getDomain(), request.getEndpoint());
            }

            if (changed) {
                workflow.setData(JSON.toJSONString(data));
                workflow.setUpdateTime(new Date());
                workflowMapper.updateById(workflow);
            }
        }
    }


    /**
     * Update node information
     *
     * @param node
     * @param prefix
     * @param domain
     * @param endpoint
     * @return
     */
    private boolean updateNodeParam(BizWorkflowNode node, String prefix, String domain, String endpoint) {
        JSONObject nodeParam = node.getData().getNodeParam();
        boolean changed = false;

        if ("agent".equals(prefix)) {
            JSONObject modelConfig = nodeParam.getJSONObject("modelConfig");
            if (!Objects.equals(nodeParam.getString("serviceId"), endpoint)) {
                nodeParam.put("serviceId", endpoint);
                changed = true;
            }
            if (!Objects.equals(modelConfig.getString("domain"), domain)) {
                modelConfig.put("domain", domain);
                changed = true;
            }
            if (!Objects.equals(modelConfig.getString("api"), endpoint)) {
                modelConfig.put("api", endpoint);
                changed = true;
            }
        } else {
            if (!Objects.equals(nodeParam.getString("url"), endpoint)) {
                nodeParam.put("url", endpoint);
                changed = true;
            }
            if (!Objects.equals(nodeParam.getString("domain"), domain)) {
                nodeParam.put("domain", domain);
                changed = true;
            }
            if (!Objects.equals(nodeParam.getString("serviceId"), domain)) {
                nodeParam.put("serviceId", domain);
                changed = true;
            }
        }

        return changed;
    }

    public ApiResult getConditionList(ModelDto dto, HttpServletRequest request) {
        boolean isScene = true;
        List<String> planFilter;
        List<String> summaryFilter;
        List<String> sceneFilter;

        ConfigInfo planFilterCfg = configInfoMapper.getByCategoryAndCode(CAT_LLM_FILTER, CODE_FILTER_PLAN);
        ConfigInfo summaryFilterCfg =
                configInfoMapper.getByCategoryAndCode(CAT_LLM_FILTER, CODE_FILTER_SUMMARY);
        if (planFilterCfg == null || summaryFilterCfg == null) {
            return ApiResult.error(ResponseEnum.FILTER_CONF_MISS);
        }

        LambdaQueryWrapper<ConfigInfo> lqw =
                Wrappers.lambdaQuery(ConfigInfo.class)
                        .eq(ConfigInfo::getCode, CODE_XINGCHEN)
                        .eq(ConfigInfo::getName, NAME_MODEL_SQUARE)
                        .eq(ConfigInfo::getIsValid, 1)
                        .eq(
                                ConfigInfo::getCategory,
                                "pre".equals(env) ? CAT_LLM_WORKFLOW_FILTER_PRE : CAT_LLM_WORKFLOW_FILTER);

        ConfigInfo llmSceneFilter = configInfoMapper.selectOne(lqw);
        sceneFilter =
                llmSceneFilter != null
                        ? StrUtil.split(llmSceneFilter.getValue(), ",")
                        : new ArrayList<>();

        planFilter = StrUtil.split(planFilterCfg.getValue(), ",");
        summaryFilter = StrUtil.split(summaryFilterCfg.getValue(), ",");

        List<LLMInfoVo> planSquareList = new ArrayList<>();
        List<LLMInfoVo> summarySquareList = new ArrayList<>();
        List<LLMInfoVo> sceneSquareList = new ArrayList<>();
        List<LLMInfoVo> ownerSquareList = new ArrayList<>();
        dealWithSelfModel(dto, ownerSquareList, null, null);

        // Public models
        llmService.getDataFromModelShelfList(sceneSquareList, sceneFilter, dto.getUid(), null);

        // Special models
        List<ConfigInfo> specialModelCfgs = configInfoMapper.getListByCategory(CAT_SPECIAL_MODEL);
        for (ConfigInfo cfg : specialModelCfgs) {
            LLMInfoVo vo = JSON.parseObject(cfg.getValue(), LLMInfoVo.class);
            if (vo == null) {
                continue;
            }
            if (isScene) {
                if (sceneFilter.contains(vo.getServiceId())) {
                    sceneSquareList.add(vo);
                }
            } else {
                if (planFilter.contains(vo.getServiceId())) {
                    planSquareList.add(vo);
                }
                if (summaryFilter.contains(vo.getServiceId())) {
                    summarySquareList.add(vo);
                }
            }
        }

        List<LLMInfoVo> merged = new ArrayList<>();
        if (dto.getType() == 0) {
            merged.addAll(planSquareList);
            merged.addAll(summarySquareList);
            merged.addAll(sceneSquareList);
            merged.addAll(ownerSquareList);
        } else if (dto.getType() == 1) {
            merged.addAll(planSquareList);
            merged.addAll(summarySquareList);
            merged.addAll(sceneSquareList);
        } else {
            merged.addAll(ownerSquareList);
        }

        if (StringUtils.isNotEmpty(dto.getName())) {
            merged =
                    merged.stream()
                            .filter(s -> StrUtil.contains(s.getName(), dto.getName()))
                            .collect(toList());
        }

        merged.sort(
                Comparator.comparing(
                                LLMInfoVo::getCreateTime, Comparator.nullsLast(Comparator.naturalOrder()))
                        .reversed());

        int start = Math.max(0, (dto.getPage() - 1) * dto.getPageSize());
        int end = Math.min(start + dto.getPageSize(), merged.size());
        List<LLMInfoVo> pagedResult = start >= end ? Collections.emptyList() : merged.subList(start, end);

        Page<LLMInfoVo> page = new Page<>();
        page.setRecords(pagedResult);
        page.setCurrent(dto.getPage());
        page.setTotal(merged.size());
        return ApiResult.success(page);
    }


    private void dealWithSelfModel(
            ModelDto dto, List<LLMInfoVo> ownerSquareList, String nameKeyword, Integer type) {
        LambdaQueryWrapper<Model> wrapper =
                new LambdaQueryWrapper<Model>().eq(Model::getIsDeleted, 0);

        if (StringUtils.isNotBlank(nameKeyword)) {
            wrapper.eq(Model::getName, nameKeyword);
        }
        if (type != null && type != 0) {
            wrapper.eq(Model::getType, type);
        }
        if (dto.getSpaceId() != null) {
            wrapper.eq(Model::getSpaceId, dto.getSpaceId());
        } else {
            wrapper.isNull(Model::getSpaceId);
            wrapper.eq(Model::getUid, dto.getUid());
        }

        List<Model> models = mapper.selectList(wrapper);
        if (CollUtil.isEmpty(models)) {
            return;
        }

        for (Model model : models) {
            LLMInfoVo vo = new LLMInfoVo();
            vo.setId(model.getId());
            vo.setName(model.getName());
            vo.setIcon(model.getImageUrl());
            vo.setLlmSource(0);
            vo.setUrl(model.getUrl());
            vo.setColor(model.getColor());
            vo.setServiceId(model.getDomain());
            vo.setDomain(model.getDomain());
            vo.setModelId(model.getId());
            vo.setDesc(model.getDesc());
            vo.setLlmId(LLMService.generate9DigitRandomFromId(model.getId()));
            vo.setAddress(s3UtilClient.getS3Prefix());
            vo.setCreateTime(model.getCreateTime());
            vo.setUpdateTime(model.getUpdateTime());
            vo.setEnabled(model.getEnable());
            vo.setCategoryTree(modelCategoryService.getTree(model.getId()));
            vo.setType(model.getType());
            ownerSquareList.add(vo);
        }
    }

    /**
     * Generate random llmId
     *
     * @param id
     * @return
     */
    public static long generate9DigitRandomFromId(long id) {
        int digitCount = 9;
        long min = (long) Math.pow(10, digitCount - 1);
        long max = (long) Math.pow(10, digitCount) - 1;
        // Use ID as seed
        Random random = new Random(id);
        long range = max - min + 1;
        long randomNumber = min + (Math.abs(random.nextLong()) % range);
        return randomNumber;
    }

    /**
     * Encode id
     *
     * @param id
     * @return
     */
    public static long encodeId(long id) {
        // Add fixed offset value, then XOR with a magic number
        long encoded = (id + 123456L) ^ 654321L;
        return encoded;
    }

    /**
     * Decode
     *
     * @param encodedId
     * @return
     */
    public static long decodeId(long encodedId) {
        // Decode: XOR first, then subtract offset
        long id = (encodedId ^ 654321L) - 123456L;
        return id;
    }

    /**
     * @param llmSource 1-shelf 2-fine-tuning 3-personal
     * @param modelId
     * @param request
     * @return
     */
    @SneakyThrows
    public ApiResult getDetail(Integer llmSource, Long modelId, HttpServletRequest request) {
        if (llmSource == 1) {
            // Public models
            LLMInfoVo modelListFromLLMShelfDetail = actionFromShelfDetail(modelId, request);
            return ApiResult.success(modelListFromLLMShelfDetail);
        } else if (llmSource == 2) {
            // Fine-tuning
            return ApiResult.success();
        } else {
            // Personal
            LLMInfoVo modelVo = actionWithSelfModel(modelId);
            return ApiResult.success(modelVo);
        }
    }

    private @NotNull LLMInfoVo actionWithSelfModel(Long modelId) {
        Model model = mapper.selectOne(new LambdaQueryWrapper<Model>().eq(Model::getId, modelId));
        LLMInfoVo vo = new LLMInfoVo();
        UserInfo userInfo = UserInfoManagerHandler.get();
        String apiKey = model.getApiKey();
        if (StringUtils.isNotBlank(apiKey) && apiKey.length() > 8) {
            // First 4 digits + asterisks + last 4 digits
            apiKey = apiKey.substring(0, 4) + "********" + apiKey.substring(apiKey.length() - 4);
        }
        vo.setName(model.getName());
        vo.setServiceId(model.getDomain());
        vo.setConfig(JSONArray.parseArray(model.getConfig()));
        vo.setApiKey(apiKey);
        vo.setLlmSource(0);
        vo.setAddress(s3UtilClient.getS3Prefix());
        BeanUtils.copyProperties(model, vo);
        vo.setUserName(userInfo.getUsername());
        vo.setLlmId(model.getId());
        vo.setUrl(model.getUrl());
        vo.setDomain(model.getDomain());
        vo.setModelId(model.getId());
        vo.setDesc(model.getDesc());
        vo.setCategoryTree(modelCategoryService.getTree(modelId));
        vo.setModelType(model.getSource());
        vo.setIcon(model.getImageUrl());
        vo.setCreateTime(model.getCreateTime());
        vo.setUpdateTime(model.getUpdateTime());
        return vo;
    }

    private @NotNull LLMInfoVo actionFromShelfDetail(Long modelId, HttpServletRequest request) {
        LLMInfoVo vo = new LLMInfoVo();
        ModelCommon modelCommon = modelCommonService.getById(modelId);
        if (modelCommon == null) {
            throw new BusinessException(ResponseEnum.MODEL_NOT_EXIST);
        }
        String domain = modelCommon.getDomain();
        if (domain == null) {
            domain = modelCommon.getServiceId();
        }
        BeanUtils.copyProperties(modelCommon, vo);
        vo.setLlmSource(CommonConst.LLM_SOURCE_SQUARE);
        vo.setLlmId(modelCommon.getId());
        vo.setModelId(modelCommon.getId());
        vo.setDomain(domain);
        vo.setPatchId("0");
        vo.setDesc(modelCommon.getDesc());
        vo.setCategoryTree(modelCommon.getCategoryTree());
        vo.setModelType(modelCommon.getSource());
        vo.setIcon(modelCommon.getUserAvatar());
        vo.setCreateTime(modelCommon.getCreateTime());
        vo.setUpdateTime(modelCommon.getUpdateTime());
        vo.setUserName(modelCommon.getUserName());
        vo.setUrl(modelCommon.getUrl());
        return vo;
    }

    public String getPublicKey() {
        ConfigInfo publicKey =
                configInfoMapper.selectOne(
                        new LambdaQueryWrapper<ConfigInfo>()
                                .eq(ConfigInfo::getCategory, CAT_MODEL_SECRET_KEY)
                                .eq(ConfigInfo::getCode, CODE_PUBLIC_KEY)
                                .eq(ConfigInfo::getIsValid, 1));
        return Optional.ofNullable(publicKey).map(ConfigInfo::getValue).orElse(null);
    }

    @Transactional(rollbackFor = Exception.class)
    public ApiResult checkAndDelete(Long modelId, HttpServletRequest request) {
        String uid = UserInfoManagerHandler.getUserId();
        Model model = this.getById(modelId);
        if (model == null) {
            throw new BusinessException(ResponseEnum.MODEL_NOT_EXIST);
        }
        if (!model.getUid().equals(uid)) {
            log.warn("Unauthorized deletion, uid={}, modelId={}", uid, modelId);
            throw new BusinessException(ResponseEnum.EXCEED_AUTHORITY);
        }

        checkWorkflowReference(uid, model);

        Integer modelCount = sparkBotMapper.checkDomainIsUsage(uid, model.getDomain());
        if (modelCount != null && modelCount > 0) {
            throw new BusinessException(ResponseEnum.MODEL_DELETE_FAILED_APPLY_AGENT);
        }

        boolean result;
        if (Objects.equals(model.getType(), 1)) {
            result = this.removeById(modelId);
        } else {
            result = this.removeById(modelId) && modelHandler.deleteModel(model.getRemark());
        }
        return ApiResult.success(result);
    }

    /**
     * Check if model is used by workflow applications
     *
     * @param uid
     * @param model
     */
    private void checkWorkflowReference(String uid, Model model) {
        LambdaQueryWrapper<Workflow> lqw =
                new LambdaQueryWrapper<Workflow>().eq(Workflow::getDeleted, false);

        Long spaceId = SpaceInfoUtil.getSpaceId();
        if (spaceId != null) {
            lqw.eq(Workflow::getSpaceId, spaceId);
        } else {
            lqw.eq(Workflow::getUid, uid);
        }

        long llmId = LLMService.generate9DigitRandomFromId(model.getId());
        List<Workflow> workflows = workflowMapper.selectList(lqw);

        ConfigInfo selfModelConfig =
                configInfoMapper.getByCategoryAndCode(CAT_LLM_WORKFLOW_FILTER, CODE_SELF_MODEL);
        List<String> prefixAllowList =
                Arrays.asList(Optional.ofNullable(selfModelConfig)
                        .map(ConfigInfo::getValue)
                        .orElse("")
                        .split(","));

        for (Workflow workflow : workflows) {
            BizWorkflowData data = JSON.parseObject(workflow.getData(), BizWorkflowData.class);
            if (data == null || CollUtil.isEmpty(data.getNodes())) {
                continue;
            }

            for (BizWorkflowNode node : data.getNodes()) {
                String prefix = node.getId().split("::")[0];
                if (!prefixAllowList.contains(prefix)) {
                    continue;
                }
                JSONObject nodeParam = node.getData().getNodeParam();
                if (nodeParam == null) {
                    continue;
                }
                if (Objects.equals(llmId, nodeParam.getLong("llmId"))) {
                    throw new BusinessException(ResponseEnum.MODEL_DELETE_FAILED_APPLY_WORKFLOW);
                }
            }
        }
    }


    public Boolean checkModelBase(Long llmId, String serviceId, String url, String uid, Long spaceId) {
        ModelDto modelDto = new ModelDto();
        modelDto.setPage(1);
        modelDto.setPageSize(999);
        modelDto.setType(0);
        modelDto.setFilter(0);
        modelDto.setSpaceId(spaceId);
        modelDto.setUid(uid);

        ApiResult<Page<LLMInfoVo>> conditionList = this.getList(modelDto, null);
        Page<LLMInfoVo> page = conditionList.data();
        List<LLMInfoVo> records = page.getRecords();

        Map<Long, LLMInfoVo> mapById =
                records.stream().collect(toMap(LLMInfoVo::getLlmId, v -> v, (a, b) -> a));

        if (!mapById.containsKey(llmId)) {
            return Boolean.FALSE;
        }
        LLMInfoVo vo = mapById.get(llmId);

        boolean matched =
                Objects.equals(vo.getServiceId(), serviceId) && Objects.equals(vo.getUrl(), url);
        if (!matched) {
            log.info(
                    "checkModelBase mismatch, llmId={}, expect serviceId/url=({}/{}) but got ({}/{})",
                    llmId, vo.getServiceId(), vo.getUrl(), serviceId, url);
        }
        return matched;
    }


    public List<CategoryTreeVO> getAllCategoryTree() {
        List<String> list = Arrays.asList("modelCategory", "languageSupport", "contextLengthTag", "modelScenario");
        List<CategoryTreeVO> allCategoryTree = modelCategoryService.getAllCategoryTree();
        return allCategoryTree.stream().filter(s -> list.contains(s.getKey())).collect(toList());
    }

    public ApiResult<Page<LLMInfoVo>> getList(ModelDto dto, HttpServletRequest request) {
        if (dto == null) {
            return ApiResult.error(ResponseEnum.PARAM_ERROR);
        }
        final int page = Math.max(1, Optional.ofNullable(dto.getPage()).orElse(1));
        final int pageSize = Optional.ofNullable(dto.getPageSize()).orElse(10);
        final int type = Optional.ofNullable(dto.getType()).orElse(0);
        final int filter = Optional.ofNullable(dto.getFilter()).orElse(0);
        final String nameKeyword = StrUtil.emptyToDefault(dto.getName(), null);

        final boolean needPublic = (type == 0) || (type == 1);
        final boolean needOwner = type != 1;

        final List<LLMInfoVo> publicList = new ArrayList<>();
        if (needPublic) {
            final List<String> sceneFilter = loadSceneFilterSafe();
            llmService.getDataFromModelShelfList(publicList, sceneFilter, dto.getUid(), nameKeyword);
        }

        final List<LLMInfoVo> ownerList = new ArrayList<>();
        dealWithSelfModel(dto, ownerList, nameKeyword, filter);
        List<LLMInfoVo> merged = new ArrayList<>();
        if (needPublic)
            merged.addAll(publicList);
        if (needOwner)
            merged.addAll(ownerList);

        merged.sort(
                Comparator.comparing(
                                LLMInfoVo::getCreateTime, Comparator.nullsLast(Comparator.naturalOrder()))
                        .reversed()
                        .thenComparing(v -> Optional.ofNullable(v.getId()).orElse(0L)));

        final int total = merged.size();
        final int from = Math.min((page - 1) * pageSize, total);
        final int to = Math.min(from + pageSize, total);
        final List<LLMInfoVo> pageRecords = from >= to ? Collections.emptyList() : merged.subList(from, to);

        Page<LLMInfoVo> mpPage = new Page<>();
        mpPage.setCurrent(page);
        mpPage.setSize(pageSize);
        mpPage.setTotal(total);
        mpPage.setRecords(pageRecords);
        return ApiResult.success(mpPage);
    }

    /**
     * Only called when public models are needed, avoiding unnecessary DB access
     */
    private List<String> loadSceneFilterSafe() {
        try {
            LambdaQueryWrapper<ConfigInfo> lqw =
                    Wrappers.lambdaQuery(ConfigInfo.class)
                            .eq(ConfigInfo::getCode, CODE_XINGCHEN)
                            .eq(ConfigInfo::getName, NAME_MODEL_SQUARE)
                            .eq(ConfigInfo::getIsValid, 1)
                            .eq(
                                    ConfigInfo::getCategory,
                                    "pre".equals(env) ? CAT_LLM_WORKFLOW_FILTER_PRE : CAT_LLM_WORKFLOW_FILTER);

            ConfigInfo cfg = configInfoMapper.selectOne(lqw);
            if (cfg == null || StrUtil.isBlank(cfg.getValue())) {
                return Collections.emptyList();
            }
            return StrUtil.split(cfg.getValue(), ",");
        } catch (Exception e) {
            log.warn("loadSceneFilterSafe() error: {}", e.getMessage(), e);
            return Collections.emptyList();
        }
    }

    public ApiResult switchModel(Long modelId, Integer llmSource, String option, HttpServletRequest request) {
        boolean enable = "on".equals(option);
        if (Objects.equals(llmSource, 2)) {
            // Fine-tuning
            llmService.switchFinetuneModel(modelId, enable);
            return ApiResult.success();
        }
        String uid = UserInfoManagerHandler.getUserId();
        Model model = this.getById(modelId);
        if (model == null) {
            throw new BusinessException(ResponseEnum.MODEL_NOT_EXIST);
        }
        if (!model.getUid().equals(uid)) {
            log.warn("Unauthorized switch, uid={}, modelId={}", uid, modelId);
            throw new BusinessException(ResponseEnum.EXCEED_AUTHORITY);
        }
        model.setEnable(enable);
        return ApiResult.success(this.updateById(model));
    }


    @Transactional(rollbackFor = Exception.class)
    public Object offShelfModel(Long llmId, String flowId, String serviceId) {
        // 0) Parameter validation
        if (llmId == null) {
            throw new BusinessException(ResponseEnum.RESPONSE_FAILED, "Invalid parameters: llmId/serviceId cannot be empty");
        }

        // 1) Calculate operable workflow set (only query necessary columns, reduce IO)
        LambdaQueryWrapper<Workflow> lqw = new LambdaQueryWrapper<Workflow>()
                .select(Workflow::getId, Workflow::getFlowId, Workflow::getData, Workflow::getUpdateTime, Workflow::getDeleted);
        if (StringUtils.isNotBlank(flowId)) {
            lqw.eq(Workflow::getFlowId, flowId);
        } else {
            // Only do replacement within workflows containing oldServiceId in data, avoid accidental damage
            lqw.like(Workflow::getData, serviceId);
        }
        lqw.eq(Workflow::getDeleted, false);
        List<Workflow> workflows = workflowMapper.selectList(lqw);
        if (CollUtil.isEmpty(workflows)) {
            throw new BusinessException(ResponseEnum.RESPONSE_FAILED, "Flow list data is empty");
        }

        ConfigInfo configInfo = configInfoMapper.getByCategoryAndCode("NODE_PREFIX_MODEL", "switch");
        String value = configInfo.getValue();

        // 2) Node prefix whitelist (prioritize configuration read, fallback to built-in)
        Set<String> nodePrefixAllow = new HashSet<>(Arrays.asList(value.split(",")));

        // 3) Traverse and precisely replace, only modify when actually "hits oldServiceId"
        List<Workflow> toUpdate = new ArrayList<>(workflows.size());
        int nodeTouched = 0;
        Map<Long, Integer> wfChangedCount = new HashMap<>();

        for (Workflow wf : workflows) {
            BizWorkflowData data;
            try {
                data = JSON.parseObject(wf.getData(), BizWorkflowData.class);
            } catch (Exception ex) {
                log.warn("Workflow parse failed, flowId={}, id={}, err={}", wf.getFlowId(), wf.getId(), ex.getMessage());
                continue;
            }
            if (data == null || CollUtil.isEmpty(data.getNodes())) {
                continue;
            }

            boolean changed = false;
            for (BizWorkflowNode node : data.getNodes()) {
                if (node == null || node.getId() == null || node.getData() == null) {
                    continue;
                }

                String prefix = node.getId().split("::")[0];
                if (!nodePrefixAllow.contains(prefix)) {
                    continue;
                }

                JSONObject nodeParam = node.getData().getNodeParam();
                if (nodeParam == null) {
                    continue;
                }
                // —— Only perform replacement when current node actually references oldServiceId ——
                boolean hitOld = Objects.equals(llmId, nodeParam.getLong("llmId"));

                if (!hitOld) {
                    continue;
                }

                // —— Replacement logic
                nodeParam.put("modelEnabled", false);
                changed = true;
                if (changed) {
                    wf.setData(JSON.toJSONString(data));
                    workflowMapper.updateById(wf);
                    nodeTouched++;
                    wfChangedCount.merge(wf.getId(), 1, Integer::sum);
                }
            }

            if (changed) {
                toUpdate.add(wf);
            }
        }

        if (toUpdate.isEmpty()) {
            // No nodes hit oldServiceId, no update needed
            log.info("offModel: No nodes hit oldServiceId={}, flowId={}, no update performed", serviceId, flowId);
            return ApiResult.success(Collections.singletonMap("updated", 0));
        }

        // 4) Batch update (only update workflows that have changed)
        // workflowService.updateBatchById(toUpdate);
        log.info("offModel: Batch replacement completed, flowsUpdated={}, nodesTouched={}, details={}",
                toUpdate.size(), nodeTouched, wfChangedCount);

        Map<String, Object> ret = new HashMap<>();
        ret.put("flowsUpdated", toUpdate.size());
        ret.put("nodesTouched", nodeTouched);
        // key=workflowId, value=number of hit nodes
        ret.put("flowChangedDetails", wfChangedCount);
        return ApiResult.success(ret);
    }

    /**
     * Add/edit local model
     *
     * @param dto
     * @return
     */
    @Transactional(rollbackFor = Exception.class)
    public Object localModel(LocalModelDto dto) {
        // 0) Parameter validation
        validateLocalModel(dto);

        final Long spaceId = SpaceInfoUtil.getSpaceId();
        final boolean isCreate = dto.getId() == null;

        // 1) Duplicate name validation
        ensureNoDuplicateName(dto, isCreate);

        // 2) Parse contextLength
        Integer contextLength = resolveContextLength(
                Optional.ofNullable(dto.getModelCategoryReq()).orElse(null));

        // 3) Assemble deployment parameters
        ModelDeployVo deployVo = buildDeployVo(dto, contextLength);

        // 4) Create new or edit load
        Model model = isCreate ? initNewModel(dto, spaceId) : loadForEdit(dto);

        // 5) Deploy and get serviceId (fail directly on error, don't save to database)
        String serviceId = deployModel(isCreate, deployVo, model.getRemark());

        // 6) Fill/update common fields
        fillCommonModelFields(model, dto, serviceId);

        // 7) Save to database (save/update)
        persistModel(model, isCreate);

        // 8) Category binding (requires model.id)
        bindCategory(dto, model);

        return Boolean.TRUE;
    }

    /*
     * -------------------- Split small methods (behavior consistent with original method)
     * --------------------
     */

    private void validateLocalModel(LocalModelDto dto) {
        if (dto == null || StrUtil.isBlank(dto.getModelName()) || StrUtil.isBlank(dto.getDomain())) {
            throw new BusinessException(ResponseEnum.PARAM_ERROR, "modelName/domain cannot be empty");
        }
    }

    private void ensureNoDuplicateName(LocalModelDto dto, boolean isCreate) {
        LambdaQueryWrapper<Model> dupLqw = Wrappers.<Model>lambdaQuery()
                .eq(Model::getUid, dto.getUid())
                .eq(Model::getName, dto.getModelName())
                .eq(Model::getIsDeleted, 0);
        if (!isCreate) {
            dupLqw.ne(Model::getId, dto.getId());
        }
        Model duplicated = this.getOne(dupLqw);
        if (duplicated != null) {
            throw new BusinessException(ResponseEnum.MODEL_NAME_EXISTED);
        }
    }

    private Integer resolveContextLength(ModelCategoryReq req) {
        if (req == null || req.getContextLengthSystemId() == null) {
            return null;
        }
        ModelCategory byId = modelCategoryService.getById(req.getContextLengthSystemId());
        if (byId == null || StrUtil.isBlank(byId.getName())) {
            return null;
        }
        // Compatible with "128k"/"32K"/"8192"
        String name = byId.getName().trim();
        String digits = name.toLowerCase().endsWith("k") ? name.substring(0, name.length() - 1) : name;
        try {
            return Integer.parseInt(digits);
        } catch (NumberFormatException ignore) {
            return null;
        }
    }

    private ModelDeployVo buildDeployVo(LocalModelDto dto, Integer contextLength) {
        ModelDeployVo deployVo = new ModelDeployVo();
        deployVo.setModelName(dto.getDomain());
        deployVo.setReplicaCount(dto.getReplicaCount());
        ModelDeployVo.ResourceRequirements res = new ModelDeployVo.ResourceRequirements();
        res.setAcceleratorCount(dto.getAcceleratorCount());
        deployVo.setResourceRequirements(res);
        if (contextLength != null) {
            deployVo.setContextLength(contextLength);
        }
        return deployVo;
    }

    private Model initNewModel(LocalModelDto dto, Long spaceId) {
        Model model = new Model();
        model.setCreateTime(new Date());
        model.setUid(dto.getUid());
        if (spaceId != null) {
            model.setSpaceId(spaceId);
        }
        model.setStatus(ModelStatusEnum.PENDING.getCode());
        return model;
    }

    private Model loadForEdit(LocalModelDto dto) {
        Model model = this.getById(dto.getId());
        if (model == null || Objects.equals(model.getIsDeleted(), 1)) {
            throw new BusinessException(ResponseEnum.MODEL_NOT_EXIST);
        }
        if (!Objects.equals(model.getUid(), dto.getUid())) {
            throw new BusinessException(ResponseEnum.EXCEED_AUTHORITY);
        }
        model.setStatus(ModelStatusEnum.PENDING.getCode());
        return model;
    }

    private String deployModel(boolean isCreate, ModelDeployVo deployVo, String oldServiceId) {
        return isCreate ? modelHandler.deployModel(deployVo)
                : modelHandler.deployModelUpdate(deployVo, oldServiceId);
    }

    private void fillCommonModelFields(Model model, LocalModelDto dto, String serviceId) {
        model.setName(dto.getModelName());
        model.setImageUrl(dto.getIcon());
        model.setContent(dto.getDescription());
        model.setDesc(dto.getDescription());
        model.setType(2);
        model.setDomain(dto.getDomain());
        model.setColor(dto.getColor());
        model.setUpdateTime(new Date());
        model.setRemark(serviceId);
    }

    private void persistModel(Model model, boolean isCreate) {
        boolean ok = isCreate ? this.save(model) : this.updateById(model);
        if (!ok) {
            throw new BusinessException(
                    ResponseEnum.RESPONSE_FAILED, isCreate ? "Failed to add model" : "Failed to update model");
        }
    }

    private void bindCategory(LocalModelDto dto, Model model) {
        ModelCategoryReq req = Optional.ofNullable(dto.getModelCategoryReq())
                .orElseGet(ModelCategoryReq::new);
        req.setOwnerUid(dto.getUid());
        req.setModelId(model.getId());
        modelCategoryService.saveAll(req);
    }

    /**
     * Get model file directory list
     *
     * @return
     */
    public Object localModelList() {
        List<ModelFileVo> localModelList = modelHandler.getLocalModelList();
        return localModelList;
    }

    /**
     * Get model file directory list
     *
     * @return
     */
    public void flushStatus(Model model) {
        String serviceId = model.getRemark();
        try {
            JSONObject ret = modelHandler.checkDeployStatus(serviceId);
            String status = ret.getString("status");
            String endpoint = ret.getString("endpoint");
            Integer codeByValue = ModelStatusEnum.getCodeByValue(status);
            model.setStatus(codeByValue);
            model.setUrl(endpoint);
            this.updateById(model);
        } catch (Exception ignore) {
            log.error("Failed to get model status:", ignore);
        }
    }

    @Transactional(rollbackFor = Exception.class)
    public int flushStatusBatch(String uid, List<Model> models) {
        if (models == null || models.isEmpty())
            return 0;

        List<Model> toUpdate = new ArrayList<>(models.size());

        for (Model model : models) {
            // Protection: only handle type=2
            if (model.getType() == null || model.getType() != 2)
                continue;

            String serviceId = model.getRemark();
            if (serviceId == null)
                continue;

            try {
                JSONObject ret = modelHandler.checkDeployStatus(serviceId);
                String statusStr = ret.getString("status");
                String endpoint = ret.getString("endpoint");
                Integer newCode = ModelStatusEnum.getCodeByValue(statusStr);

                boolean changed = false;
                if (!Objects.equals(model.getStatus(), newCode)) {
                    model.setStatus(newCode);
                    changed = true;
                }
                if (!Objects.equals(model.getUrl(), endpoint)) {
                    model.setUrl(endpoint);
                    changed = true;
                }
                if (changed) {
                    toUpdate.add(model);
                }
            } catch (Exception ex) {
                // Single model exception does not interrupt the whole
                log.warn("[flushStatusBatch] uid={}, modelId={}, serviceId={} check failed: {}",
                        uid, model.getId(), serviceId, ex.getMessage());
            }
        }

        if (toUpdate.isEmpty())
            return 0;

        // Batch save to database, default batch size 1000 (can be changed to updateBatchById(toUpdate,
        // 200))
        boolean ok = this.updateBatchById(toUpdate);
        if (!ok) {
            log.warn("[flushStatusBatch] uid={}, toUpdate={} updateBatchById returned false", uid, toUpdate.size());
        }
        return toUpdate.size();
    }
}
