package com.iflytek.astron.console.toolkit.service.workflow;

import cn.hutool.core.collection.CollUtil;
import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.esotericsoftware.minlog.Log;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.iflytek.astron.console.commons.constant.ResponseEnum;
import com.iflytek.astron.console.commons.entity.workflow.Workflow;
import com.iflytek.astron.console.commons.exception.BusinessException;
import com.iflytek.astron.console.commons.response.ApiResult;
import com.iflytek.astron.console.commons.util.BotUtil;
import com.iflytek.astron.console.commons.util.space.SpaceInfoUtil;
import com.iflytek.astron.console.toolkit.config.properties.BizConfig;
import com.iflytek.astron.console.toolkit.config.properties.CommonConfig;
import com.iflytek.astron.console.toolkit.entity.biz.modelconfig.ModelDto;
import com.iflytek.astron.console.toolkit.entity.biz.workflow.BizWorkflowData;
import com.iflytek.astron.console.toolkit.entity.biz.workflow.BizWorkflowNode;
import com.iflytek.astron.console.toolkit.entity.biz.workflow.node.BizNodeData;
import com.iflytek.astron.console.toolkit.entity.dto.WorkflowReq;
import com.iflytek.astron.console.toolkit.entity.table.database.DbInfo;
import com.iflytek.astron.console.toolkit.entity.table.tool.ToolBox;
import com.iflytek.astron.console.toolkit.entity.vo.LLMInfoVo;
import com.iflytek.astron.console.toolkit.handler.UserInfoManagerHandler;
import com.iflytek.astron.console.toolkit.mapper.database.DbInfoMapper;
import com.iflytek.astron.console.toolkit.service.model.ModelService;
import com.iflytek.astron.console.toolkit.service.repo.RepoService;
import com.iflytek.astron.console.toolkit.service.tool.ToolBoxService;
import com.iflytek.astron.console.toolkit.tool.DataPermissionCheckTool;
import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletRequest;
import lombok.SneakyThrows;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.yaml.snakeyaml.DumperOptions;
import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.SafeConstructor;
import org.yaml.snakeyaml.representer.Representer;

import java.io.InputStream;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Workflow export/import service for handling YAML format workflow data exchange. Provides
 * functionality to export workflows as YAML files and import workflows from YAML. Handles data
 * cleaning, permission checks, and format conversions during import/export operations.
 *
 * @author clliu19
 * @since 2025/6/18 15:39
 */
@Service
public class WorkflowExportService {
    private static final ObjectMapper objectMapper = new ObjectMapper();

    static {
        objectMapper.setSerializationInclusion(JsonInclude.Include.NON_NULL);
        objectMapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
    }

    @Resource
    WorkflowService workflowService;
    @Resource
    ModelService modelService;
    @Autowired
    private BotUtil botUtil;
    @Resource
    BizConfig bizConfig;
    @Resource
    ToolBoxService toolBoxService;
    @Autowired
    DataPermissionCheckTool dataPermissionCheckTool;
    @Resource
    RepoService repoService;
    @Autowired
    DbInfoMapper dbInfoMapper;
    @Autowired
    CommonConfig commonConfig;

    /**
     * Export workflow data as YAML format.
     *
     * @param workflow Workflow to export
     * @param outputStream Output stream to write YAML data
     */
    public void exportWorkflowDataAsYaml(Workflow workflow, OutputStream outputStream) {
        // Permission check
        dataPermissionCheckTool.checkWorkflowVisible(workflow, SpaceInfoUtil.getSpaceId());
        // Prevent timestamp
        objectMapper.configure(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS, false);
        try {
            BizWorkflowData bizWorkflowData = JSON.parseObject(workflow.getData(), BizWorkflowData.class);
            // Only process nodes: remove private fields from each node.data.nodeParam
            if (bizWorkflowData != null) {
                List<BizWorkflowNode> nodes = bizWorkflowData.getNodes();
                for (BizWorkflowNode node : nodes) {
                    BizNodeData data = node.getData();
                    if (data != null) {
                        JSONObject param = data.getNodeParam();
                        // if (param != null) {
                        // param.keySet().removeIf(key ->
                        // key.equals("uid") || key.equals("appId") || key.equals("repoList")
                        // || key.equals("repoId") || key.equals("modelId")
                        // || key.equals("llmId") || key.equals("serviceId")
                        // );
                        // }
                    }
                }
            }
            Map<String, Object> meta = objectMapper.convertValue(workflow, Map.class);

            // Keep only whitelist fields
            List<String> allowedKeys = new ArrayList<>(Arrays.asList(
                    "name", "description", "avatarIcon", "avatarColor",
                    "edgeType", "category", "advancedConfig"));
            meta.keySet().removeIf(k -> !allowedKeys.contains(k));

            // Remove null value fields
            meta.entrySet().removeIf(e -> e.getValue() == null);

            // Add DSL version
            meta.put("dslVersion", "v1");
            Map<String, Object> yamlWrapper = new LinkedHashMap<>();
            yamlWrapper.put("flowMeta", meta);
            yamlWrapper.put("flowData", objectMapper.convertValue(bizWorkflowData, Map.class));

            // YAML dump configuration
            DumperOptions options = new DumperOptions();
            options.setDefaultFlowStyle(DumperOptions.FlowStyle.BLOCK);
            options.setPrettyFlow(true);
            options.setIndent(2);
            options.setDefaultScalarStyle(DumperOptions.ScalarStyle.PLAIN);

            LoaderOptions loaderOptions = new LoaderOptions();
            Representer representer = new Representer(options);
            representer.getPropertyUtils().setSkipMissingProperties(true);
            // Output
            Yaml yaml = new Yaml(new SafeConstructor(loaderOptions), representer, options, loaderOptions);
            yaml.dump(yamlWrapper, new OutputStreamWriter(outputStream, StandardCharsets.UTF_8));
        } catch (Exception e) {
            Log.error("Export YAML failed", e);
            throw new BusinessException(ResponseEnum.RESPONSE_FAILED, "Export YAML failed");
        }
    }

    /**
     * Import workflow from YAML format.
     *
     * @param inputStream Input stream containing YAML data
     * @param request HTTP request context
     * @return API result with imported workflow
     */
    @SneakyThrows
    public ApiResult importWorkflowFromYaml(InputStream inputStream, HttpServletRequest request) {
        LoaderOptions loaderOptions = new LoaderOptions();
        Yaml yaml = new Yaml(new SafeConstructor(loaderOptions));
        Map<String, Object> rootMap = yaml.load(inputStream);
        JSONObject root = new JSONObject(rootMap);

        if (root == null || !root.containsKey("flowMeta") || !root.containsKey("flowData")) {
            throw new BusinessException(ResponseEnum.WORKFLOW_DLS_UPLOAD_FAILED);
        }
        String uid = UserInfoManagerHandler.getUserId();

        Map<String, Object> meta = (Map<String, Object>) root.get("flowMeta");
        Map<String, Object> flow = (Map<String, Object>) root.get("flowData");

        // Build new Workflow entity
        Workflow wf = new Workflow();
        wf.setUid(uid);
        String name = (String) meta.get("name");
        String flowName = generateNameWithTimestamp(name);
        wf.setName(flowName);
        wf.setAppId(commonConfig.getAppId());
        wf.setDescription((String) meta.get("description"));
        wf.setAvatarIcon((String) meta.get("avatarIcon"));
        wf.setAvatarColor((String) meta.get("avatarColor"));
        wf.setEdgeType((String) meta.get("edgeType"));
        wf.setCategory(meta.get("category") != null ? (Integer) meta.get("category") : null);
        wf.setAdvancedConfig((String) meta.get("advancedConfig"));
        BizWorkflowData bizWorkflowData = objectMapper.convertValue(flow, BizWorkflowData.class);
        // Clear node private information
        cleanNodesForImport(bizWorkflowData, uid, request);
        String data = objectMapper.writeValueAsString(bizWorkflowData);
        wf.setData(data);
        // Call core system to get flowId
        WorkflowReq workflowReq = new WorkflowReq();
        workflowReq.setName(wf.getName());
        workflowReq.setDescription(wf.getDescription());
        workflowReq.setAppId(wf.getAppId());
        ApiResult<String> addResult = workflowService.callProtocolAdd(workflowReq);
        if (addResult.code() != 0) {
            return addResult;
        }
        wf.setCreateTime(new Date());
        wf.setUpdateTime(new Date());
        wf.setFlowId(addResult.data());
        if (wf.getSource() == null) {
            wf.setSource(0);
        }
        if (StringUtils.isBlank(wf.getAvatarColor())) {
            wf.setAvatarColor("#FFEAD5");
        }
        if (StringUtils.isBlank(wf.getAvatarIcon())) {
            wf.setAvatarIcon("icon/common/emojiitem_00_10@2x.png");
        }
        // Save
        Long spaceId = SpaceInfoUtil.getSpaceId();
        wf.setSpaceId(spaceId);
        workflowService.save(wf);
        // Sync to Spark database
        Integer botId = botUtil.syncToSparkDatabase(wf, UserInfoManagerHandler.getUserId(), spaceId);
        JSONObject jsonData = new JSONObject();
        jsonData.put("botId", botId);
        // Update botId
        wf.setExt(jsonData.toJSONString());
        workflowService.updateById(wf);
        return ApiResult.success(wf);
    }

    /**
     * Generate a short name with timestamp, ensuring total length doesn't exceed specified limit.
     *
     * @param baseName Original name
     * @return Generated name with timestamp
     */
    public static String generateNameWithTimestamp(String baseName) {
        if (baseName == null) {
            baseName = "workflow";
        }
        String timestamp = new SimpleDateFormat("yyyyMMddHHmmss").format(new Date());
        int allowedBaseLength = 20 - timestamp.length();

        if (baseName.length() > allowedBaseLength) {
            baseName = baseName.substring(0, allowedBaseLength);
        }

        return baseName + timestamp;
    }

    /**
     * Clean private information during workflow import.
     *
     * @param bizWorkflowData Workflow data to clean
     * @param uid User ID
     * @param request HTTP request context
     */
    public void cleanNodesForImport(BizWorkflowData bizWorkflowData, String uid, HttpServletRequest request) {
        List<BizWorkflowNode> nodes = bizWorkflowData.getNodes();
        ModelDto modelDto = new ModelDto();
        modelDto.setPage(1);
        modelDto.setPageSize(999);
        modelDto.setType(0);
        modelDto.setUid(uid);
        ApiResult<Page<LLMInfoVo>> conditionList = modelService.getConditionList(modelDto, request);
        Page<LLMInfoVo> page = conditionList.data();
        List<LLMInfoVo> records = page.getRecords();
        Set<Long> allowedLlmSet = records.stream().map(LLMInfoVo::getLlmId).collect(Collectors.toSet());
        for (BizWorkflowNode node : nodes) {
            BizNodeData data = node.getData();
            if (data == null || data.getNodeParam() == null)
                continue;
            JSONObject param = data.getNodeParam();
            String prefix = node.getId().split("::")[0];

            switch (prefix) {
                case "spark-llm":
                case "decision-making":
                case "extractor-parameter":
                case "question-answer":
                    cleanLlmNode(param, allowedLlmSet, uid);
                    break;
                case "plugin":
                    cleanPluginNode(param, uid, data);
                    break;
                case "flow":
                    cleanFlowNode(param, uid, data);
                    break;
                case "knowledge-base":
                case "knowledge-pro-base":
                    cleanKnowledgeNode(param, uid, allowedLlmSet, prefix);
                    break;
                case "agent":
                    cleanAgentNode(param, allowedLlmSet, request);
                    break;
                case "database":
                    // Database node
                    cleanDataBaseNode(param, request);
                    break;
                default:
                    break;
            }
        }
    }

    /**
     * Process database node during import.
     *
     * @param param Node parameters
     * @param request HTTP request context
     */
    private void cleanDataBaseNode(JSONObject param, HttpServletRequest request) {
        List<DbInfo> dbInfos = dbInfoMapper.selectList(new QueryWrapper<DbInfo>().lambda()
                .eq(DbInfo::getUid, UserInfoManagerHandler.getUserId())
                .eq(DbInfo::getDeleted, false)
                .orderByDesc(DbInfo::getCreateTime));
        if (CollUtil.isNotEmpty(dbInfos)) {
            Set<Long> collect = dbInfos.stream().map(DbInfo::getDbId).collect(Collectors.toSet());
            String dbId = param.getString("dbId");
            if (StringUtils.isNotBlank(dbId) && !collect.contains(Long.valueOf(dbId))) {
                param.remove("dbId");
                param.remove("sql");
            }
        } else {
            param.remove("dbId");
            param.remove("sql");
        }
    }

    /**
     * Process LLM (Large Language Model) node during import.
     *
     * @param param Node parameters
     * @param allowedLlmSet Set of allowed LLM IDs
     * @param uid User ID
     */
    private void cleanLlmNode(JSONObject param, Set<Long> allowedLlmSet, String uid) {
        String source = param.getString("source");
        Long paramUid = param.getLong("uid");
        Long llmId = param.getLong("llmId");

        // If it's openai and uid matches, allow it to pass
        if ("openai".equals(source) && Objects.equals(paramUid.toString(), uid)) {
            return;
        }

        // Other cases: if llmId is not included, clean all
        if (llmId == null || !allowedLlmSet.contains(llmId)) {
            removeLlmParamNew(param);
        }
    }

    /**
     * Process plugin/tool node during import.
     *
     * @param param Node parameters
     * @param uid User ID
     * @param data Node data
     */
    private void cleanPluginNode(JSONObject param, String uid,
            BizNodeData data) {
        String pluginId = param.getString("pluginId");
        ToolBox toolBox = toolBoxService.getOnly(new LambdaQueryWrapper<ToolBox>()
                .eq(ToolBox::getToolId, pluginId));
        if (toolBox == null || (!Boolean.TRUE.equals(toolBox.getIsPublic())
                && !Objects.equals(toolBox.getUserId(), String.valueOf(bizConfig.getAdminUid()))
                && !Objects.equals(toolBox.getUserId(), uid))) {
            param.remove("pluginId");
            param.remove("uid");
            data.setInputs(Collections.emptyList());
            data.setOutputs(Collections.emptyList());
        }
    }

    /**
     * Process workflow node during import.
     *
     * @param param Node parameters
     * @param uid User ID
     * @param data Node data
     */
    private void cleanFlowNode(JSONObject param, String uid, BizNodeData data) {
        String flowId = param.getString("flowId");
        if (flowId != null && !Objects.equals(param.getString("uid"), uid.toString())) {
            param.remove("flowId");
            param.remove("uid");
            data.setInputs(Collections.emptyList());
            data.setOutputs(Collections.emptyList());
        }
    }

    /**
     * Process knowledge base or knowledge base pro node during import.
     *
     * @param param Node parameters
     * @param uid User ID
     * @param allowedLlmSet Set of allowed LLM IDs
     * @param prefix Node type prefix
     */
    private void cleanKnowledgeNode(JSONObject param, String uid,
            Set<Long> allowedLlmSet, String prefix) {
        if ("knowledge-pro".equals(prefix)) {
            cleanLlmNode(param, allowedLlmSet, uid);
        }
        JSONArray repoList = param.getJSONArray("repoList");
        if (CollUtil.isEmpty(repoList)) {
            param.put("repoList", Collections.emptyList());
        } else {
            JSONObject repoObj = repoList.getJSONObject(0);
            if (!Objects.equals(repoObj.getString("userId"), uid.toString())) {
                param.put("repoList", Collections.emptyList());
            }
        }
    }

    /**
     * Process agent node during import.
     *
     * @param param Node parameters
     * @param allowedLlmSet Set of allowed LLM IDs
     * @param request HTTP request context
     */
    private void cleanAgentNode(JSONObject param,
            Set<Long> allowedLlmSet,
            HttpServletRequest request) {

        if (!allowedLlmSet.contains(param.getLong("llmId"))) {
            param.remove("serviceId");
            param.remove("llmId");
            JSONObject modelConfig = param.getJSONObject("modelConfig");
            modelConfig.remove("domain");
            modelConfig.remove("api");
            param.replace("modelConfig", modelConfig);
            param.remove("uid");
        }

        JSONObject plugin = param.getJSONObject("plugin");
        if (plugin == null)
            return;

        JSONArray toolsList = plugin.getJSONArray("toolsList");
        JSONArray knowledgeArray = plugin.getJSONArray("knowledge");

        if (CollUtil.isNotEmpty(knowledgeArray)) {
            Set<String> userRepos = repoService.list(1, 999, "", "create_time", request, "")
                    .getPageData()
                    .stream()
                    .map(r -> r.getCoreRepoId())
                    .collect(Collectors.toSet());

            boolean hasInvalidRepo = knowledgeArray.stream().anyMatch(o -> {
                JSONObject j = (JSONObject) o;
                JSONArray repoIds = j.getJSONObject("match").getJSONArray("repoIds");
                return repoIds.stream().anyMatch(r -> !userRepos.contains((String) r));
            });

            if (hasInvalidRepo) {
                plugin.put("knowledge", Collections.emptyList());
                if (toolsList != null) {
                    toolsList.removeIf(tool -> "knowledge".equals(((JSONObject) tool).getString("type")));
                }
            }
        }

        JSONArray tools = plugin.getJSONArray("tools");
        Set<String> toolSet = new HashSet<>();
        for (int i = 0; tools != null && i < tools.size(); i++) {
            String toolId = tools.getString(i);
            ToolBox toolBox = toolBoxService.getOnly(new LambdaQueryWrapper<ToolBox>()
                    .eq(ToolBox::getToolId, toolId));
            if (toolBox == null || (!toolBox.getIsPublic() && !Objects.equals(toolBox.getUserId(), bizConfig.getAdminUid()))) {
                tools.remove(i--);
                toolSet.add(toolId);
            }
        }

        if (toolsList != null && CollUtil.isNotEmpty(toolSet)) {
            toolsList.removeIf(tool -> {
                JSONObject toolJson = (tool instanceof JSONObject)
                        ? (JSONObject) tool
                        : new JSONObject((Map<String, Object>) tool);
                return "tool".equals(toolJson.getString("type"))
                        && toolSet.contains(toolJson.getString("toolId"));
            });
        }
    }

    private static void removeLlmParamNew(JSONObject nodeParam) {
        List<String> keys = Arrays.asList("domain", "serviceId", "maxTokens", "temperature",
                "topK", "llmId", "url", "uid", "patchId");
        keys.forEach(nodeParam::remove);
    }
}
