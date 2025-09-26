package com.iflytek.astron.console.toolkit.service.model;


import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.collection.CollectionUtil;
import cn.hutool.core.util.StrUtil;
import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import com.alibaba.fastjson2.TypeReference;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.iflytek.astron.console.commons.constant.ResponseEnum;
import com.iflytek.astron.console.commons.entity.workflow.Workflow;
import com.iflytek.astron.console.commons.exception.BusinessException;
import com.iflytek.astron.console.commons.response.ApiResult;
import com.iflytek.astron.console.commons.util.space.SpaceInfoUtil;
import com.iflytek.astron.console.toolkit.common.ResultStatus;
import com.iflytek.astron.console.toolkit.common.constant.CommonConst;
import com.iflytek.astron.console.toolkit.common.constant.WorkflowConst;
import com.iflytek.astron.console.toolkit.common.constant.http.CustomHeader;
import com.iflytek.astron.console.toolkit.entity.biz.modelconfig.Config;
import com.iflytek.astron.console.toolkit.entity.biz.workflow.BizWorkflowData;
import com.iflytek.astron.console.toolkit.entity.table.ConfigInfo;
import com.iflytek.astron.console.toolkit.entity.table.model.Model;
import com.iflytek.astron.console.toolkit.entity.table.model.ModelCommon;
import com.iflytek.astron.console.toolkit.entity.vo.LLMInfoVo;
import com.iflytek.astron.console.toolkit.handler.UserInfoManagerHandler;
import com.iflytek.astron.console.toolkit.mapper.ConfigInfoMapper;
import com.iflytek.astron.console.toolkit.mapper.model.ModelMapper;
import com.iflytek.astron.console.toolkit.mapper.workflow.WorkflowMapper;
import com.iflytek.astron.console.toolkit.tool.DataPermissionCheckTool;
import com.iflytek.astron.console.toolkit.util.S3Util;
import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.jetbrains.annotations.Nullable;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;

import java.util.*;

/**
 * Large Language Model Service
 * <p>
 * This service handles LLM (Large Language Model) related operations including: - Model
 * authorization and authentication management - LLM information retrieval and filtering - Model
 * configuration and validation - Workflow integration with models - Fine-tuning model management
 *
 * @author clliu19
 * @since 1.0.0
 */
@Slf4j
@Service
public class LLMService {

    @Resource
    ConfigInfoMapper configInfoMapper;


    @Resource
    DataPermissionCheckTool dataPermissionCheckTool;

    @Resource
    WorkflowMapper workflowMapper;

    @Resource
    RedisTemplate<String, Object> redisTemplate;
    @Resource
    ModelMapper modelMapper;
    @Resource
    ModelCommonService modelCommonService;

    @Value("${spring.profiles.active}")
    String env;

    public static final String MODEL_ENABLE_KEY = "data_cache:fineTuneServer_enable_";
    @Resource
    private S3Util s3UtilClient;

    /**
     * Get LLM authorization list based on scene and node type
     *
     * @param request HTTP servlet request
     * @param appId Application ID
     * @param scene Scene identifier (workflow, etc.)
     * @param nodeType Node type (agent, plan, summary, etc.)
     * @return Authorization list containing available models grouped by categories
     */
    public Object getLlmAuthList(HttpServletRequest request, String appId, String scene, String nodeType) {
        boolean isScene = StrUtil.isNotBlank(scene);
        String authSource = resolveAuthSource(request);

        // 1) Parse scene filtering (including pre-prod/production, agent special filtering, plan/summary
        // config validation for non-scene mode)
        SceneFilterResult filter = resolveSceneFilter(isScene, scene, nodeType, authSource);
        if (filter.error != null) {
            return filter.error;
        }

        // 2) Initialize return data structure
        List<Map<String, Object>> plan = new ArrayList<>();
        List<Map<String, Object>> summary = new ArrayList<>();
        List<Map<String, Object>> sceneList = new ArrayList<>();
        Map<String, Object> personalFt = new HashMap<>();
        Map<String, Object> sceneFt = new HashMap<>();

        List<LLMInfoVo> sceneFineTuneList = new ArrayList<>();
        List<LLMInfoVo> personalList = new ArrayList<>();

        String userId = UserInfoManagerHandler.getUserId();

        // 3) Custom models (my models/custom models)
        ConfigInfo selfModelConfig = configInfoMapper.getByCategoryAndCode("LLM_WORKFLOW_FILTER", "self-model");
        dealWithSelfModel(nodeType, selfModelConfig, userId, personalList);
        sceneFt.put("categoryName", "My Models");
        sceneFt.put("modelList", sceneFineTuneList);
        personalFt.put("categoryName", "Custom Models");
        personalFt.put("modelList", personalList);

        // 4) Public models (model marketplace)
        List<Map<String, Object>> builtSceneList = buildSceneList(filter.sceneFilter, userId, personalList);
        sceneList.addAll(builtSceneList);
        sceneList.add(sceneFt);
        // sceneList.add(personalFt);

        // 5) Return (consistent with original logic)
        if (isScene) {
            return CollectionUtil.zip(Collections.singletonList(scene), Collections.singletonList(sceneList));
        } else {
            return CollectionUtil.zip(Arrays.asList("plan", "summary"), Arrays.asList(plan, summary));
        }
    }


    private String resolveAuthSource(HttpServletRequest request) {
        String authSource = request.getHeader(CustomHeader.X_AUTH_SOURCE);
        return StringUtils.isBlank(authSource) ? CommonConst.Platform.IFLYAICLOUD : authSource;
    }

    private static final class SceneFilterResult {
        List<String> sceneFilter = new ArrayList<>();
        List<String> mcpModelFilter = Collections.emptyList(); // Reserved: agent mcp filtering
        Object error; // Assigned when ApiResult.error occurs
    }

    /**
     * Parse \"scene filtering\" and \"pre-production/production environment configuration\";\n *
     * Validate plan/summary filter configuration completeness in non-scene mode.
     */
    private SceneFilterResult resolveSceneFilter(boolean isScene, String scene, String nodeType, String authSource) {
        SceneFilterResult r = new SceneFilterResult();
        if (isScene) {
            if ("workflow".equals(scene)) {
                LambdaQueryWrapper<ConfigInfo> lqw = Wrappers.lambdaQuery(ConfigInfo.class)
                        .eq(ConfigInfo::getCode, authSource)
                        .eq(ConfigInfo::getName, String.valueOf(nodeType))
                        .eq(ConfigInfo::getIsValid, 1);
                if ("pre".equals(env)) {
                    lqw.eq(ConfigInfo::getCategory, "LLM_WORKFLOW_FILTER_PRE");
                    if ("agent".equals(nodeType)) {
                        ConfigInfo summaryFilterCfg = configInfoMapper.getByCategoryAndCode("LLM_FILTER_PRE", "summary_agent");
                        r.mcpModelFilter = StrUtil.split(summaryFilterCfg.getValue(), ",");
                    }
                } else {
                    lqw.eq(ConfigInfo::getCategory, "LLM_WORKFLOW_FILTER");
                    if ("agent".equals(nodeType)) {
                        ConfigInfo summaryFilterCfg = configInfoMapper.getByCategoryAndCode("LLM_FILTER", "summary_agent");
                        r.mcpModelFilter = StrUtil.split(summaryFilterCfg.getValue(), ",");
                    }
                }
                ConfigInfo llmSceneFilter = configInfoMapper.selectOne(lqw);
                r.sceneFilter = (llmSceneFilter == null) ? new ArrayList<>() : StrUtil.split(llmSceneFilter.getValue(), ",");
            } else {
                r.sceneFilter = new ArrayList<>();
            }
        } else {
            // Non-scene mode maintains original validation: plan/summary configuration must exist
            ConfigInfo planFilterCfg = configInfoMapper.getByCategoryAndCode("LLM_FILTER", "plan");
            ConfigInfo summaryFilterCfg = configInfoMapper.getByCategoryAndCode("LLM_FILTER", "summary");
            if (planFilterCfg == null || summaryFilterCfg == null) {
                r.error = ApiResult.error(ResultStatus.FILTER_CONF_MISS.getCode(), ResultStatus.FILTER_CONF_MISS.getMessage());
                return r;
            }
            r.sceneFilter = new ArrayList<>();
        }
        return r;
    }

    /**
     * Assemble \"model marketplace/custom model\" scene list (consistent with original logic).
     */
    private List<Map<String, Object>> buildSceneList(List<String> sceneFilter, String userId, List<LLMInfoVo> personalList) {
        Map<String, Object> sceneSq = new HashMap<>();
        Map<String, Object> personalSq = new HashMap<>();
        List<LLMInfoVo> sceneSquareList = new ArrayList<>();

        getDataFromModelShelfList(sceneSquareList, sceneFilter, userId, null);

        sceneSq.put("categoryName", "Model Marketplace");
        sceneSq.put("modelList", sceneSquareList);
        personalSq.put("categoryName", "Custom Models");
        personalSq.put("modelList", personalList);

        List<Map<String, Object>> sceneList = new ArrayList<>();
        sceneList.add(sceneSq);
        sceneList.add(personalSq);
        return sceneList;
    }

    private void dealWithSelfModel(String nodeType, ConfigInfo selfModelConfig, String userId, List<LLMInfoVo> personalList) {
        List<String> valueList = new ArrayList<>();
        if (selfModelConfig != null && StringUtils.isNotBlank(selfModelConfig.getValue())) {
            valueList = Arrays.asList(selfModelConfig.getValue().split(","));
        }
        if (CollUtil.isNotEmpty(valueList) && !valueList.contains(nodeType)) {
            return;
        }
        List<Model> models = modelMapper.selectList(new LambdaQueryWrapper<Model>()
                .eq(Model::getUid, userId)
                .eq(Model::getEnable, 1)
                .eq(Model::getIsDeleted, 0));
        for (Model model : models) {
            LLMInfoVo llmInfoVo = new LLMInfoVo();
            llmInfoVo.setId(model.getId());
            llmInfoVo.setLlmId(generate9DigitRandomFromId(model.getId()));
            llmInfoVo.setServiceId(model.getDomain());
            llmInfoVo.setUrl(model.getUrl());
            llmInfoVo.setName(model.getName());
            llmInfoVo.setAddress(s3UtilClient.getS3Prefix());
            llmInfoVo.setIcon(model.getImageUrl());
            llmInfoVo.setTag(JSONArray.parseArray(model.getTag(), String.class));
            llmInfoVo.setLlmSource(0);
            llmInfoVo.setDomain(model.getDomain());
            llmInfoVo.setConfig(model.getConfig());
            personalList.add(llmInfoVo);
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


    public Object getModelServerInfo(HttpServletRequest request, Long id, Integer llmSource) {
        if (llmSource == CommonConst.LLM_SOURCE_SQUARE) {
            ModelCommon byId = modelCommonService.getById(id);
            String config = byId.getConfig();
            return JSON.parseArray(config);
        } else {
            return ApiResult.error(-1, "llmSource invalid");
        }

    }


    private @Nullable Map<String, Boolean> getCatchModelMap(String enabledKey) {
        Map<String, Boolean> enabledMap = new HashMap<>();
        Object enabledCache = redisTemplate.opsForValue().get(enabledKey);
        if (enabledCache != null) {
            try {
                enabledMap = JSON.parseObject(
                        String.valueOf(enabledCache),
                        new TypeReference<Map<String, Boolean>>() {});
            } catch (Exception ex) {
                log.warn("enabledMap parse failed, will re-init. raw={}", enabledCache);
                enabledMap = new HashMap<>();
            }
        }
        return enabledMap;
    }

    public void getDataFromModelShelfList(List<LLMInfoVo> sceneSquareList, List<String> sceneFilter, String uid, String name) {
        List<ModelCommon> modelListFromLLMShelf = modelCommonService.getCommonModelList(uid, name);

        if (!CollectionUtils.isEmpty(modelListFromLLMShelf)) {
            for (ModelCommon modelCommon : modelListFromLLMShelf) {
                String domain = modelCommon.getDomain();
                if (domain == null) {
                    domain = modelCommon.getServiceId();
                }
                LLMInfoVo vo = new LLMInfoVo();
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
                ConfigInfo llmTag = configInfoMapper.selectOne(Wrappers.lambdaQuery(ConfigInfo.class)
                        .eq(ConfigInfo::getCategory, "LLM_TAG")
                        .eq(ConfigInfo::getCode, vo.getServiceId())
                        .eq(ConfigInfo::getIsValid, 1));
                if (llmTag != null) {
                    vo.setTag(JSON.parseArray(llmTag.getValue(), String.class));
                }

                vo.setUrl(modelCommon.getUrl());
                // Temporary handling for gemma model
                if (vo.getName().startsWith("gemma")) {
                    ConfigInfo gemmaUrl = configInfoMapper.getByCategoryAndCode("gemma", "url");
                    if (gemmaUrl != null) {
                        vo.setUrl(gemmaUrl.getValue());
                    }
                }
                vo.setStatus(CommonConst.AUTH_STATUS_AUTHED);
                if (CollUtil.isNotEmpty(sceneFilter)) {
                    if (sceneFilter.contains(vo.getServiceId())) {
                        sceneSquareList.add(vo);
                    }
                } else {
                    sceneSquareList.add(vo);
                }
            }
        }
    }

    private void personalModel(List<LLMInfoVo> sceneSquareList, List<String> sceneFilter) {
        List<ConfigInfo> specialModelCfgs = configInfoMapper.getListByCategory("PERSONAL_MODEL");
        for (ConfigInfo cfg : specialModelCfgs) {
            String specialModelInfo = cfg.getValue();
            LLMInfoVo llmInfoVo = JSON.parseObject(specialModelInfo, LLMInfoVo.class);
            if (sceneFilter.contains(llmInfoVo.getServiceId())) {
                sceneSquareList.add(llmInfoVo);
            }
        }
    }

    public Object getFlowUseList(String flowId) {
        Workflow workflow = workflowMapper.selectOne(Wrappers.lambdaQuery(Workflow.class).eq(Workflow::getFlowId, flowId));
        dataPermissionCheckTool.checkWorkflowBelong(workflow, SpaceInfoUtil.getSpaceId());

        String data = workflow.getData();
        if (data == null) {
            return ApiResult.error(ResultStatus.PROTOCOL_EMPTY.getCode(), ResultStatus.PROTOCOL_EMPTY.getMessage());
        }

        HashSet<String> domainSet = new HashSet<>();
        JSONArray array = new JSONArray();
        BizWorkflowData bizWorkflowData = JSON.parseObject(data, BizWorkflowData.class);
        bizWorkflowData.getNodes().forEach(n -> {
            if (StrUtil.startWithAny(n.getId(),
                    WorkflowConst.NodeType.SPARK_LLM,
                    WorkflowConst.NodeType.DECISION_MAKING,
                    WorkflowConst.NodeType.EXTRACTOR_PARAMETER)) {
                String domain = n.getData().getNodeParam().getString("domain");
                if (domainSet.contains(domain)) {
                    return;
                }
                domainSet.add(domain);
                String serviceId = n.getData().getNodeParam().getString("serviceId");
                String patchId = n.getData().getNodeParam().getString("patchId");
                if ("0".equals(patchId)) {
                    patchId = null;
                }
                array.add(new JSONObject()
                        .fluentPut("domain", domain)
                        .fluentPut("channel", serviceId)
                        .fluentPut("patchId", patchId));
            }
        });

        return array;
    }

    public Object selfModelConfig(Long id, Integer llmSource) {
        if (llmSource != 0) {
            throw new BusinessException(ResponseEnum.NOT_CUSTOM_MODEL);
        }
        String uid = UserInfoManagerHandler.getUserId();

        Model one = modelMapper.selectOne(new LambdaQueryWrapper<Model>().eq(Model::getId, id));
        if (one == null) {
            throw new BusinessException(ResponseEnum.MODEL_NOT_EXIST);
        }
        if (!Objects.equals(uid, one.getUid())) {
            throw new BusinessException(ResponseEnum.EXCEED_AUTHORITY);
        }
        List<Config> configs = JSON.parseArray(one.getConfig(), Config.class);
        if (CollUtil.isNotEmpty(configs)) {
            for (Config config : configs) {
                Float precision = config.getPrecision();
                if (precision != null) {
                    // If precision is an integer greater than 1, convert to decimal form, e.g., 1 to 0.1, 2 to 0.01,
                    // etc.
                    int intPrec = Math.round(precision);
                    if (precision >= 1 && Math.abs(precision - intPrec) < 1e-6) {
                        float newPrec = 1f / (float) Math.pow(10, intPrec);
                        config.setPrecision(newPrec);
                    }
                }
            }
        }
        return ApiResult.success(configs);
    }

    /**
     * Whether to enable fine-tuning model
     *
     * @param modelId
     * @param enable
     */
    public void switchFinetuneModel(Long modelId, Boolean enable) {
        final String enabledKey = MODEL_ENABLE_KEY.concat(UserInfoManagerHandler.getUserId());
        Map<String, Boolean> catchModelMap = getCatchModelMap(enabledKey);
        if (catchModelMap == null) {
            catchModelMap = new HashMap<>();
        }
        final String key = String.valueOf(modelId);
        Boolean exists = catchModelMap.get(key);
        if (exists == null) {
            throw new BusinessException(ResponseEnum.RESPONSE_FAILED, "Fine-tuning model does not exist");
        } else {
            catchModelMap.put(modelId.toString(), enable);
            redisTemplate.opsForValue().set(enabledKey, JSON.toJSONString(catchModelMap));
        }
    }
}
