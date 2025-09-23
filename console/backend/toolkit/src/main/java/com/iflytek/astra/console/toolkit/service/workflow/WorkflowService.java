package com.iflytek.astra.console.toolkit.service.workflow;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.collection.CollectionUtil;
import cn.hutool.core.date.DateUnit;
import cn.hutool.core.date.DateUtil;
import cn.hutool.core.lang.Assert;
import cn.hutool.core.util.ReUtil;
import cn.hutool.core.util.StrUtil;
import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import com.alibaba.fastjson2.TypeReference;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.iflytek.astra.console.commons.constant.ResponseEnum;
import com.iflytek.astra.console.commons.data.UserInfoDataService;
import com.iflytek.astra.console.commons.entity.bot.BotDetail;
import com.iflytek.astra.console.commons.entity.bot.BotMarketForm;
import com.iflytek.astra.console.commons.entity.bot.ChatBotBase;
import com.iflytek.astra.console.commons.entity.bot.UserLangChainInfo;
import com.iflytek.astra.console.commons.entity.user.UserInfo;
import com.iflytek.astra.console.commons.entity.workflow.Workflow;
import com.iflytek.astra.console.commons.exception.BusinessException;
import com.iflytek.astra.console.commons.mapper.UserLangChainInfoMapper;
import com.iflytek.astra.console.commons.mapper.bot.ChatBotBaseMapper;
import com.iflytek.astra.console.commons.response.ApiResult;
import com.iflytek.astra.console.commons.service.bot.BotMarketDataService;
import com.iflytek.astra.console.commons.util.RequestContextUtil;
import com.iflytek.astra.console.commons.util.SseEmitterUtil;
import com.iflytek.astra.console.commons.util.space.SpaceInfoUtil;
import com.iflytek.astra.console.toolkit.common.Result;
import com.iflytek.astra.console.toolkit.common.constant.CommonConst;
import com.iflytek.astra.console.toolkit.common.constant.WorkflowConst;
import com.iflytek.astra.console.toolkit.config.properties.ApiUrl;
import com.iflytek.astra.console.toolkit.config.properties.BizConfig;
import com.iflytek.astra.console.toolkit.config.properties.CommonConfig;
import com.iflytek.astra.console.toolkit.entity.biz.external.app.AkSk;
import com.iflytek.astra.console.toolkit.entity.biz.workflow.*;
import com.iflytek.astra.console.toolkit.entity.biz.workflow.channel.AiuiAgentInfo;
import com.iflytek.astra.console.toolkit.entity.biz.workflow.node.*;
import com.iflytek.astra.console.toolkit.entity.common.PageData;
import com.iflytek.astra.console.toolkit.entity.core.workflow.*;
import com.iflytek.astra.console.toolkit.entity.core.workflow.node.InputOutput;
import com.iflytek.astra.console.toolkit.entity.core.workflow.node.NodeData;
import com.iflytek.astra.console.toolkit.entity.core.workflow.node.Property;
import com.iflytek.astra.console.toolkit.entity.core.workflow.node.Schema;
import com.iflytek.astra.console.toolkit.entity.core.workflow.sse.ChatResponse;
import com.iflytek.astra.console.toolkit.entity.core.workflow.sse.ChatSysReq;
import com.iflytek.astra.console.toolkit.entity.dto.*;
import com.iflytek.astra.console.toolkit.entity.dto.eval.NodeSimpleDto;
import com.iflytek.astra.console.toolkit.entity.dto.eval.WorkflowComparisonSaveReq;
import com.iflytek.astra.console.toolkit.entity.table.ConfigInfo;
import com.iflytek.astra.console.toolkit.entity.table.database.DbTable;
import com.iflytek.astra.console.toolkit.entity.table.eval.EvalSet;
import com.iflytek.astra.console.toolkit.entity.table.eval.EvalSetVer;
import com.iflytek.astra.console.toolkit.entity.table.eval.EvalSetVerData;
import com.iflytek.astra.console.toolkit.entity.table.model.Model;
import com.iflytek.astra.console.toolkit.entity.table.relation.FlowDbRel;
import com.iflytek.astra.console.toolkit.entity.table.relation.FlowRepoRel;
import com.iflytek.astra.console.toolkit.entity.table.relation.FlowToolRel;
import com.iflytek.astra.console.toolkit.entity.table.repo.FileInfoV2;
import com.iflytek.astra.console.toolkit.entity.table.tool.ToolBox;
import com.iflytek.astra.console.toolkit.entity.table.tool.ToolBoxOperateHistory;
import com.iflytek.astra.console.toolkit.entity.table.workflow.*;
import com.iflytek.astra.console.toolkit.entity.tool.McpServerTool;
import com.iflytek.astra.console.toolkit.entity.vo.*;
import com.iflytek.astra.console.toolkit.entity.vo.eval.EvalSetVerDataVo;
import com.iflytek.astra.console.toolkit.handler.McpServerHandler;
import com.iflytek.astra.console.toolkit.handler.UserInfoManagerHandler;
import com.iflytek.astra.console.toolkit.mapper.ConfigInfoMapper;
import com.iflytek.astra.console.toolkit.mapper.database.DbTableMapper;
import com.iflytek.astra.console.toolkit.mapper.eval.EvalSetMapper;
import com.iflytek.astra.console.toolkit.mapper.eval.EvalSetVerDataMapper;
import com.iflytek.astra.console.toolkit.mapper.eval.EvalSetVerMapper;
import com.iflytek.astra.console.toolkit.mapper.relation.FlowDbRelMapper;
import com.iflytek.astra.console.toolkit.mapper.relation.FlowRepoRelMapper;
import com.iflytek.astra.console.toolkit.mapper.relation.FlowToolRelMapper;
import com.iflytek.astra.console.toolkit.mapper.repo.FileInfoV2Mapper;
import com.iflytek.astra.console.toolkit.mapper.tool.ToolBoxMapper;
import com.iflytek.astra.console.toolkit.mapper.tool.ToolBoxOperateHistoryMapper;
import com.iflytek.astra.console.toolkit.mapper.trace.ChatInfoMapper;
import com.iflytek.astra.console.toolkit.mapper.trace.NodeInfoMapper;
import com.iflytek.astra.console.toolkit.mapper.workflow.*;
import com.iflytek.astra.console.toolkit.service.extra.AppService;
import com.iflytek.astra.console.toolkit.service.extra.CoreSystemService;
import com.iflytek.astra.console.toolkit.service.extra.OpenPlatformService;
import com.iflytek.astra.console.toolkit.service.model.ModelService;
import com.iflytek.astra.console.toolkit.sse.WorkflowSseEventSourceListener;
import com.iflytek.astra.console.toolkit.tool.DataPermissionCheckTool;
import com.iflytek.astra.console.toolkit.tool.JsonConverter;
import com.iflytek.astra.console.toolkit.tool.MyThreadTool;
import com.iflytek.astra.console.toolkit.util.JacksonUtil;
import com.iflytek.astra.console.toolkit.util.OkHttpUtil;
import com.iflytek.astra.console.toolkit.util.RedisUtil;
import com.iflytek.astra.console.toolkit.util.S3Util;
import com.iflytek.astra.console.toolkit.util.ssrf.SsrfParamGuard;
import com.iflytek.astra.console.toolkit.util.ssrf.SsrfProperties;
import com.iflytek.astra.console.toolkit.util.ssrf.SsrfValidators;
import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import net.sf.jsqlparser.parser.CCJSqlParserUtil;
import net.sf.jsqlparser.statement.Statement;
import net.sf.jsqlparser.util.TablesNamesFinder;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.internal.Util;
import okhttp3.internal.sse.RealEventSource;
import okhttp3.sse.EventSource;
import okhttp3.sse.EventSourceListener;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.collections4.MapUtils;
import org.apache.commons.lang3.StringUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.*;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Workflow service providing comprehensive workflow management functionality. Handles workflow
 * creation, modification, execution, publishing, and related operations. Includes support for
 * workflow nodes, edges, versions, debugging, and multi-round conversations.
 *
 * @author WorkflowService Team
 * @since 1.0.0
 */
@Service
@Slf4j
public class WorkflowService extends ServiceImpl<WorkflowMapper, Workflow> {

    public static final String PROTOCOL_ADD_PATH = "/sparkflow/v1/protocol/add";
    public static final String PROTOCOL_UPDATE_PATH = "/sparkflow/v1/protocol/update/";
    public static final String PROTOCOL_DELETE_PATH = "/sparkflow/v1/protocol/delete";
    public static final String NODE_DEBUG_PATH = "/sparkflow/v1/node/debug/";
    public static final String PROTOCOL_BUILD_PATH = "/sparkflow/v1/protocol/build/";
    public static final String CODE_RUN_PATH = "/workflow/v1/run";
    public static final String CLONED_SUFFIX_PATTERN = "[(]\\d+[)]$";

    private static final String JSON_KEY_BOT_ID = "botId";
    private static final String PUBLISH_SUCCESS = "Success";
    private static final int DEFAULT_ORDER = 0;

    @Value("${spring.profiles.active}")
    String env;
    @org.springframework.beans.factory.annotation.Value("${mcp-server.file-path}")
    private String mcpServerFilePath;

    // MCP server cache, key is the ID from file, value is JSONObject
    private static volatile Map<String, JSONObject> MCP_SERVER_CACHE = new HashMap<>();
    // Cache last load time
    private static volatile long lastCacheLoadTime = 0;
    // Cache expiration time (30 seconds)
    private static final long CACHE_EXPIRE_TIME = 30000;
    // Cache load lock
    private static final Object CACHE_LOAD_LOCK = new Object();

    @Autowired
    WorkflowDialogMapper workflowDialogMapper;
    @Autowired
    AppService appService;
    @Autowired
    S3Util s3Util;
    @Autowired
    ApiUrl apiUrl;
    @Autowired
    DataPermissionCheckTool dataPermissionCheckTool;
    @Autowired
    BizConfig bizConfig;
    @Autowired
    EvalSetMapper evalSetMapper;
    @Autowired
    ConfigInfoMapper configInfoMapper;
    @Autowired
    EvalSetVerDataMapper evalSetVerDataMapper;
    @Autowired
    EvalSetVerMapper evalSetVerMapper;
    @Autowired
    OpenPlatformService openPlatformService;
    @Autowired
    FlowToolRelMapper flowToolRelMapper;
    @Autowired
    FlowRepoRelMapper flowRepoRelMapper;
    @Autowired
    FlowReleaseChannelMapper flowReleaseChannelMapper;
    @Autowired
    FlowReleaseAiuiInfoMapper flowReleaseAiuiInfoMapper;
    @Autowired
    CoreSystemService coreSystemService;
    @Autowired
    FlowProtocolTempMapper flowProtocolTempMapper;
    @Autowired
    WorkflowMapper workflowMapper;
    @Autowired
    NodeInfoMapper nodeInfoMapper;
    @Autowired
    ChatInfoMapper chatInfoMapper;
    @Autowired
    RedisUtil redisUtil;
    @Autowired
    private ModelService modelService;
    @Autowired
    McpServerHandler mcpServerHandler;
    @Resource
    FileInfoV2Mapper fileInfoV2Mapper;
    @Autowired
    private UserLangChainInfoMapper userLangChainInfoDao;
    @Autowired
    private ChatBotBaseMapper chatBotBaseMapper;
    @Autowired
    private McpToolConfigMapper mcpToolConfigMapper;
    @Resource
    RedisTemplate<String, Object> redisTemplate;
    @Autowired
    private BotMarketDataService chatBotMarketService;
    @Autowired
    private WorkflowComparisonMapper workflowComparisonMapper;
    @Autowired
    private WorkflowFeedbackMapper workflowFeedbackMapper;
    @Autowired
    private WorkflowVersionMapper workflowVersionMapper;
    @Autowired
    private FlowDbRelMapper flowDbRelMapper;
    @Autowired
    private DbTableMapper dbTableMapper;
    @Autowired
    private ToolBoxMapper toolBoxMapper;
    @Autowired
    private PromptTemplateMapper promptTemplateMapper;
    @Autowired
    private ToolBoxOperateHistoryMapper toolBoxOperateHistoryMapper;
    @Autowired
    private CommonConfig commonConfig;
    @Autowired
    private UserInfoDataService userInfoDataService;

    /**
     * Query workflow list with pagination (in-memory pagination, can be replaced with database
     * pagination if needed).
     *
     * @param apiSpaceId Space ID from API parameter
     * @param current Current page number
     * @param pageSize Page size
     * @param search Search keyword for workflow name or flowId
     * @param status Workflow status filter
     * @param order Sort order (1: by create time desc, 2: by update time desc)
     * @param flowId Specific flow ID to exclude from results
     * @return Paginated workflow list
     */
    public PageData<WorkflowVo> listPage(Long apiSpaceId,
            Integer current,
            Integer pageSize,
            String search,
            Integer status,
            Integer order,
            String flowId) {
        // 1) Parse spaceId priority: Header > parameter
        final Long headSpaceId = SpaceInfoUtil.getSpaceId();
        final Long spaceId = headSpaceId != null ? (apiSpaceId == null ? headSpaceId : apiSpaceId) : apiSpaceId;

        // 2) Special user whitelist, whether can view all workflows
        boolean specFlag = false;
        final ConfigInfo specialUser = configInfoMapper.getByCategoryAndCode("SPECIAL_USER", "workflow-all-view");
        if (specialUser != null && Objects.equals(specialUser.getValue(), UserInfoManagerHandler.getUserId())) {
            specFlag = true;
        }

        UserInfo userInfo = UserInfoManagerHandler.get();
        final String uid = userInfo.getUid();

        final LambdaQueryWrapper<Workflow> wrapper;
        if (spaceId == null) {
            wrapper = Wrappers.lambdaQuery(Workflow.class)
                    .eq(Workflow::getDeleted, false)
                    .isNull(Workflow::getSpaceId)
                    .orderByDesc(Workflow::getOrder)
                    .orderByDesc(Workflow::getUpdateTime);
        } else {
            wrapper = Wrappers.lambdaQuery(Workflow.class)
                    .eq(Workflow::getDeleted, false)
                    .eq(Workflow::getSpaceId, spaceId)
                    .orderByDesc(Workflow::getOrder)
                    .orderByDesc(Workflow::getUpdateTime);
        }
        if (!specFlag && spaceId == null) {
            wrapper.eq(Workflow::getUid, uid);
        }
        if (search != null) {
            dealWithSearchParam(search, wrapper);
        }
        if (order != null) {
            if (order == 1) {
                wrapper.orderByDesc(Workflow::getCreateTime);
            } else if (order == 2) {
                wrapper.orderByDesc(Workflow::getUpdateTime);
            }
        }

        final List<Workflow> list = this.list(wrapper);
        final List<WorkflowVo> workflowVos = new ArrayList<>(list.size());

        final Map<String, String> workflowVersionMap = new HashMap<>();
        fixOnStatusList(list, workflowVersionMap);

        // Filter/mapping
        delwithResultList(status, list, workflowVos, flowId, workflowVersionMap);

        final int safeCurrent = Math.max(1, Optional.ofNullable(current).orElse(1));
        final int safeSize = Math.max(1, Optional.ofNullable(pageSize).orElse(10));
        final int start = Math.min((safeCurrent - 1) * safeSize, workflowVos.size());
        final int end = Math.min(start + safeSize, workflowVos.size());

        final PageData<WorkflowVo> pageData = new PageData<>();
        pageData.setPageData(workflowVos.subList(start, end));
        pageData.setTotalCount((long) workflowVos.size());
        return pageData;
    }

    /**
     * Handle search parameter: decode + escape + like name/flowId.
     */
    private static void dealWithSearchParam(String search, LambdaQueryWrapper<Workflow> wrapper) {
        try {
            final String decode = URLDecoder.decode(search, StandardCharsets.UTF_8.name());
            final String escaped = decode
                    .replace("\\", "\\\\")
                    .replace("_", "\\_")
                    .replace("%", "\\%");
            wrapper.and(w -> w.like(Workflow::getName, escaped).or().like(Workflow::getFlowId, escaped));
        } catch (Exception e) {
            // Invalid search, return empty results
            log.warn("Invalid search parameter: {}", search, e);
            wrapper.and(w -> w.eq(Workflow::getId, -1L));
        }
    }

    /**
     * Filter status and map to VO.
     */
    private void delwithResultList(Integer status,
            List<Workflow> list,
            List<WorkflowVo> workflowVos,
            String flowId,
            Map<String, String> workflowVersionMap) {
        for (Workflow w : list) {
            WorkflowVo vo = new WorkflowVo();
            org.springframework.beans.BeanUtils.copyProperties(w, vo, "data", "publishedData");
            vo.setAddress(s3Util.getS3Prefix());
            vo.setColor(w.getAvatarColor());
            vo.setHaQaNode(checkFlowHasQaNode(w)); // Project tool method (not shown), keep original implementation if none
            if (StringUtils.isNotBlank(w.getData())) {
                vo.setIoInversion(getIoTrans(JSON.parseObject(w.getData(), BizWorkflowData.class).getNodes()));
            }
            vo.setSourceCode(CommonConst.Platform.COMMON);
            vo.setVersion(workflowVersionMap.get(w.getFlowId()));

            if (status != null && status != -1) {
                if (Objects.equals(status, w.getStatus()) && !w.getFlowId().equals(flowId)) {
                    workflowVos.add(vo);
                }
            } else {
                workflowVos.add(vo);
            }
        }
    }

    /**
     * Correct publish status, refresh to "latest published version" data and version number when
     * necessary.
     */
    private void fixOnStatusList(List<Workflow> list, Map<String, String> workflowVersionMap) {
        for (Workflow workflow : list) {
            JSONObject extObj = JSON.parseObject(workflow.getExt());
            int statusFlag = 0;
            Integer botId;
            if (StringUtils.isBlank(workflow.getExt())) {
                UserLangChainInfo userLangChainInfo = userLangChainInfoDao.selectOne(new LambdaQueryWrapper<UserLangChainInfo>().eq(UserLangChainInfo::getFlowId, workflow.getFlowId()));
                if (userLangChainInfo != null) {
                    botId = userLangChainInfo.getBotId();
                } else {
                    botId = -1;
                }
            } else {
                botId = extObj.getInteger(JSON_KEY_BOT_ID);
            }

            if (botId != -1) {
                // Get publish records from publish management (success means published)
                Long count = workflowVersionMapper.selectCount(
                        Wrappers.lambdaQuery(WorkflowVersion.class)
                                .eq(WorkflowVersion::getFlowId, workflow.getFlowId())
                                .eq(WorkflowVersion::getPublishResult, PUBLISH_SUCCESS));
                if (count > 0) {
                    statusFlag = 1;
                    final WorkflowVo maxVersionByFlowId = getMaxVersionByFlowId(workflow.getFlowId());
                    if (maxVersionByFlowId != null) {
                        workflowVersionMap.put(workflow.getFlowId(), maxVersionByFlowId.getVersion());
                        workflow.setData(maxVersionByFlowId.getData());
                    }
                }
                // No publish record, fallback to bot status
                if (statusFlag != 1) {
                    BotDetail result = chatBotBaseMapper.botDetail(botId);
                    if (result != null) {
                        Integer botStatus = result.getBotStatus();
                        if (Objects.equals(2, botStatus)) {
                            statusFlag = 1;
                        }
                        workflow.setName(result.getBotName());
                        workflow.setDescription(result.getBotDesc());
                        workflow.setAvatarIcon(result.getAvatar());
                    }
                }
            } else {
                statusFlag = workflow.getStatus();
            }
            workflow.setStatus(statusFlag);
        }
    }

    /**
     * Details: get by id (could be flowId or primary key).
     */
    public WorkflowVo detail(String id, Long apiSpaceId) {
        final Long headSpaceId = SpaceInfoUtil.getSpaceId();
        final Long spaceId = headSpaceId != null ? (apiSpaceId == null ? headSpaceId : apiSpaceId) : apiSpaceId;

        boolean specFlag = false;
        ConfigInfo specialUser = configInfoMapper.getByCategoryAndCode("SPECIAL_USER", "workflow-all-view");
        if (specialUser != null && Objects.equals(specialUser.getValue(), UserInfoManagerHandler.getUserId())) {
            specFlag = true;
        }

        final Workflow workflow;
        if (id.length() >= 19) {
            workflow = getOne(Wrappers.lambdaQuery(Workflow.class).eq(Workflow::getFlowId, id));
        } else {
            workflow = getById(Long.parseLong(id));
        }
        if (workflow == null) {
            throw new BusinessException(ResponseEnum.WORKFLOW_NOT_EXIST);
        }

        if (!specFlag) {
            dataPermissionCheckTool.checkWorkflowVisibleForDetail(workflow, spaceId);
        }

        // Tool/node version tips
        workflow.setData(buildFlowToolLastVersion(workflow.getData()));
        workflow.setData(buildFlowLastVersion(workflow.getData()));

        WorkflowVo vo = new WorkflowVo();
        org.springframework.beans.BeanUtils.copyProperties(workflow, vo);
        vo.setAddress(s3Util.getS3Prefix());
        vo.setColor(workflow.getAvatarColor());
        vo.setSourceCode(CommonConst.Platform.COMMON);

        if (StringUtils.isBlank(workflow.getExt())) {
            UserLangChainInfo userLangChainInfo = userLangChainInfoDao.selectOne(new LambdaQueryWrapper<UserLangChainInfo>().eq(UserLangChainInfo::getFlowId, workflow.getFlowId()));
            if (userLangChainInfo != null) {
                int botId = userLangChainInfo.getBotId();
                JSONObject jsonObject = updateNameAndDesc(botId, vo);
                vo.setExt(jsonObject.toJSONString());
            }
        } else {
            JSONObject jsonObject = JSON.parseObject(workflow.getExt());
            Integer botId = jsonObject.getInteger(JSON_KEY_BOT_ID);
            updateNameAndDesc(botId, vo);
        }

        // Whether bound to AIUI agent
        FlowReleaseChannel releaseChannel = flowReleaseChannelMapper.selectOne(
                Wrappers.lambdaQuery(FlowReleaseChannel.class)
                        .eq(FlowReleaseChannel::getFlowId, vo.getFlowId())
                        .eq(FlowReleaseChannel::getChannel, WorkflowConst.ReleaseChannel.AIUI));
        if (releaseChannel != null) {
            FlowReleaseAiuiInfo aiuiInfo = flowReleaseAiuiInfoMapper.selectById(releaseChannel.getInfoId());
            String data = aiuiInfo.getData();
            if (data != null) {
                List<AiuiAgentInfo> agentInfos = JSON.parseArray(data, AiuiAgentInfo.class);
                if (CollectionUtils.isNotEmpty(agentInfos)) {
                    vo.setBindAiuiAgent(true);
                }
            }
        }
        return vo;
    }

    /**
     * Mark tool nodes based on current tool information in data, whether it's the "latest version", and
     * backfill tool name when not latest.
     *
     * @param data Workflow data JSON string
     * @return Updated workflow data with version flags
     */
    private String buildFlowToolLastVersion(String data) {
        if (StringUtils.isBlank(data))
            return data;

        BizWorkflowData bizWorkflowData = JSON.parseObject(data, BizWorkflowData.class);
        Map<String, String> toolVersionMap = new HashMap<>();

        bizWorkflowData.getNodes().forEach(n -> {
            if (n.getId().startsWith(WorkflowConst.NodeType.PLUGIN)) {
                String pluginId = n.getData().getNodeParam().getString("pluginId");
                String version = n.getData().getNodeParam().getString("version");
                toolVersionMap.put(pluginId, version);
            } else if (n.getId().startsWith(WorkflowConst.NodeType.AGENT)) {
                JSONObject tools = JSONObject.parseObject(n.getData().getNodeParam().getString("plugin"));
                parseTools(tools.getString("tools"), toolVersionMap);
            }
        });

        if (!toolVersionMap.isEmpty()) {
            List<ToolBox> tools = toolBoxMapper.getToolsLastVersion(new ArrayList<>(toolVersionMap.keySet()));
            Map<String, String> toolLastVersionMap = new LinkedHashMap<>();
            Map<String, String> toolLastPluginMap = new LinkedHashMap<>();
            tools.forEach(tool -> {
                toolLastVersionMap.put(tool.getToolId(), tool.getVersion());
                toolLastPluginMap.put(tool.getToolId(), tool.getName());
            });

            bizWorkflowData.getNodes().forEach(n -> {
                if (n.getId().startsWith(WorkflowConst.NodeType.PLUGIN)) {
                    String pluginId = n.getData().getNodeParam().getString("pluginId");
                    String version = n.getData().getNodeParam().getString("version");
                    markLatestFlagForPluginNode(n, pluginId, version, toolLastVersionMap, toolLastPluginMap);
                } else if (n.getId().startsWith(WorkflowConst.NodeType.AGENT)) {
                    JSONObject plugins = JSONObject.parseObject(n.getData().getNodeParam().getString("plugin"));
                    JSONArray toolsArray = JSONArray.parseArray(plugins.getString("toolsList"));
                    Map<String, String> lastVersionMap = new LinkedHashMap<>();
                    parseTools(plugins.getString("tools"), lastVersionMap);
                    toolsArray.forEach(item -> {
                        JSONObject toolObj = (JSONObject) item;
                        String pluginId = toolObj.getString("toolId");
                        String version = lastVersionMap.get(pluginId);
                        boolean isLatest = computeLatestFlag(pluginId, version, toolLastVersionMap);
                        toolObj.put("isLatest", isLatest);
                        if (!isLatest)
                            toolObj.put("pluginName", toolLastPluginMap.get(pluginId));
                    });
                    plugins.put("toolsList", toolsArray);
                    n.getData().getNodeParam().put("plugin", plugins);
                }
            });
        }
        return JSONObject.toJSONString(bizWorkflowData);
    }

    private static boolean computeLatestFlag(String pluginId,
            String version,
            Map<String, String> toolLastVersionMap) {
        final String last = toolLastVersionMap.get(pluginId);
        if (StringUtils.isBlank(version)) {
            // No version info, and no online version => consider as latest
            return last == null;
        }
        if (last == null) {
            return "V1.0".equals(version);
        }
        return version.equals(last);
    }

    private static void markLatestFlagForPluginNode(BizWorkflowNode n,
            String pluginId,
            String version,
            Map<String, String> toolLastVersionMap,
            Map<String, String> toolLastPluginMap) {
        boolean isLatest = computeLatestFlag(pluginId, version, toolLastVersionMap);
        n.getData().setIsLatest(isLatest);
        if (!isLatest) {
            n.getData().setPluginName(toolLastPluginMap.get(pluginId));
        }
    }

    /**
     * Mark "sub-workflow" nodes whether they are the latest version, and fill in version when
     * necessary.
     *
     * @param data Workflow data JSON string
     * @return Updated workflow data with version information
     */
    private String buildFlowLastVersion(String data) {
        if (StringUtils.isBlank(data))
            return data;

        BizWorkflowData bizWorkflowData = JSON.parseObject(data, BizWorkflowData.class);
        bizWorkflowData.getNodes().forEach(n -> {
            if (n.getId().startsWith(WorkflowConst.NodeType.FLOW)) {
                Object flowIdObj = n.getData().getNodeParam().get("flowId");
                if (flowIdObj == null)
                    return;
                String flowId = String.valueOf(flowIdObj);
                WorkflowVo maxVersionByFlowId = getMaxVersionByFlowId(flowId);
                if (maxVersionByFlowId == null) {
                    n.getData().setIsLatest(true);
                    n.getData().getNodeParam().put("version", StringUtils.EMPTY);
                } else {
                    if (n.getData().getNodeParam().containsKey("version")) {
                        String version = String.valueOf(n.getData().getNodeParam().get("version"));
                        n.getData().setIsLatest(StringUtils.equals(maxVersionByFlowId.getVersion(), version));
                    } else {
                        n.getData().setIsLatest(true);
                        n.getData().getNodeParam().put("version", maxVersionByFlowId.getVersion());
                    }
                }
            }
        });
        return JSONObject.toJSONString(bizWorkflowData);
    }

    /**
     * Query the latest published version information for a specific flowId.
     *
     * @param flowId Flow ID to query
     * @return Latest version information, null if not found
     */
    public WorkflowVo getMaxVersionByFlowId(String flowId) {
        log.info("Query workflow maximum version number, flowId: {}", flowId);
        try {
            Workflow workflow = workflowMapper.selectOne(
                    Wrappers.lambdaQuery(Workflow.class)
                            .eq(Workflow::getFlowId, flowId)
                            .eq(Workflow::getDeleted, false)
                            .orderByDesc(Workflow::getUpdateTime)
                            .last("LIMIT 1"));
            if (workflow == null) {
                return null;
            }

            dataPermissionCheckTool.checkWorkflowBelong(workflow, SpaceInfoUtil.getSpaceId());

            WorkflowVersion workflowVersion = workflowVersionMapper.selectOne(
                    Wrappers.lambdaQuery(WorkflowVersion.class)
                            .eq(WorkflowVersion::getFlowId, flowId)
                            .eq(WorkflowVersion::getPublishResult, PUBLISH_SUCCESS)
                            .orderByDesc(WorkflowVersion::getCreatedTime)
                            .last("LIMIT 1"));

            if (workflowVersion == null)
                return null;

            WorkflowVo vo = new WorkflowVo();
            if (StringUtils.isNotBlank(workflowVersion.getData())) {
                vo.setIoInversion(getIoTrans(JSON.parseObject(workflowVersion.getData(), BizWorkflowData.class).getNodes()));
            }
            vo.setVersion(workflowVersion.getName());
            vo.setData(workflowVersion.getData());
            return vo;
        } catch (Exception e) {
            log.error("Query workflow maximum version number exception, flowId: {}", flowId, e);
            throw new BusinessException(ResponseEnum.WORKFLOW_VERSION_GET_MAX_FAILED);
        }
    }


    /**
     * Parse Agent's tools field (supports array string or object array).
     *
     * @param jsonString JSON string to parse
     * @param toolVersionMap Map to store tool versions
     * @return Updated tool version map
     */
    public Map<String, String> parseTools(String jsonString, Map<String, String> toolVersionMap) {
        JSONArray toolsArray = JSONArray.parseArray(jsonString);
        if (toolsArray == null || toolsArray.isEmpty())
            return toolVersionMap;

        Object first = toolsArray.getFirst();
        if (first instanceof String) {
            List<String> list = toolsArray.toJavaList(String.class);
            for (String toolId : list)
                toolVersionMap.put(toolId, null);
        } else if (first instanceof JSONObject) {
            toolsArray.forEach(item -> {
                JSONObject toolObj = (JSONObject) item;
                String toolId = toolObj.getString("tool_id");
                String version = toolObj.getString("version");
                if (StringUtils.isNotBlank(toolId)) {
                    toolVersionMap.put(toolId, version);
                }
            });
        }
        return toolVersionMap;
    }

    private @NotNull JSONObject updateNameAndDesc(int botId, WorkflowVo vo) {
        JSONObject jsonObject = new JSONObject();
        jsonObject.put(JSON_KEY_BOT_ID, botId);
        BotDetail result = chatBotBaseMapper.botDetail(botId);
        if (result != null) {
            Integer botStatus = result.getBotStatus();
            if (botStatus != null && Arrays.asList(1, 2, 4).contains(botStatus)) {
                vo.setStatus(botStatus);
            }
            vo.setName(result.getBotName());
            vo.setDescription(result.getBotDesc());
            vo.setAvatarIcon(result.getAvatar());
        }
        return jsonObject;
    }

    /**
     * Create workflow: first call core "add protocol", then store locally.
     *
     * @param createReq Create request parameters
     * @param request HTTP request
     * @return Created workflow
     */
    public Workflow create(WorkflowReq createReq, HttpServletRequest request) {
        // Name duplication check (isolated by space)
        final Long spaceId = createReq.getSpaceId();
        Workflow one = getOne(
                Wrappers.lambdaQuery(Workflow.class)
                        .eq(Workflow::getName, createReq.getName())
                        .eq(spaceId == null, Workflow::getUid, UserInfoManagerHandler.getUserId())
                        .eq(spaceId != null, Workflow::getSpaceId, spaceId)
                        .eq(Workflow::getDeleted, false)
                        .last("limit 1"));
        if (one != null) {
            throw new BusinessException(ResponseEnum.WORKFLOW_NAME_EXISTED);
        }

        createReq.setAppId(commonConfig.getAppId());
        if (Boolean.TRUE.equals(createReq.getCommonUser())) {
            // Dedicated cloud commonUser logic
            createReq.setAppId(commonConfig.getAppId());
            createReq.setDomain("generalv3.5");
        }

        // Core system - add protocol, return flowId
        ApiResult<String> addResult = callProtocolAdd(createReq);
        if (addResult.code() != 0) {
            throw new BusinessException(ResponseEnum.RESPONSE_FAILED, addResult.message());
        }

        // Product side database storage
        Workflow workflow = new Workflow();
        org.springframework.beans.BeanUtils.copyProperties(createReq, workflow);
        if (createReq.getAdvancedConfig() != null) {
            workflow.setAdvancedConfig(new JSONObject(createReq.getAdvancedConfig()).toJSONString());
        }
        Date now = new Date();
        workflow.setCreateTime(now);
        workflow.setUpdateTime(now);
        workflow.setFlowId(addResult.data());
        if (spaceId != null)
            workflow.setSpaceId(spaceId);
        workflow.setUid(UserInfoManagerHandler.getUserId());
        if (StringUtils.isBlank(workflow.getAvatarColor()))
            workflow.setAvatarColor("#FFEAD5");
        if (StringUtils.isBlank(workflow.getAvatarIcon()))
            workflow.setAvatarIcon("icon/common/emojiitem_00_10@2x.png");
        if (createReq.getExt() != null && !createReq.getExt().isEmpty()) {
            workflow.setExt(new JSONObject(createReq.getExt()).toJSONString());
        }
        ConfigInfo init = configInfoMapper.getByCategoryAndCode("WORKFLOW_INIT_DATA", "workflow");
        if (StringUtils.isBlank(workflow.getData()) && init != null) {
            workflow.setData(init.getValue());
        }
        workflow.setOrder(DEFAULT_ORDER);
        save(workflow);

        // Sync to Spark database
        // Integer botId = botUtil.syncToSparkDatabase(workflow, UserInfoManagerHandler.getUserId());
        // JSONObject data = new JSONObject();
        // data.put("botId",botId);
        // //Update botId
        // workflow.setExt(data.toJSONString());
        // updateById(workflow);
        return workflow;
    }


    /**
     * Clone workflow (current login space).
     *
     * @param id Workflow ID to clone
     * @return Cloned workflow
     */
    @Transactional(rollbackFor = Exception.class)
    public Workflow clone(Long id) {
        final Long spaceId = SpaceInfoUtil.getSpaceId();
        final Workflow src = getById(id);
        Assert.notNull(src, () -> new BusinessException(ResponseEnum.WORKFLOW_NOT_EXIST));
        dataPermissionCheckTool.checkWorkflowVisible(src, spaceId);

        src.setStatus(WorkflowConst.Status.UNPUBLISHED);

        final String uid = UserInfoManagerHandler.getUserId();

        // Core add protocol
        WorkflowReq flowReq = new WorkflowReq();
        org.springframework.beans.BeanUtils.copyProperties(src, flowReq);
        ApiResult<String> addResult = callProtocolAdd(flowReq);
        if (addResult.code() != 0) {
            throw new BusinessException(ResponseEnum.RESPONSE_FAILED, addResult.message());
        }
        String nFlowId = addResult.data();

        BizWorkflowData data = handleDataClone(nFlowId, src.getData());
        if (data != null) {
            flowReq.setData(data);
            saveRemote(flowReq, nFlowId); // Sync to core
        }

        final Workflow replica = new Workflow();
        org.springframework.beans.BeanUtils.copyProperties(src, replica);
        String cloneName = nextCloneName(src.getName());
        replica.setId(null);
        if (spaceId != null)
            replica.setSpaceId(spaceId);
        replica.setUid(uid);
        replica.setName(cloneName);
        Date now = new Date();
        replica.setCreateTime(now);
        replica.setUpdateTime(now);
        replica.setFlowId(nFlowId);
        if (data != null)
            replica.setData(JSON.toJSONString(data));
        if (src.getPublishedData() != null) {
            replica.setPublishedData(JSON.toJSONString(handleDataClone(nFlowId, src.getPublishedData())));
        }
        replica.setAppUpdatable(false);
        replica.setOrder(DEFAULT_ORDER);
        replica.setExt(null);
        save(replica);
        Integer botId = openPlatformService.syncWorkflowClone(uid, src.getId(), replica.getId(), replica.getFlowId(), spaceId);
        JSONObject result = new JSONObject();
        if (result != null) {
            JSONObject ext = new JSONObject();
            ext.put(JSON_KEY_BOT_ID, Integer.valueOf(String.valueOf(botId)));
            replica.setName(result.getString("botName"));
            replica.setExt(ext.toJSONString());
            updateById(replica);
        }
        return replica;
    }

    /**
     * Clone capability for certain internal workflows (with request context).
     *
     * @param id Workflow ID
     * @param spaceId Space ID
     * @param request HTTP request
     * @return Cloned workflow
     */
    @Transactional(rollbackFor = Exception.class, propagation = Propagation.REQUIRES_NEW)
    public Workflow cloneForXfYun(Long id, Long spaceId, HttpServletRequest request) {
        String uid = RequestContextUtil.getUID();
        log.info("cloneForXfYun uid = {}", uid);
        Workflow src = getById(id);
        if (src == null || Boolean.TRUE.equals(src.getDeleted())) {
            throw new BusinessException(ResponseEnum.WORKFLOW_TEMPLATE_NOT_EXIST);
        }
        src.setStatus(WorkflowConst.Status.UNPUBLISHED);

        // Prevent reusing old bot during cloning
        src.setExt(null);

        WorkflowReq flowReq = new WorkflowReq();
        org.springframework.beans.BeanUtils.copyProperties(src, flowReq);
        ApiResult<String> addResult = callProtocolAdd(flowReq);
        if (addResult.code() != 0) {
            throw new BusinessException(ResponseEnum.RESPONSE_FAILED, addResult.message());
        }
        String nFlowId = addResult.data();

        BizWorkflowData data = handleDataClone(nFlowId, src.getData());
        if (data != null) {
            BizWorkflowData copy = JSON.parseObject(JSON.toJSONString(data), BizWorkflowData.class);
            flowReq.setData(copy);
            saveRemote(flowReq, nFlowId);
        }

        Workflow replica = new Workflow();
        org.springframework.beans.BeanUtils.copyProperties(src, replica);
        String cloneName = nextCloneName(src.getName());

        replica.setId(null);
        if (spaceId != null)
            replica.setSpaceId(spaceId);
        replica.setUid(uid);
        replica.setName(cloneName);
        Date now = new Date();
        replica.setCreateTime(now);
        replica.setUpdateTime(now);
        replica.setFlowId(nFlowId);
        if (data != null)
            replica.setData(JSON.toJSONString(data));
        if (src.getPublishedData() != null) {
            replica.setPublishedData(JSON.toJSONString(handleDataClone(nFlowId, src.getPublishedData())));
        }
        replica.setAppUpdatable(false);
        replica.setOrder(DEFAULT_ORDER);
        save(replica);

        // Fix appId
        if (!commonConfig.getAppId().equals(replica.getAppId())) {
            replaceAppId(commonConfig.getAppId(), replica.getFlowId());
        }
        return replica;
    }

    private String nextCloneName(String origin) {
        String name = origin;
        while (getOne(Wrappers.lambdaQuery(Workflow.class)
                .eq(Workflow::getUid, UserInfoManagerHandler.getUserId())
                .eq(Workflow::getName, name)
                .last("limit 1")) != null) {
            if (ReUtil.contains(CLONED_SUFFIX_PATTERN, name)) {
                int idx = name.lastIndexOf("(");
                String prefix = name.substring(0, idx);
                int num = Integer.parseInt(name.substring(idx + 1, name.length() - 1)) + 1;
                name = prefix + "(" + num + ")";
            } else {
                name = name + "(1)";
            }
        }
        return name;
    }


    /**
     * Update basic info: local changes + core sync (basic elements only).
     *
     * @param updateDto Update request
     * @return Updated workflow
     */
    public Workflow updateInfo(WorkflowReq updateDto) {
        final Long headSpaceId = SpaceInfoUtil.getSpaceId();
        final Long apiSpaceId = updateDto.getSpaceId();
        final Long spaceId = headSpaceId != null ? (apiSpaceId == null ? headSpaceId : apiSpaceId) : apiSpaceId;

        updateDto.setSpaceId(spaceId);
        Workflow workflow = saveLocal(updateDto);

        // Sync to core: only sync basic elements, protocol is synced during build
        updateDto.setData(null);
        updateDto.setAppId(workflow.getAppId());
        saveRemote(updateDto, workflow.getFlowId());
        return workflow;
    }


    /**
     * Build: local save protocol + core sync + call core build SSE.
     *
     * @param buildDto Build request
     * @return Build result
     * @throws InterruptedException If interrupted during execution
     */
    public ApiResult<Void> build(WorkflowReq buildDto) throws InterruptedException {
        buildDto.setSpaceId(SpaceInfoUtil.getSpaceId());

        // 1) Local update (including SSRF validation, binding relationship sync)
        Workflow workflow = saveLocal(buildDto);

        // 2) Sync to core
        buildDto.setAppId(workflow.getAppId());
        saveRemote(buildDto, workflow.getFlowId());

        // 3) Call core build (SSE)
        String url = apiUrl.getWorkflow().concat(PROTOCOL_BUILD_PATH).concat(workflow.getFlowId());
        log.info("workflow protocol build, url = {}", url);

        Request request = new Request.Builder().url(url).post(Util.EMPTY_REQUEST).build();
        CountDownLatch latch = new CountDownLatch(1);
        JSONObject wholeRespJson = new JSONObject();

        RealEventSource realEventSource = new RealEventSource(request, new EventSourceListener() {
            @Override
            public void onOpen(@NotNull EventSource eventSource, @NotNull Response response) {
                log.info("build onOpen, response = {}", response);
            }

            @Override
            public void onEvent(@NotNull EventSource eventSource, String id, String type, @NotNull String data) {
                log.info("build response data = {}", data);
                wholeRespJson.putAll(JSON.parseObject(data));
            }

            @Override
            public void onClosed(@NotNull EventSource eventSource) {
                log.info("build onClosed");
                latch.countDown();
            }

            @Override
            public void onFailure(@NotNull EventSource eventSource, Throwable t, Response response) {
                log.error("build onFailure, res = {}, t = {}", response, t.getMessage(), t);
            }
        });
        try {
            realEventSource.connect(OkHttpUtil.getHttpClient());
            latch.await();
            String message = wholeRespJson.getString("message");
            if (StringUtils.isNotBlank(message)) {
                int code = Integer.parseInt(message.substring(0, message.indexOf(":")));
                if (code != 0)
                    throw new BusinessException(ResponseEnum.RESPONSE_FAILED, message);
            }
            return ApiResult.success();
        } finally {
            // Prevent leaks
            realEventSource.cancel();
        }
    }


    /**
     * Single node debug: convert Biz protocol to core protocol and call.
     *
     * @param nodeId Node ID
     * @param debugDto Debug request
     * @return Debug result
     */
    public ApiResult<Object> nodeDebug(String nodeId, WorkflowDebugDto debugDto) {
        BizWorkflowData bizWorkflowData = debugDto.getData();
        BizWorkflowNode node = bizWorkflowData.getNodes().get(0);
        String prefix = node.getId().split("::")[0];
        String type = node.getType();
        BizNodeData bizNodeData = node.getData();

        // Fill app/ak/sk
        String appId = bizNodeData.getNodeParam().getString("appId");
        AkSk aksk = appService.remoteCallAkSk(appId);
        try {
            bizNodeData.getNodeParam().put("apiKey", aksk.getApiKey());
            bizNodeData.getNodeParam().put("apiSecret", aksk.getApiSecret());

            if (!node.getId().startsWith(WorkflowConst.NodeType.FLOW)
                    && StringUtils.equalsAny(env, CommonConst.FIXED_APPID_ENV)) {
                buidKeyInfo(bizNodeData);
            }
            String source = bizNodeData.getNodeParam().getString("source");
            if ("openai".equals(source)) {
                Long modelId = bizNodeData.getNodeParam().getLong("modelId");
                if (modelId != null) {
                    Model model = modelService.getById(modelId);
                    bizNodeData.getNodeParam().put("apiKey", model.getApiKey());
                    bizNodeData.getNodeParam().put("apiSecret", StringUtils.EMPTY);
                }
            }
            if (SpaceInfoUtil.getSpaceId() != null && "database".equals(prefix)) {
                bizNodeData.getNodeParam().put("uid", Objects.requireNonNull(UserInfoManagerHandler.getUserId()).toString());
            }
            checkAndEditData(bizNodeData, prefix);
            fixOnRepoNode(type, bizNodeData, prefix);
        } catch (Exception ignored) {
            if (!node.getId().startsWith(WorkflowConst.NodeType.FLOW)
                    && StringUtils.equalsAny(env, CommonConst.FIXED_APPID_ENV)) {
                buidKeyInfo(bizNodeData);
                checkAndEditData(bizNodeData, prefix);
                fixOnRepoNode(type, bizNodeData, prefix);
            }
        }

        // Build core protocol
        FlowProtocol protocol = new FlowProtocol();
        org.springframework.beans.BeanUtils.copyProperties(debugDto, protocol);
        FlowProtocolData protocolData = new FlowProtocolData();
        protocol.setId(debugDto.getFlowId());
        protocolData.setEdges(bizEdgesToSysEdges(bizWorkflowData.getEdges()));
        protocolData.setNodes(bizNodesToSysNodes(bizWorkflowData.getNodes()));
        protocol.setData(protocolData);

        String url = apiUrl.getWorkflow().concat(NODE_DEBUG_PATH);
        String body = JSON.toJSONString(protocol);

        log.info("node debug, url = {}, body = {}", url, body);
        String response = OkHttpUtil.post(url, body);
        log.info("node debug, response = {}", response);

        NodeDebugResponse nodeDebugResponse = JSON.parseObject(response, NodeDebugResponse.class);
        if (nodeDebugResponse.getCode() != 0) {
            throw new BusinessException(ResponseEnum.RESPONSE_FAILED, nodeDebugResponse.getMessage());
        }
        return ApiResult.success(nodeDebugResponse.getData());
    }

    /**
     * Logical delete: local flag + call core delete + cleanup tool/knowledge base relationships.
     *
     * @param id Workflow ID
     * @param spaceId Space ID
     * @return Delete result
     */
    @Transactional(rollbackFor = Exception.class)
    public ApiResult<Void> logicDelete(Long id, Long spaceId) {
        if (id == null)
            return ApiResult.error(ResponseEnum.PARAM_MISS);

        Workflow workflow = getById(id);
        if (workflow == null)
            throw new BusinessException(ResponseEnum.WORKFLOW_NOT_EXIST);

        dataPermissionCheckTool.checkWorkflowBelong(workflow, spaceId);

        workflow.setDeleted(true);
        updateById(workflow);

        String flowId = workflow.getFlowId();
        if (flowId != null) {
            String url = apiUrl.getWorkflow().concat(PROTOCOL_DELETE_PATH);
            String body = new JSONObject()
                    .fluentPut("app_id", workflow.getAppId())
                    .fluentPut("flow_id", flowId)
                    .toString();
            log.info("call workflow delete request url = {}, body = {}", url, body);
            String response = OkHttpUtil.post(url, body);
            log.info("call workflow delete response = {}", response);
        }

        // Clear relationships
        flowToolRelMapper.delete(Wrappers.lambdaQuery(FlowToolRel.class).eq(FlowToolRel::getFlowId, flowId));
        flowRepoRelMapper.delete(Wrappers.lambdaQuery(FlowRepoRel.class).eq(FlowRepoRel::getFlowId, flowId));
        return ApiResult.success();
    }


    private List<Edge> bizEdgesToSysEdges(List<BizWorkflowEdge> bizWorkflowEdges) {
        List<Edge> edges = new ArrayList<>(bizWorkflowEdges.size());
        bizWorkflowEdges.forEach(item -> {
            Edge e = new Edge();
            e.setSourceNodeId(item.getSource());
            e.setTargetNodeId(item.getTarget());
            String sourceHandle = item.getSourceHandle();
            if (StringUtils.isNotBlank(sourceHandle) &&
                    StringUtils.containsAny(sourceHandle, "intent-one-of", "branch_one_of", "fail_one_of")) {
                sourceHandle = "intent_chain|".concat(sourceHandle);
            }
            e.setSourceHandle(sourceHandle);
            e.setTargetHandle(item.getTargetHandle());
            edges.add(e);
        });
        return edges;
    }

    private List<Node> bizNodesToSysNodes(List<BizWorkflowNode> bizWorkflowNodes) {
        List<Node> nodes = new ArrayList<>(bizWorkflowNodes.size());
        bizWorkflowNodes.forEach(item -> {
            Node n = new Node();
            n.setId(item.getId());
            n.setData(bizNodeDataToSysNodeData(item.getData()));
            nodes.add(n);
        });
        return nodes;
    }


    private NodeData bizNodeDataToSysNodeData(BizNodeData bizNodeData) {
        NodeData nodeData = new NodeData();
        nodeData.setNodeMeta(bizNodeData.getNodeMeta());
        nodeData.getNodeMeta().put("aliasName", bizNodeData.getLabel());

        // inputs
        List<BizInputOutput> bizInputs = bizNodeData.getInputs();
        List<InputOutput> inputs = new ArrayList<>(bizInputs.size());
        inputCopy(bizInputs, inputs);

        // outputs
        List<BizInputOutput> bizOutputs = bizNodeData.getOutputs();
        List<InputOutput> outputs = new ArrayList<>(bizOutputs.size());
        outputCopy(bizOutputs, outputs);

        nodeData.setInputs(inputs);
        nodeData.setOutputs(outputs);

        JSONObject jsonObject = new JSONObject();
        jsonObject.putAll(bizNodeData.getNodeParam());
        nodeData.setNodeParam(handleNodeParam(jsonObject));
        nodeData.setRetryConfig(handleRetryConfig(bizNodeData));
        return nodeData;
    }


    private static @Nullable JSONObject handleRetryConfig(BizNodeData bizNodeData) {
        JSONObject retryConfig = bizNodeData.getRetryConfig();
        if (retryConfig != null) {
            String customOutput = retryConfig.getString("customOutput");
            if (StringUtils.isNotBlank(customOutput)) {
                retryConfig.put("customOutput", JSONObject.parseObject(customOutput));
            }
        }
        return retryConfig;
    }

    private void inputCopy(List<BizInputOutput> bizInputs, List<InputOutput> inputs) {
        if (CollectionUtils.isEmpty(bizInputs))
            return;

        bizInputs.forEach(bi -> {
            // Empty input filtering
            if (isInputContentEmpty(bi.getSchema().getValue().getContent()))
                return;

            InputOutput i = new InputOutput();
            org.springframework.beans.BeanUtils.copyProperties(bi, i);

            // schema
            BizSchema bs = bi.getSchema();
            Schema s = new Schema();
            if ("time".equalsIgnoreCase(bs.getType())) {
                bs.setType("string");
            }
            org.springframework.beans.BeanUtils.copyProperties(bs, s);
            if (bs.getType() != null && bs.getType().startsWith("array-")) {
                String[] split = bs.getType().split("-");
                s.setType(split[0]);
                Property p = new Property();
                p.setType(split.length > 1 ? split[1] : "object");
                s.setItems(p);
            }

            // value
            BizValue bv = bs.getValue();
            if (bv != null) {
                com.iflytek.astra.console.toolkit.entity.core.workflow.node.Value v = new com.iflytek.astra.console.toolkit.entity.core.workflow.node.Value();
                org.springframework.beans.BeanUtils.copyProperties(bv, v);
                s.setValue(v);
            }
            i.setSchema(s);
            inputs.add(i);
        });
    }

    private void outputCopy(List<BizInputOutput> bizOutputs, List<InputOutput> outputs) {
        if (CollectionUtils.isEmpty(bizOutputs))
            return;

        bizOutputs.forEach(bo -> {
            InputOutput o = new InputOutput();
            org.springframework.beans.BeanUtils.copyProperties(bo, o);

            BizSchema bs = bo.getSchema();
            Schema s = new Schema();
            org.springframework.beans.BeanUtils.copyProperties(bs, s);

            if (bs.getType() != null && bs.getType().startsWith("array-")) {
                String[] split = bs.getType().split("-");
                s.setType(split[0]);
                Property p = new Property();
                p.setType(split.length > 1 ? split[1] : "object");
                if ("object".equals(p.getType())) {
                    p.setProperties(bizPropertiesToPropertyMap(bs.getProperties()));
                    // Required field collection
                    List<String> required = new ArrayList<>();
                    if (bs.getProperties() != null) {
                        bs.getProperties().forEach(bp -> {
                            if (Boolean.TRUE.equals(bp.getRequired()))
                                required.add(bp.getName());
                        });
                    }
                    p.setRequired(required);
                }
                s.setItems(p);
            } else {
                s.setProperties(bizPropertiesToPropertyMap(bs.getProperties()));
            }

            BizValue bv = bs.getValue();
            if (bv != null) {
                com.iflytek.astra.console.toolkit.entity.core.workflow.node.Value v = new com.iflytek.astra.console.toolkit.entity.core.workflow.node.Value();
                org.springframework.beans.BeanUtils.copyProperties(bv, v);
                s.setValue(v);
            }

            // Compatible with description
            if (s.getDescription() == null) {
                s.setDescription(bs.getDft() == null ? null : bs.getDft().toString());
                s.setDft(null);
            }

            o.setSchema(s);
            outputs.add(o);
        });
    }


    public ApiResult<String> saveDialog(WorkflowDialog dialog) {
        Workflow workflow = getById(dialog.getWorkflowId());
        dataPermissionCheckTool.checkWorkflowVisible(workflow, SpaceInfoUtil.getSpaceId());

        String answerItem = dialog.getAnswerItem();
        if (answerItem != null && answerItem.length() >= 2 && answerItem.startsWith("\"") && answerItem.endsWith("\"")) {
            dialog.setAnswerItem(answerItem.substring(1, answerItem.length() - 1));
        }
        dialog.setUid(UserInfoManagerHandler.getUserId());
        dialog.setCreateTime(new Date());
        workflowDialogMapper.insert(dialog);
        return ApiResult.success(dialog.getChatId());
    }

    public List<WorkflowDialog> listDialog(Long workflowId, Integer type) {
        return workflowDialogMapper.selectList(
                Wrappers.lambdaQuery(WorkflowDialog.class)
                        .eq(WorkflowDialog::getUid, UserInfoManagerHandler.getUserId())
                        .eq(WorkflowDialog::getWorkflowId, workflowId)
                        .eq(WorkflowDialog::getType, type)
                        .eq(WorkflowDialog::getDeleted, false)
                        .orderByDesc(WorkflowDialog::getCreateTime)
                        .last("limit 10"));
    }

    /**
     * Private method
     *
     * @param workflowReq Workflow request
     * @return API result with flow ID
     */
    /**
     * Call core "add protocol", return flowId.
     */
    public ApiResult<String> callProtocolAdd(WorkflowReq workflowReq) {
        String url = apiUrl.getWorkflow().concat(PROTOCOL_ADD_PATH);
        String body = new JSONObject()
                .fluentPut("app_id", workflowReq.getAppId())
                .fluentPut("name", workflowReq.getName())
                .fluentPut("description", workflowReq.getDescription())
                .fluentPut("data", null)
                .toString();
        log.info("workflow protocol add, url = {}, body = {}", url, body);

        String response = OkHttpUtil.post(url, body);
        log.info("workflow protocol add, response = {}", response);

        Result<?> result = JSON.parseObject(response, Result.class);
        if (result.getCode() != 0) {
            throw new BusinessException(ResponseEnum.RESPONSE_FAILED, result.getMessage());
        }
        JSONObject jsonObject = JSON.parseObject(String.valueOf(result.getData()));
        String flowId = jsonObject.getString("flow_id");
        return ApiResult.success(flowId);
    }

    /**
     * Save local information (including protocol, SSRF validation, knowledge base/tool/database binding
     * relationship sync).
     * <p>
     * Note: This method is lengthy, mainly containing your original business rules; I only enhanced
     * null checks/logging/boundaries and preserved behavioral consistency.
     * </p>
     *
     * @param saveReq Save request
     * @return Saved workflow
     */
    private Workflow saveLocal(WorkflowReq saveReq) {
        // 1) Load and permission check
        Workflow workflow = loadAndCheckWorkflow(saveReq);

        // 2) Sync bot basic info & basic field updates
        syncBaseBotAndPatchBasics(saveReq, workflow);

        // 3) Merge/validate advanced configuration
        mergeAdvancedConfigSafe(saveReq, workflow);

        // 4) Validate & write protocol data (nodes/edges & length limit & merge write)
        BizWorkflowData bizWorkflowData = saveReq.getData();
        writeProtocolDataIfPresent(workflow, bizWorkflowData);

        // 5) SSRF/URL whitelist/blacklist validation (only when data exists)
        if (bizWorkflowData != null) {
            validateSsrfForNodes(bizWorkflowData);
        }

        // 6) Status change and persistence
        touchAndPersist(workflow);

        // 7) Sync "prologue" etc. (only for XFYUN source with advancedConfig)
        syncPrologueIfNeeded(workflow, saveReq);

        // 8) Asynchronously refresh binding relationships (tools/knowledge base/database)
        scheduleRelationsRefresh(workflow.getFlowId(), bizWorkflowData);

        return workflow;
    }

    // ========== 1. Load and permission check ==========
    private Workflow loadAndCheckWorkflow(WorkflowReq saveReq) {
        Workflow workflow = getById(saveReq.getId());
        if (workflow == null) {
            throw new BusinessException(ResponseEnum.WORKFLOW_NOT_EXIST);
        }
        dataPermissionCheckTool.checkWorkflowVisible(workflow, saveReq.getSpaceId());
        return workflow;
    }

    // ========== 2. Sync bot basic info & basic field updates ==========
    private void syncBaseBotAndPatchBasics(WorkflowReq saveReq, Workflow workflow) {
        // Sync bot basic info (name/description/avatar/category)
        updateBaseBot(saveReq, workflow.getExt());

        // ---- Update basic info ----
        if (StringUtils.isNotBlank(saveReq.getName())) {
            workflow.setName(saveReq.getName());
        }
        if (saveReq.getCategory() != null) {
            workflow.setCategory(saveReq.getCategory());
        }
        if (StringUtils.isNotBlank(saveReq.getDescription())) {
            workflow.setDescription(saveReq.getDescription());
        }
        if (StringUtils.isNotBlank(saveReq.getAvatarIcon())) {
            workflow.setAvatarIcon(saveReq.getAvatarIcon());
        }
    }

    // ========== 3. Merge/validate advanced configuration ==========
    private void mergeAdvancedConfigSafe(WorkflowReq saveReq, Workflow workflow) {
        if (saveReq.getAdvancedConfig() == null) {
            return;
        }
        try {
            ObjectMapper mapper = new ObjectMapper();
            ObjectNode original = workflow.getAdvancedConfig() == null
                    ? mapper.createObjectNode()
                    : (ObjectNode) mapper.readTree(workflow.getAdvancedConfig());
            ObjectNode updateNode = (ObjectNode) mapper.readTree(new JSONObject(saveReq.getAdvancedConfig()).toJSONString());
            mergeJsonNodes(original, updateNode);
            workflow.setAdvancedConfig(mapper.writeValueAsString(original));
        } catch (Exception ex) {
            log.error("update advancedConfig error, original:{}, update:{}, error:{}",
                    workflow.getAdvancedConfig(),
                    new JSONObject(saveReq.getAdvancedConfig()).toJSONString(), ex);
            throw new BusinessException(ResponseEnum.WORKFLOW_HIGH_PARAM_FAILED);
        }
    }

    // ========== 4. Write protocol data (including validation and length limits) ==========
    private void writeProtocolDataIfPresent(Workflow workflow, BizWorkflowData bizWorkflowData) {
        if (bizWorkflowData == null) {
            return;
        }
        if (CollectionUtils.isEmpty(bizWorkflowData.getNodes())) {
            throw new BusinessException(ResponseEnum.WORKFLOW_PROTOCOL_NODE_INFO_CANNOT_EMPTY);
        }
        String dataString = JSON.toJSONString(bizWorkflowData);
        if (dataString.getBytes(StandardCharsets.UTF_8).length > CommonConst.MEDIUM_TEXT_BYTES_LIMIT) {
            throw new BusinessException(ResponseEnum.WORKFLOW_PROTOCOL_LENGTH_LIMIT);
        }
        String old = workflow.getData();
        if (StringUtils.isNotEmpty(old)) {
            JSONObject dataInfo = JSON.parseObject(old);
            dataInfo.put("nodes", bizWorkflowData.getNodes());
            dataInfo.put("edges", bizWorkflowData.getEdges());
            workflow.setData(JSON.toJSONString(dataInfo));
        } else {
            workflow.setData(dataString);
        }
    }

    // ========== 5. SSRF/URL validation ==========
    private void validateSsrfForNodes(BizWorkflowData bizWorkflowData) {
        List<String> ipBlacklist = loadIpBlacklist();
        SsrfProperties ssrfProps = new SsrfProperties();
        ssrfProps.setIpBlaklist(ipBlacklist);
        SsrfParamGuard ssrfGuard = new SsrfParamGuard(ssrfProps);

        for (BizWorkflowNode node : bizWorkflowData.getNodes()) {
            JSONObject nodeParam = node.getData().getNodeParam();
            if (nodeParam == null) {
                continue;
            }
            final boolean isAgent = node.getId().startsWith(WorkflowConst.NodeType.AGENT);
            final String url = isAgent
                    ? Optional.ofNullable(nodeParam.getJSONObject("modelConfig")).map(o -> o.getString("api")).orElse(null)
                    : nodeParam.getString("url");
            if (StringUtils.isBlank(url)) {
                continue;
            }
            ensureHttpLikeScheme(url);
            try {
                SsrfValidators.Normalized n = SsrfValidators.normalizeFlex(SsrfValidators.stripUserInfo(url));
                URL norm = n.effectiveUrl;
                String rebuilt = SsrfValidators.rebuildWithOriginalScheme(norm, n.originalScheme, n.wsLike);
                String hostOnly = rebuilt + "://" + norm.getHost() + (norm.getPort() != -1 ? (":" + norm.getPort()) : "");
                ssrfGuard.validateUrlParam(hostOnly);
            } catch (BusinessException e) {
                throw e;
            } catch (Exception e) {
                log.error("workflow model url check failed :", e);
                throw new BusinessException(ResponseEnum.MODEL_URL_CHECK_FAILED);
            }
        }
    }

    private List<String> loadIpBlacklist() {
        List<ConfigInfo> cfgList = configInfoMapper.getListByCategory("NETWORK_SEGMENT_BLACK_LIST");
        if (cfgList == null || cfgList.isEmpty() || StringUtils.isBlank(cfgList.get(0).getValue())) {
            return Collections.emptyList();
        }
        return Arrays.stream(cfgList.get(0).getValue().split(","))
                .map(String::trim)
                .filter(StringUtils::isNotBlank)
                .distinct()
                .toList();
    }

    private void ensureHttpLikeScheme(String url) {
        String lower = StringUtils.left(url.trim(), 6).toLowerCase(Locale.ROOT);
        if (!(lower.startsWith("http:") || lower.startsWith("https:")
                || lower.startsWith("ws:") || lower.startsWith("wss:"))) {
            throw new BusinessException(ResponseEnum.MODEL_URL_CHECK_FAILED);
        }
    }

    // ========== 6. Update status and persist ==========
    private void touchAndPersist(Workflow workflow) {
        workflow.setUpdateTime(new Date());
        workflow.setAppUpdatable(false);
        workflow.setEditing(true);
        updateById(workflow);
    }

    // ========== 7. Conditional sync prologue ==========
    private void syncPrologueIfNeeded(Workflow workflow, WorkflowReq saveReq) {
        if (!Objects.equals(workflow.getSource(), CommonConst.PlatformCode.XFYUN)
                || saveReq.getAdvancedConfig() == null) {
            return;
        }
        JSONObject advancedConfig = JSONObject.parseObject(workflow.getAdvancedConfig());
        if (advancedConfig.get("prologue") != null) {
            JSONObject prologue = JSONObject.parseObject(advancedConfig.get("prologue").toString());
            String prologueText = Optional.ofNullable(prologue.get("prologueText")).map(Object::toString).orElse("");
            List<String> inputExample = Optional.ofNullable(prologue.getList("inputExample", String.class))
                    .orElseGet(ArrayList::new);
            openPlatformService.syncWorkflowUpdate(workflow.getId(), workflow.getDescription(), prologueText, inputExample);
        }
    }

    // ========== 8. Asynchronous relationship refresh ==========
    private void scheduleRelationsRefresh(String flowId, BizWorkflowData bizWorkflowData) {
        MyThreadTool.execute(() -> refreshToolRelations(flowId, bizWorkflowData));
        MyThreadTool.execute(() -> refreshRepoRelations(flowId, bizWorkflowData));
        MyThreadTool.execute(() -> refreshDbRelations(flowId, bizWorkflowData));
    }

    // ---- Binding relationship refresh: tools / knowledge base / database ----
    private void refreshToolRelations(String flowId, BizWorkflowData bizWorkflowData) {
        List<FlowToolRel> nowTools = new ArrayList<>();
        if (bizWorkflowData != null) {
            bizWorkflowData.getNodes().forEach(n -> {
                if (n.getId().startsWith(WorkflowConst.NodeType.PLUGIN)) {
                    String pluginId = n.getData().getNodeParam().getString("pluginId");
                    String version = n.getData().getNodeParam().getString("version");
                    if (StringUtils.isNotBlank(pluginId)) {
                        FlowToolRel rel = new FlowToolRel();
                        rel.setFlowId(flowId);
                        rel.setToolId(pluginId);
                        rel.setVersion(version);
                        nowTools.add(rel);
                    }
                }
                if (n.getId().startsWith(WorkflowConst.NodeType.AGENT)) {
                    JSONObject tools = n.getData().getNodeParam().getJSONObject("plugin");
                    Map<String, String> toolVersionMap = new HashMap<>();
                    String tools1 = JSONObject.toJSONString(tools.get("tools"));
                    parseTools(tools1, toolVersionMap);
                    JSONArray.parseArray(JSON.toJSONString(tools.get("toolsList"))).forEach(item -> {
                        JSONObject tool = (JSONObject) item;
                        String toolId = tool.getString("toolId");
                        String version = tool.getString("version");
                        if (StringUtils.isNotBlank(toolId) && !toolVersionMap.containsKey(toolId)) {
                            toolVersionMap.put(toolId, version);
                        }
                    });
                    toolVersionMap.forEach((toolId, version) -> {
                        FlowToolRel rel = new FlowToolRel();
                        rel.setFlowId(flowId);
                        rel.setToolId(toolId);
                        rel.setVersion(version);
                        nowTools.add(rel);
                    });
                }
            });
            flowToolRelMapper.delete(Wrappers.lambdaQuery(FlowToolRel.class).eq(FlowToolRel::getFlowId, flowId));
            if (!nowTools.isEmpty())
                flowToolRelMapper.insertBatch(nowTools);
        }
    }

    private void refreshRepoRelations(String flowId, BizWorkflowData bizWorkflowData) {
        List<FlowRepoRel> had = flowRepoRelMapper.selectList(Wrappers.lambdaQuery(FlowRepoRel.class)
                .eq(FlowRepoRel::getFlowId, flowId));
        List<String> hadRepos = had.stream().map(FlowRepoRel::getRepoId).toList();

        List<String> nowRepos = new ArrayList<>();
        if (bizWorkflowData != null) {
            bizWorkflowData.getNodes().forEach(n -> {
                if (n.getId().startsWith(WorkflowConst.NodeType.KNOWLEDGE)) {
                    JSONArray array = n.getData().getNodeParam().getJSONArray("repoId");
                    if (array != null && !array.isEmpty())
                        nowRepos.addAll(array.toJavaList(String.class));
                }
                if (n.getId().startsWith(WorkflowConst.NodeType.KNOWLEDGE_PRO)) {
                    JSONArray array = n.getData().getNodeParam().getJSONArray("repoIds");
                    if (array != null && !array.isEmpty())
                        nowRepos.addAll(array.toJavaList(String.class));
                }
                if (n.getId().startsWith(WorkflowConst.NodeType.AGENT)) {
                    JSONArray array = n.getData().getNodeParam().getJSONObject("plugin").getJSONArray("knowledge");
                    if (array != null && !array.isEmpty()) {
                        for (int i = 0; i < array.size(); i++) {
                            JSONObject item = (array.get(i) instanceof JSONObject)
                                    ? (JSONObject) array.get(i)
                                    : new JSONObject((Map<?, ?>) array.get(i));
                            JSONArray jsonArray = item.getJSONObject("match").getJSONArray("repoIds");
                            nowRepos.addAll(jsonArray.toJavaList(String.class));
                        }
                    }
                }
            });
            List<String> addRepos = CollectionUtil.subtractToList(nowRepos, hadRepos);
            List<String> delRepos = CollectionUtil.subtractToList(hadRepos, nowRepos);
            addRepos.forEach(r -> flowRepoRelMapper.insert(new FlowRepoRel(flowId, r)));
            delRepos.forEach(r -> flowRepoRelMapper.delete(Wrappers.lambdaQuery(FlowRepoRel.class)
                    .eq(FlowRepoRel::getFlowId, flowId)
                    .eq(FlowRepoRel::getRepoId, r)));
        }
    }

    private void refreshDbRelations(String flowId, BizWorkflowData bizWorkflowData) {
        Map<String, Set<String>> dbMap = new HashMap<>();
        if (bizWorkflowData != null) {
            bizWorkflowData.getNodes().forEach(n -> {
                if (n.getId().startsWith(WorkflowConst.NodeType.DATABASE)) {
                    Object dbIdObj = n.getData().getNodeParam().get("dbId");
                    if (dbIdObj == null)
                        return;
                    String dbId = String.valueOf(dbIdObj);
                    try {
                        int mode = Integer.parseInt(String.valueOf(n.getData().getNodeParam().get("mode")));
                        Set<String> tableNameSet = dbMap.computeIfAbsent(dbId, k -> new HashSet<>());
                        if (mode == 0) {
                            String sql = String.valueOf(n.getData().getNodeParam().get("sql"));
                            Statement statement = CCJSqlParserUtil.parse(sql);
                            TablesNamesFinder tablesNamesFinder = new TablesNamesFinder();
                            Set<String> tableList = tablesNamesFinder.getTables(statement);
                            tableNameSet.addAll(tableList);
                        } else {
                            String tableName = String.valueOf(n.getData().getNodeParam().get("tableName"));
                            tableNameSet.add(tableName);
                        }
                    } catch (Exception ex) {
                        dbMap.put(dbId, null);
                    }
                }
            });
            flowDbRelMapper.delete(Wrappers.lambdaQuery(FlowDbRel.class).eq(FlowDbRel::getFlowId, flowId));

            List<FlowDbRel> dbRelList = new ArrayList<>();
            dbMap.forEach((dbId, tableNames) -> {
                if (tableNames == null) {
                    FlowDbRel rel = new FlowDbRel();
                    rel.setFlowId(flowId);
                    rel.setDbId(dbId);
                    rel.setTbId(null);
                    dbRelList.add(rel);
                } else if (!tableNames.isEmpty()) {
                    List<DbTable> dbTables = dbTableMapper.selectListByDbIdAndName(dbId, tableNames);
                    dbTables.forEach(t -> {
                        FlowDbRel rel = new FlowDbRel();
                        rel.setFlowId(flowId);
                        rel.setDbId(dbId);
                        rel.setTbId(t.getId());
                        dbRelList.add(rel);
                    });
                }
            });
            if (!dbRelList.isEmpty())
                flowDbRelMapper.insertBatch(dbRelList);
        }
    }


    private void updateBaseBot(WorkflowReq saveReq, String ext) {
        Integer botId = null;
        if (!StringUtils.isBlank(ext)) {
            JSONObject jsonObject = JSON.parseObject(ext);
            botId = jsonObject.getInteger("botId");
        } else {
            UserLangChainInfo userLangChainInfo = userLangChainInfoDao.selectOne(new LambdaQueryWrapper<UserLangChainInfo>().eq(UserLangChainInfo::getFlowId, saveReq.getFlowId()));
            if (userLangChainInfo != null) {
                botId = userLangChainInfo.getBotId();
            }
        }
        if (botId != null) {
            ChatBotBase chatBotBase = chatBotBaseMapper.selectById(botId);
            if (StringUtils.isNotBlank(saveReq.getName())) {
                chatBotBase.setBotName(saveReq.getName());
            }
            if (StringUtils.isNotBlank(saveReq.getDescription())) {
                chatBotBase.setBotDesc(saveReq.getDescription());
            }
            if (StringUtils.isNotBlank(saveReq.getAvatarIcon())) {
                chatBotBase.setAvatar(saveReq.getAvatarIcon());
            }
            if (saveReq.getCategory() != null) {
                chatBotBase.setBotType(saveReq.getCategory());
            }
            chatBotBase.setUpdateTime(LocalDateTime.now());
            chatBotBaseMapper.updateById(chatBotBase);
        }
    }

    private void saveFlowProtocolTemp(String flowId, String bizProtocol, String sysProtocol) {
        if (bizProtocol == null)
            return;

        if (sysProtocol == null) {
            FlowProtocolTemp last = flowProtocolTempMapper.selectOne(
                    Wrappers.lambdaQuery(FlowProtocolTemp.class)
                            .eq(FlowProtocolTemp::getFlowId, flowId)
                            .orderByDesc(FlowProtocolTemp::getCreatedTime)
                            .last("limit 1"));
            if (last == null || DateUtil.between(new Date(), last.getCreatedTime(), DateUnit.MINUTE, true) > 10) {
                FlowProtocolTemp t = new FlowProtocolTemp();
                t.setFlowId(flowId);
                t.setCreatedTime(new Date());
                t.setBizProtocol(bizProtocol);
                flowProtocolTempMapper.insert(t);
            }
        } else {
            FlowProtocolTemp last = flowProtocolTempMapper.selectOne(
                    Wrappers.lambdaQuery(FlowProtocolTemp.class)
                            .eq(FlowProtocolTemp::getFlowId, flowId)
                            .orderByDesc(FlowProtocolTemp::getCreatedTime)
                            .isNotNull(FlowProtocolTemp::getSysProtocol)
                            .last("limit 1"));
            if (last == null || DateUtil.between(new Date(), last.getCreatedTime(), DateUnit.MINUTE, true) > 10) {
                FlowProtocolTemp t = new FlowProtocolTemp();
                t.setFlowId(flowId);
                t.setCreatedTime(new Date());
                t.setBizProtocol(bizProtocol);
                t.setSysProtocol(sysProtocol);
                flowProtocolTempMapper.insert(t);
            }
        }

    }

    /**
     * Merge two JSON nodes, updating targetNode with values from sourceNode
     */
    private void mergeJsonNodes(ObjectNode targetNode, ObjectNode sourceNode) {
        Iterator<Map.Entry<String, JsonNode>> fields = sourceNode.fields();
        while (fields.hasNext()) {
            Map.Entry<String, JsonNode> field = fields.next();
            String fieldName = field.getKey();
            JsonNode sourceValue = field.getValue();

            // If targetNode has this field and is ObjectNode, recursively update
            if (targetNode.has(fieldName) && targetNode.get(fieldName).isObject() && sourceValue.isObject()) {
                mergeJsonNodes((ObjectNode) targetNode.get(fieldName), (ObjectNode) sourceValue);
            } else {
                // Otherwise directly replace the value
                targetNode.set(fieldName, sourceValue);
            }
        }
    }

    public FlowProtocol buildWorkflowData(WorkflowReq saveDto, String flowId) {
        FlowProtocol protocol = null;
        BizWorkflowData bizWorkflowData = saveDto.getData();
        // Fill app elements
        String appId;
        String apiKey;
        String apiSecret;

        boolean fixedAppEnv = StringUtils.equalsAny(env, CommonConst.FIXED_APPID_ENV);
        Workflow workflow = getOne(Wrappers.lambdaQuery(Workflow.class).eq(Workflow::getFlowId, flowId));
        try {
            if (workflow == null) {
                appId = commonConfig.getAppId();
            } else {
                appId = workflow.getAppId();
            }
            AkSk aksk = appService.getAkSk(appId);
            apiKey = aksk.getApiKey();
            apiSecret = aksk.getApiSecret();
        } catch (Exception e) {
            if (fixedAppEnv) {
                appId = commonConfig.getAppId();
                apiKey = commonConfig.getApiKey();
                apiSecret = commonConfig.getApiSecret();
            } else {
                throw e;
            }
        }
        if (bizWorkflowData != null) {
            protocol = new FlowProtocol();
            // Fill app elements
            List<BizWorkflowNode> nodes = bizWorkflowData.getNodes();
            for (BizWorkflowNode node : nodes) {
                boolean notFlowNode = !node.getId().startsWith(WorkflowConst.NodeType.FLOW);
                BizNodeData bizNodeData = node.getData();
                String type = node.getType();
                try {
                    if (notFlowNode && fixedAppEnv) {
                        buidKeyInfo(bizNodeData);
                    } else {
                        bizNodeData.getNodeParam().put("appId", appId);
                        bizNodeData.getNodeParam().put("apiKey", apiKey);
                        bizNodeData.getNodeParam().put("apiSecret", apiSecret);
                    }
                    String source = bizNodeData.getNodeParam().getString("source");
                    if ("openai".equals(source)) {
                        Long modelId = bizNodeData.getNodeParam().getLong("modelId");
                        if (modelId != null) {
                            Model model = modelService.getById(modelId);
                            bizNodeData.getNodeParam().put("apiKey", model.getApiKey());
                            bizNodeData.getNodeParam().put("apiSecret", StringUtils.EMPTY);
                        }
                    }
                    String prefix = node.getId().split("::")[0];
                    // Agent node changes
                    checkAndEditData(bizNodeData, prefix);
                    // Knowledge base node new parameter passing logic
                    fixOnRepoNode(type, bizNodeData, prefix);
                    // Handle retry strategy information
                    JSONObject retryConfig = node.getData().getRetryConfig();
                    if (retryConfig != null) {
                        String customOutput = retryConfig.getString("customOutput");
                        try {
                            JSONObject parseObject = JSON.parseObject(customOutput);
                            retryConfig.put("customOutput", parseObject);
                        } catch (Exception e) {
                            log.info("Exception fallback strategy json parse error: {}", customOutput);
                        }
                    }

                } catch (BusinessException e) {
                    log.info("build remote param error: ", e);
                    throw e;
                } catch (Exception ignored) {

                    // if(!node.getId().startsWith(WorkflowConst.NodeType.FLOW) && StringUtils.equalsAny(env,
                    // CommonConst.FIXED_APPID_ENV_PRO)) {
                    buidKeyInfo(bizNodeData);
                    // }
                }
            }

            // Update core system flow

            // copy name desc
            BeanUtils.copyProperties(saveDto, protocol);

            // set id
            protocol.setId(flowId);

            // set data
            FlowProtocolData protocolData = new FlowProtocolData();
            protocolData.setEdges(bizEdgesToSysEdges(bizWorkflowData.getEdges()));
            protocolData.setNodes(bizNodesToSysNodes(bizWorkflowData.getNodes()));
            protocol.setData(protocolData);
        }
        return protocol;
    }

    private void buidKeyInfo(BizNodeData bizNodeData) {
        bizNodeData.getNodeParam().put("appId", commonConfig.getAppId());
        bizNodeData.getNodeParam().put("apiKey", commonConfig.getApiKey());
        bizNodeData.getNodeParam().put("apiSecret", commonConfig.getApiSecret());
    }

    private void fixOnRepoNode(String type, BizNodeData bizNodeData, String prefix) {
        if (WorkflowConst.NodeType.KNOWLEDGE.equals(prefix)) {
            JSONArray repoIds = bizNodeData.getNodeParam().getJSONArray("repoId");
            setDocIds(bizNodeData, repoIds);
        }
        if (WorkflowConst.NodeType.KNOWLEDGE_PRO.equals(prefix)) {
            // Change model address
            String serviceId = bizNodeData.getNodeParam().getString("serviceId");
            List<ConfigInfo> configInfos = configInfoMapper.selectList(new LambdaQueryWrapper<ConfigInfo>()
                    .eq(ConfigInfo::getCategory, "MCP_MODEL_API_REFLECT")
                    .eq(ConfigInfo::getCode, "mcp"));
            Optional<ConfigInfo> first = configInfos.stream().filter(s -> Objects.equals(serviceId, s.getName())).findFirst();
            if (first.isPresent()) {
                String apiUrl = first.get().getValue();
                bizNodeData.getNodeParam().put("url", apiUrl);
            }
            JSONArray repoIds = bizNodeData.getNodeParam().getJSONArray("repoIds");
            setDocIds(bizNodeData, repoIds);
        }
    }

    private void setDocIds(BizNodeData bizNodeData, JSONArray repoIds) {
        if (!CollUtil.isEmpty(repoIds)) {
            JSONArray docIds = new JSONArray();
            for (int i = 0; i < repoIds.size(); i++) {
                String repoId = repoIds.getString(i);
                List<FileInfoV2> fileInfoList = fileInfoV2Mapper.getFileInfoV2ByCoreRepoId(repoId);
                if (CollUtil.isNotEmpty(fileInfoList)) {
                    log.info("get file info list ,{}", fileInfoList);
                    List<String> uuids = CollUtil.getFieldValues(fileInfoList, "uuid", String.class);
                    docIds.addAll(uuids);
                }
            }
            bizNodeData.getNodeParam().put("docIds", docIds);
        }
    }

    private void checkAndEditData(BizNodeData bizNodeData, String prefix) {
        if (bizNodeData == null || bizNodeData.getNodeMeta() == null) {
            return;
        }
        if (!WorkflowConst.NodeType.AGENT.equals(prefix)) {
            return;
        }

        JSONObject nodeParam = bizNodeData.getNodeParam();
        JSONObject modelConfig = nodeParam.getJSONObject("modelConfig");
        if (modelConfig != null) {
            String serviceId = nodeParam.getString("serviceId");
            dealWithUrl(modelConfig, serviceId);
        }
        // Configure model corresponding URL
        // checkAndChangeConfig(bizNodeData, modelConfig);

        JSONObject plugin = nodeParam.getJSONObject("plugin");
        if (plugin == null) {
            log.warn("plugin config is missing");
            return;
        }
        JSONArray knowledgeArray = plugin.getJSONArray("knowledge");
        if (knowledgeArray == null || knowledgeArray.isEmpty()) {
            return;
        }
        for (int i = 0; i < knowledgeArray.size(); i++) {
            Object obj = knowledgeArray.get(i);
            if (!(obj instanceof Map)) {
                continue;
            }

            Map knowledgeObj = (Map) obj;
            Object matchObj = knowledgeObj.get("match");
            if (!(matchObj instanceof Map)) {
                continue;
            }

            Map match = (Map) matchObj;
            Object repoIdsObj = match.get("repoIds");
            if (!(repoIdsObj instanceof List)) {
                continue;
            }

            List<String> repoIds = (List<String>) repoIdsObj;
            if (repoIds.isEmpty()) {
                continue;
            }

            List<String> allDocIds = new ArrayList<>();
            for (String repoId : repoIds) {
                List<FileInfoV2> fileInfoList = fileInfoV2Mapper.getFileInfoV2ByCoreRepoId(repoId);
                if (CollUtil.isNotEmpty(fileInfoList)) {
                    List<String> docIds = CollUtil.getFieldValues(fileInfoList, "uuid", String.class);
                    allDocIds.addAll(docIds);
                    log.info("Found docIds for repoId {}: {}", repoId, docIds);
                }
            }

            if (!allDocIds.isEmpty()) {
                match.put("docIds", allDocIds);
            }
        }
    }

    private void dealWithUrl(JSONObject modelConfig, String serviceId) {
        if (modelConfig != null) {
            List<ConfigInfo> configInfos = configInfoMapper.selectList(new LambdaQueryWrapper<ConfigInfo>()
                    .eq(ConfigInfo::getCategory, "MCP_MODEL_API_REFLECT")
                    .eq(ConfigInfo::getCode, "mcp"));
            String api = modelConfig.getString("api");
            Optional<ConfigInfo> first = configInfos.stream().filter(s -> Objects.equals(serviceId, s.getName())).findFirst();
            if (first.isPresent()) {
                String apiUrl = first.get().getValue();
                modelConfig.put("api", apiUrl);
            } else {
                String apiUrl = api.replace("ws://", "http://").replace("wss://", "https://");
                modelConfig.put("api", apiUrl);
            }
        }
    }

    public void saveRemote(WorkflowReq saveDto, String flowId) {

        FlowProtocol protocol = buildWorkflowData(saveDto, flowId);
        String url = apiUrl.getWorkflow().concat(PROTOCOL_UPDATE_PATH).concat(flowId);
        JSONObject jsonObject = new JSONObject()
                .fluentPut("id", flowId)
                .fluentPut("app_id", saveDto.getAppId())
                .fluentPut("name", saveDto.getName())
                .fluentPut("description", saveDto.getDescription())
                .fluentPut("status", saveDto.getStatus());
        if (protocol != null) {
            jsonObject.fluentPut("data", protocol);
        }
        String body = jsonObject.toString();

        // body = StringEscapeUtils.unescapeJava(body);

        log.info("workflow protocol update, url = {}, body = {}", url, body);
        String response = OkHttpUtil.post(url, body);
        log.info("workflow protocol update, response = {}", response);
        Result<?> result = JSON.parseObject(response, Result.class);
        if (result.getCode() != 0) {
            throw new BusinessException(ResponseEnum.RESPONSE_FAILED, result.getMessage());
        }

        // Flow protocol temporary storage
        saveFlowProtocolTemp(flowId,
                saveDto.getData() == null ? null : JSON.toJSONString(saveDto.getData()),
                saveDto.getData() == null ? null : JSON.toJSONString(protocol.getData()));
    }

    private Property bizPropertyToProperty(BizProperty bizProperty) {
        Property property = new Property();

        // Array special handling
        if (bizProperty.getType().startsWith("array-")) {
            String[] split = bizProperty.getType().split("-");
            Property items = new Property();
            items.setType(split[1]);
            items.setProperties(bizPropertiesToPropertyMap(bizProperty.getProperties()));

            property.setItems(items);
            property.setType(split[0]);
        } else {
            property.setProperties(bizPropertiesToPropertyMap(bizProperty.getProperties()));
            property.setType(bizProperty.getType());
        }
        return property;
    }

    private Map<String, Property> bizPropertiesToPropertyMap(List<BizProperty> bizProperties) {
        if (CollectionUtils.isEmpty(bizProperties)) {
            return null;
        }

        Map<String, Property> propertyMap = new HashMap<>();

        for (BizProperty bizProperty : bizProperties) {
            Property property = new Property();
            if (bizProperty.getType().startsWith("array-")) {
                property = bizPropertyToProperty(bizProperty);
            } else {
                property.setType(bizProperty.getType());
                property.setProperties(bizPropertiesToPropertyMap(bizProperty.getProperties()));

                // if(bizProperty.getType().equals("object")) {
                // List<String> required = new ArrayList<>();
                // if(bizProperty.getProperties() != null) {
                // bizProperty.getProperties().forEach(bp -> {
                // if(bp.getRequired()) {
                // required.add(bp.getName());
                // }
                // });
                //
                // if(!required.isEmpty()) {
                // property.setRequired(required);
                // }
                // }
                // }
            }
            propertyMap.put(bizProperty.getName(), property);
        }

        return propertyMap;
    }

    public Object runCode(Object runCodeData) {
        String url = apiUrl.getWorkflow() + CODE_RUN_PATH;
        log.info("code run, url = {}, data = {}", url, runCodeData);
        String body = JSON.toJSONString(runCodeData);

        // body = StringEscapeUtils.unescapeJava(body);

        String resp = OkHttpUtil.post(url, body);
        log.info("code run, resp = {}", resp);
        return JSON.parseObject(resp, Result.class);
    }

    public Object getSquare(int current, int size, String search, Integer tagFlag, Integer tags) {
        Page<Workflow> page = new Page<>(current, size);
        String uid = null;
        if (tagFlag != null && tagFlag.equals(2)) {
            // Get user uid
            uid = dataPermissionCheckTool.getThreadLocalUidNoNull();
        }

        List<Workflow> workflows = workflowMapper.selectSuqareFlowList(page, uid, tags, bizConfig.getAdminUid(), search);

        page.setRecords(workflows);
        PageData<WorkflowVo> pageData = new PageData<>();
        List<WorkflowVo> workflowVos = new ArrayList<>(page.getRecords().size());
        page.getRecords().forEach(w -> {
            WorkflowVo vo = new WorkflowVo();
            BeanUtils.copyProperties(w, vo, "data", "publishedData");
            vo.setAddress(s3Util.getS3Prefix());
            vo.setColor(w.getAvatarColor());
            workflowVos.add(vo);
        });
        pageData.setPageData(workflowVos);
        pageData.setTotalCount(page.getTotal());
        return pageData;
    }

    private BizWorkflowData handleDataClone(String flowId, String data) {
        if (StringUtils.isBlank(data)) {
            return null;
        }
        BizWorkflowData bizWorkflowData = JSON.parseObject(data, BizWorkflowData.class);
        List<BizWorkflowNode> nodes = bizWorkflowData.getNodes();
        nodes.forEach(e -> {
            if (e.getData().getNodeParam().getString("flowId") != null) {
                if (!e.getId().startsWith(WorkflowConst.NodeType.FLOW)) {
                    e.getData().getNodeParam().put("flowId", flowId);
                }
            }
            if (Boolean.TRUE.equals(e.getData().getUpdatable())) {
                e.getData().setUpdatable(false);
            }
        });

        return bizWorkflowData;
    }

    private BizWorkflowData handleDataPublicCopy(String flowId, String appId, String data) {
        if (StringUtils.isBlank(data)) {
            return null;
        }
        BizWorkflowData bizWorkflowData = JSON.parseObject(data, BizWorkflowData.class);
        List<BizWorkflowNode> nodes = bizWorkflowData.getNodes();
        nodes.forEach(e -> {
            if (e.getData().getNodeParam().getString("flowId") != null) {
                if (!e.getId().startsWith(WorkflowConst.NodeType.FLOW)) {
                    e.getData().getNodeParam().put("flowId", flowId);
                }
                e.getData().getNodeParam().put("appId", appId);
            }
        });

        return bizWorkflowData;
    }

    @Transactional(rollbackFor = Exception.class)
    public Object publicCopy(WorkflowReq req) {
        if (req.getId() == null) {
            return ApiResult.error(ResponseEnum.BAD_REQUEST);
        }
        req.setAppId(commonConfig.getAppId());
        String appId = req.getAppId();
        // Validate workflow ID
        Workflow prototype = getById(req.getId());
        if (prototype == null) {
            throw new BusinessException(ResponseEnum.WORKFLOW_NOT_EXIST);
        }
        if (!prototype.getIsPublic() && !Objects.equals(prototype.getUid(), bizConfig.getAdminUid())) {
            throw new BusinessException(ResponseEnum.WORKFLOW_NOT_PUBLIC);
        }

        // Force set to unpublished
        prototype.setStatus(WorkflowConst.Status.UNPUBLISHED);

        // Call core system to get flow ID
        WorkflowReq flowReq = new WorkflowReq();
        BeanUtils.copyProperties(prototype, flowReq);
        flowReq.setAppId(appId);
        ApiResult<String> addResult = callProtocolAdd(flowReq);
        if (addResult.code() != 0) {
            return addResult;
        }
        String nFlowId = addResult.data();

        // Update core system
        BizWorkflowData bizWorkflowData = handleDataPublicCopy(nFlowId, appId, prototype.getData());

        // Update core system
        if (
        // workflow.getStatus() == WorkflowConst.Status.PUBLISHED &&
        bizWorkflowData != null) {
            flowReq.setData(bizWorkflowData);
            saveRemote(flowReq, nFlowId);
        }

        Workflow replica = new Workflow();
        BeanUtils.copyProperties(prototype, replica);

        replica.setId(null);
        replica.setAppId(req.getAppId());
        replica.setUid(UserInfoManagerHandler.getUserId());
        replica.setCreateTime(new Date());
        replica.setUpdateTime(new Date());
        replica.setFlowId(addResult.data());
        replica.setData(JSON.toJSONString(bizWorkflowData));
        replica.setPublishedData(JSON.toJSONString(handleDataPublicCopy(nFlowId, appId, prototype.getPublishedData())));
        replica.setAppUpdatable(false);
        replica.setOrder(0);
        replica.setIsPublic(false);
        save(replica);

        WorkflowVo vo = new WorkflowVo();
        BeanUtils.copyProperties(replica, vo);

        if (bizWorkflowData != null) {
            vo.setIoInversion(getIoTrans(bizWorkflowData.getNodes()));
        }
        return vo;
    }

    private JSONObject getIoTrans(List<BizWorkflowNode> nodes) {
        if (nodes.isEmpty()) {
            return null;
        }
        // Handle IO
        BizWorkflowNode startNode = nodes.get(0);
        BizWorkflowNode endNode = nodes.get(1);
        if (!startNode.getId().startsWith(WorkflowConst.NodeType.START)) {
            for (BizWorkflowNode node : nodes) {
                if (node.getId().startsWith(WorkflowConst.NodeType.START)) {
                    startNode = node;
                }
            }
        }
        if (!endNode.getId().startsWith(WorkflowConst.NodeType.END)) {
            for (BizWorkflowNode node : nodes) {
                if (node.getId().startsWith(WorkflowConst.NodeType.END)) {
                    endNode = node;
                }
            }
        }
        List<BizInputOutput> inputs = endNode.getData().getInputs();
        List<BizInputOutput> outputs = startNode.getData().getOutputs();
        List<BizInputOutput> outputsTransToInputs = new ArrayList<>(outputs.size());
        List<BizInputOutput> inputsTransToOutputs = new ArrayList<>(inputs.size());
        Integer outputMode = endNode.getData().getNodeParam().getInteger("outputMode");
        if (outputMode == 0) {
            inputs.forEach(i -> {
                BizInputOutput o = new BizInputOutput();
                o.setId(UUID.randomUUID().toString());
                o.setName(i.getName());
                BizSchema s = new BizSchema();
                s.setType(i.getSchema().getType());
                o.setSchema(s);
                inputsTransToOutputs.add(o);
            });
        } else if (outputMode == 1) {
            Arrays.asList("content", "reasoning_content").forEach(i -> {
                BizInputOutput o = new BizInputOutput();
                o.setId(UUID.randomUUID().toString());
                o.setName(i);
                BizSchema s = new BizSchema();
                s.setType("string");
                o.setSchema(s);
                inputsTransToOutputs.add(o);
            });
        }


        outputs.forEach(o -> {
            BizInputOutput i = new BizInputOutput();
            i.setId(UUID.randomUUID().toString());
            i.setName(o.getName());
            BizSchema s = new BizSchema();
            s.setType(o.getSchema().getType());
            BizValue v = new BizValue();
            JSONObject content = new JSONObject();
            content.put("id", UUID.randomUUID().toString());
            content.put("name", "");
            v.setContent(content);
            v.setType("ref");
            s.setValue(v);
            i.setSchema(s);
            i.setRequired(o.getRequired());
            outputsTransToInputs.add(i);
        });

        JSONObject ioInv = new JSONObject();
        ioInv.put("inputs", outputsTransToInputs);
        ioInv.put("outputs", inputsTransToOutputs);

        return ioInv;
    }

    private static final List<String> DEFAULT_KEYS = Arrays.asList(
            "text", "content", "value", "title", "name", "message", "prompt",
            "url", "fileUrl", "path");

    public static boolean isInputContentEmpty(Object content) {
        return isInputContentEmpty(content, DEFAULT_KEYS);
    }

    public static boolean isInputContentEmpty(Object content, Collection<String> candidateKeys) {
        if (content == null) {
            return true;
        }

        // Pure string
        if (content instanceof CharSequence) {
            return StringUtils.isBlank((CharSequence) content);
        }

        // fastjson JSONObject
        if (content instanceof JSONObject) {
            return isJsonObjEmpty((JSONObject) content, candidateKeys);
        }

        // General Map
        if (content instanceof Map) {
            return isJsonObjEmpty(new JSONObject((Map<String, Object>) content), candidateKeys);
        }

        // Collection/array: if any element is non-empty, consider the whole as non-empty
        if (content instanceof Collection) {
            for (Object o : (Collection<?>) content) {
                if (!isInputContentEmpty(o, candidateKeys)) {
                    return false;
                }
            }
            return true;
        }
        if (content.getClass().isArray()) {
            int len = java.lang.reflect.Array.getLength(content);
            for (int i = 0; i < len; i++) {
                Object o = java.lang.reflect.Array.get(content, i);
                if (!isInputContentEmpty(o, candidateKeys)) {
                    return false;
                }
            }
            return true;
        }
        // Other objects: try to serialize to JSON and then judge
        try {
            JSONObject jo = JSON.parseObject(JSON.toJSONString(content));
            return isJsonObjEmpty(jo, candidateKeys);
        } catch (Exception ignore) {
            return StringUtils.isBlank(String.valueOf(content));
        }
    }

    private static boolean isJsonObjEmpty(JSONObject jo, Collection<String> candidateKeys) {
        if (jo == null || jo.isEmpty()) {
            return true;
        }

        // 1) First check if there are non-empty strings in candidate keys
        for (String key : candidateKeys) {
            String v = jo.getString(key);
            if (StringUtils.isNotBlank(v)) {
                return false;
            }
        }

        // 2) Common "default" container: string or array/object
        Object def = jo.get("default");
        if (def != null && !isInputContentEmpty(def, candidateKeys)) {
            return false;
        }

        // 3) If any "direct string value" is non-empty, also consider as non-empty (avoid missed judgments
        // due to inconsistent key names)
        for (Map.Entry<String, Object> e : jo.entrySet()) {
            Object v = e.getValue();
            if (v instanceof CharSequence && StringUtils.isNotBlank((CharSequence) v)) {
                return false;
            }
        }
        return true;
    }

    private JSONObject handleNodeParam(JSONObject nodeParam) {
        // Remove redundant information for core system
        nodeParam.remove("configs");
        // Special handling
        Integer topN = nodeParam.getInteger("topN");
        if (topN != null) {
            nodeParam.put("topN", topN.toString());
        }
        // Convert patchId
        String patchId = nodeParam.getString("patchId");
        if (StringUtils.isNotEmpty(patchId)) {
            String domain = nodeParam.getString("domain");
            // Some models patch id = 0 fallback
            ConfigInfo patchId0Cfg = configInfoMapper.getByCategoryAndCode("PATCH_ID", "0");
            List<String> pathId0 = StrUtil.split(patchId0Cfg.getValue(), ",");
            if (!pathId0.contains(domain) && "0".equals(patchId)) {
                nodeParam.put("patch_id", new ArrayList<>());
            } else {
                nodeParam.put("patch_id", Collections.singletonList(patchId));
            }
            nodeParam.remove("patchId");
        }
        // Database dbId string to long
        String dbId = nodeParam.getString("dbId");
        if (StringUtils.isNotEmpty(dbId)) {
            Long dbIdLong = Long.parseLong(dbId);
            nodeParam.put("dbId", dbIdLong);
        }

        return nodeParam;
    }

    public Object getAutoAddEvalSetData(Long id) {
        List<EvalSet> setList = evalSetMapper.selectList(Wrappers.lambdaQuery(EvalSet.class)
                .eq(EvalSet::getApplicationId, id)
                .eq(EvalSet::getApplicationType, CommonConst.ApplicationType.WORKFLOW));

        if (CollectionUtils.isEmpty(setList)) {
            return ApiResult.success();
        }

        List<EvalSetVerDataVo> voList = new ArrayList<>();
        setList.forEach(evalSet -> {
            List<EvalSetVer> evalSetVers = evalSetVerMapper.selectList(Wrappers.lambdaQuery(EvalSetVer.class)
                    .eq(EvalSetVer::getEvalSetId, evalSet.getId())
                    .eq(EvalSetVer::getDeleted, false)
                    .orderByDesc(EvalSetVer::getUpdateTime));
            if (CollectionUtils.isEmpty(evalSetVers)) {
                return;
            }
            List<Long> verIds = evalSetVers.stream().map(EvalSetVer::getId).collect(Collectors.toList());
            List<EvalSetVerData> evalSetVerDataList = evalSetVerDataMapper.selectList(Wrappers.lambdaQuery(EvalSetVerData.class)
                    .in(EvalSetVerData::getEvalSetVerId, verIds)
                    .eq(EvalSetVerData::getDeleted, false)
                    .eq(EvalSetVerData::getAutoAdd, true)
                    .orderByDesc(EvalSetVerData::getCreateTime));

            evalSetVerDataList.forEach(d -> {
                EvalSetVerDataVo vo = new EvalSetVerDataVo();
                BeanUtils.copyProperties(d, vo);
                vo.setAnswer(d.getExpectedAnswer());
                voList.add(vo);
            });
        });

        return voList;
    }

    public Object getNodeTemplate(Integer source) {
        int code = CommonConst.PlatformCode.COMMON;

        List<ConfigInfo> workflowNodeTemplate = configInfoMapper.selectList(Wrappers.lambdaQuery(ConfigInfo.class)
                .eq(ConfigInfo::getCategory, "WORKFLOW_NODE_TEMPLATE")
                .eq(ConfigInfo::getIsValid, 1)
                .like(ConfigInfo::getCode, Integer.toString(code)));

        if ("pre".equals(env)) {
            workflowNodeTemplate = configInfoMapper.selectList(Wrappers.lambdaQuery(ConfigInfo.class)
                    .eq(ConfigInfo::getCategory, "WORKFLOW_NODE_TEMPLATE_PRE")
                    .eq(ConfigInfo::getIsValid, 1)
                    .like(ConfigInfo::getCode, Integer.toString(code)));
        }
        ConfigInfo spaceSwitchNode = configInfoMapper.selectOne(new LambdaQueryWrapper<ConfigInfo>().eq(ConfigInfo::getCategory, "SPACE_SWITCH_NODE"));
        if (spaceSwitchNode != null
                && StringUtils.isNotBlank(spaceSwitchNode.getValue())
                && SpaceInfoUtil.getSpaceId() != null) {
            Set<String> filter = Arrays.stream(spaceSwitchNode.getValue().split(","))
                    .map(String::trim)
                    .filter(StringUtils::isNotBlank)
                    .collect(Collectors.toSet());
            if (!filter.isEmpty() && CollUtil.isNotEmpty(workflowNodeTemplate)) {
                workflowNodeTemplate.removeIf(configInfo -> {
                    try {
                        JSONObject obj = JSONObject.parseObject(configInfo.getValue());
                        String idType = obj == null ? null : obj.getString("idType");
                        // Remove if matched
                        return StringUtils.isNotBlank(idType) && filter.contains(idType);
                    } catch (Exception ex) {
                        return false;
                    }
                });
            }
        }
        Map<String, List<ConfigInfo>> groupByType = workflowNodeTemplate.stream().collect(Collectors.groupingBy(ConfigInfo::getName, LinkedHashMap::new, Collectors.toList()));
        JSONArray ret = new JSONArray(groupByType.size());
        groupByType.forEach((k, v) -> {
            JSONObject jsonObject = new JSONObject();
            jsonObject.put("name", k);
            JSONArray nodes = new JSONArray(v.size());
            v.forEach(config -> {
                nodes.add(JSONObject.parseObject(config.getValue()));
            });
            jsonObject.put("nodes", nodes);
            ret.add(jsonObject);
        });
        return ret;
    }

    public Object clearDialog(Long workflowId, Integer type) {
        return workflowDialogMapper.update(Wrappers.lambdaUpdate(WorkflowDialog.class)
                .eq(WorkflowDialog::getWorkflowId, workflowId)
                .eq(WorkflowDialog::getType, type)
                .set(WorkflowDialog::getDeleted, true));
    }

    public Object canPublishSetNot(Long id) {
        Workflow workflow = getById(id);
        WorkflowReq req = new WorkflowReq();
        // req.setStatus(WorkflowConst.Status.UNPUBLISHED);
        req.setAppId(workflow.getAppId());

        // saveRemote(req, workflow.getFlowId());

        return update(Wrappers.lambdaUpdate(Workflow.class)
                .eq(Workflow::getId, id)
                .set(Workflow::getCanPublish, false)
        // .set(Workflow::getStatus, WorkflowConst.Status.UNPUBLISHED)
        );
    }

    public boolean isSimpleIo(Long id) {
        Workflow workflow = getById(id);
        dataPermissionCheckTool.checkWorkflowBelong(workflow, SpaceInfoUtil.getSpaceId());

        String data = workflow.getData();
        if (data == null) {
            throw new BusinessException(ResponseEnum.WORKFLOW_PROTOCOL_EMPTY);
        }

        // Get start and end nodes
        BizWorkflowData bizWorkflowData = JSON.parseObject(data, BizWorkflowData.class);
        List<BizWorkflowNode> nodes = bizWorkflowData.getNodes();
        BizWorkflowNode start = nodes.get(0);
        BizWorkflowNode end = nodes.get(1);
        if (!start.getId().startsWith(WorkflowConst.NodeType.START)) {
            for (BizWorkflowNode node : nodes) {
                if (node.getId().startsWith(WorkflowConst.NodeType.START)) {
                    start = node;
                    break;
                }
            }
        }
        if (!end.getId().startsWith(WorkflowConst.NodeType.END)) {
            for (BizWorkflowNode node : nodes) {
                if (node.getId().startsWith(WorkflowConst.NodeType.END)) {
                    end = node;
                    break;
                }
            }
        }

        boolean inputSimple = true;
        boolean outputSimple = true;

        List<BizInputOutput> startO = start.getData().getOutputs();
        for (BizInputOutput so : startO) {
            if ("object".equals(so.getSchema().getType()) || so.getSchema().getType().startsWith("array")) {
                inputSimple = false;
                break;
            }
        }

        List<BizInputOutput> endI = end.getData().getInputs();
        for (BizInputOutput ei : endI) {
            if ("object".equals(ei.getSchema().getType()) || ei.getSchema().getType().startsWith("array")) {
                outputSimple = false;
                break;
            }
        }

        return inputSimple && outputSimple;
    }

    public SseEmitter sseChat(ChatBizReq bizReq) {
        try {
            if (bizReq.getOutputType() == null) {
                bizReq.setOutputType(1);
            }

            // Handle input null values
            bizReq.getInputs().forEach((k, v) -> {
                if (v == null) {
                    bizReq.getInputs().remove(k);
                }
            });

            // Data validation
            String flowId = bizReq.getFlowId();
            Assert.notNull(bizReq);
            Assert.notEmpty(flowId);
            Assert.notNull(bizReq.getInputs());

            String uid = UserInfoManagerHandler.getUserId();

            // if (SseEmitterUtil.exist(uid)) {
            // return SseEmitterUtil.newSseAndSendMessageClose("Too fast request! Please try again later");
            // }

            Workflow workflow = getOne(Wrappers.lambdaQuery(Workflow.class).eq(Workflow::getFlowId, flowId));
            Assert.notNull(workflow);
            AkSk akSk = appService.remoteCallAkSk(workflow.getAppId());
            Assert.notNull(akSk);
            Assert.notEmpty(akSk.getApiKey());
            Assert.notEmpty(akSk.getApiSecret());

            // Multi-round conversation validation
            BizWorkflowData bizWorkflowData = JSON.parseObject(workflow.getData(), BizWorkflowData.class);
            List<BizWorkflowNode> nodes = bizWorkflowData.getNodes();
            boolean isEnabled = false;
            int maxRounds = 0;
            for (BizWorkflowNode node : nodes) {
                if (isMultiRoundEnabled(node)) {
                    isEnabled = true;
                    maxRounds = Math.max(maxRounds, getMaxRounds(node));
                }
            }
            Map<String, String> headerMap = new HashMap<>();
            headerMap.put(HttpHeaders.AUTHORIZATION, akSk.getApiKey() + ":" + akSk.getApiSecret());
            headerMap.put("X-Consumer-Username", workflow.getAppId());

            ChatSysReq sysReq = new ChatSysReq();
            sysReq.setFlowId(flowId);
            sysReq.setParameters(bizReq.getInputs());
            sysReq.setUid(uid);
            sysReq.setVersion(bizReq.getVersion());
            // Support multi-round conversation, construct params
            if (isEnabled) {
                buildParams(bizReq, maxRounds, sysReq);
            }
            String url = apiUrl.getWorkflow().concat("/workflow/v1/debug/chat/completions");
            String reqBody = JacksonUtil.toJSONString(sysReq, JacksonUtil.NON_NULL_OBJECT_MAPPER);

            SseEmitter sseEmitter = SseEmitterUtil.create(bizReq.getChatId(), 1800_000L);
            log.info("[SSE]workflow chat url = {}, headers = {}, reqBody = {}", url, headerMap, reqBody);
            WorkflowSseEventSourceListener listener = new WorkflowSseEventSourceListener(flowId, bizReq.getChatId(), bizReq.getOutputType(), bizReq.getPromptDebugger(), bizReq.getVersion());
            OkHttpUtil.connectRealEventSource(url, headerMap, reqBody, listener);

            if (Boolean.TRUE.equals(bizReq.getRegen())) {
                WorkflowDialog latestDialog = workflowDialogMapper.selectOne(Wrappers.lambdaQuery(WorkflowDialog.class).eq(WorkflowDialog::getWorkflowId, workflow.getId()).orderByDesc(WorkflowDialog::getCreateTime).last("limit 1"));
                workflowDialogMapper.delete(Wrappers.lambdaQuery(WorkflowDialog.class).eq(WorkflowDialog::getId, latestDialog.getId()));
            }

            return sseEmitter;
        } catch (Exception e) {
            log.error("SSE error occurred: {}", e.getMessage(), e);
            return SseEmitterUtil.newSseAndSendMessageClose(new ChatResponse(e.getMessage()));
        }
    }

    public SseEmitter sseChatResume(ChatResumeReq bizReq) {
        try {
            if (bizReq.getOutputType() == null) {
                bizReq.setOutputType(1);
            }
            // Data validation
            String eventId = bizReq.getEventId();
            Assert.notNull(bizReq);
            Assert.notEmpty(eventId);
            Assert.notNull(bizReq.getContent());

            String uid = UserInfoManagerHandler.getUserId();

            // if (SseEmitterUtil.exist(uid)) {
            // return SseEmitterUtil.newSseAndSendMessageClose("Too fast request! Please try again later");
            // }
            String flowId = bizReq.getFlowId();
            Workflow workflow = getOne(Wrappers.lambdaQuery(Workflow.class).eq(Workflow::getFlowId, flowId));
            Assert.notNull(workflow);
            AkSk akSk = appService.remoteCallAkSk(workflow.getAppId());
            Assert.notNull(akSk);
            Assert.notEmpty(akSk.getApiKey());
            Assert.notEmpty(akSk.getApiSecret());

            Map<String, String> headerMap = new HashMap<>();
            headerMap.put(HttpHeaders.AUTHORIZATION, akSk.getApiKey() + ":" + akSk.getApiSecret());
            headerMap.put("X-Consumer-Username", workflow.getAppId());

            JSONObject sysReq = new JSONObject();
            sysReq.put("event_id", bizReq.getEventId());
            sysReq.put("event_type", bizReq.getEventType());
            sysReq.put("content", bizReq.getContent());

            String url = apiUrl.getWorkflow().concat("/workflow/v1/debug/resume");
            String reqBody = JacksonUtil.toJSONString(sysReq, JacksonUtil.NON_NULL_OBJECT_MAPPER);

            SseEmitter sseEmitter = SseEmitterUtil.create(bizReq.getEventId(), 1800_000L);
            log.info("[SSE]workflow resume url = {}, headers = {}, reqBody = {}", url, headerMap, reqBody);
            WorkflowSseEventSourceListener listener = new WorkflowSseEventSourceListener(flowId, bizReq.getEventId(), bizReq.getOutputType(), bizReq.getPromptDebugger(), bizReq.getVersion());
            OkHttpUtil.connectRealEventSource(url, headerMap, reqBody, listener);

            if (Boolean.TRUE.equals(bizReq.getRegen())) {
                WorkflowDialog latestDialog = workflowDialogMapper.selectOne(Wrappers.lambdaQuery(WorkflowDialog.class).eq(WorkflowDialog::getWorkflowId, workflow.getId()).orderByDesc(WorkflowDialog::getCreateTime).last("limit 1"));
                workflowDialogMapper.delete(Wrappers.lambdaQuery(WorkflowDialog.class).eq(WorkflowDialog::getId, latestDialog.getId()));
            }
            return sseEmitter;
        } catch (Exception e) {
            log.error("workflow resume SSE error occurred: {}", e.getMessage(), e);
            return SseEmitterUtil.newSseAndSendMessageClose(new ChatResponse(e.getMessage()));
        }
    }

    /**
     * Construct multi-round conversation parameters
     *
     * @param bizReq
     * @param maxRounds
     * @param sysReq
     */
    private void buildParams(ChatBizReq bizReq, int maxRounds, ChatSysReq sysReq) {
        List<WorkflowDialog> metaData = workflowDialogMapper.selectList(new LambdaQueryWrapper<WorkflowDialog>()
                .eq(WorkflowDialog::getChatId, bizReq.getChatId())
                .orderByDesc(WorkflowDialog::getCreateTime)
                .last("limit " + maxRounds));
        List<WorkflowDialog> workflowDialogs = CollUtil.reverse(metaData);
        if (!workflowDialogs.isEmpty()) {
            List<JSONObject> historyList = new ArrayList<>(workflowDialogs.size() * 2);
            for (WorkflowDialog workflowDialog : workflowDialogs) {
                JSONArray questionItems = JSONArray.parseArray(workflowDialog.getQuestionItem());
                JSONObject questionObj = JSONArray.parseArray(workflowDialog.getQuestionItem()).getJSONObject(0);
                JSONObject historyInfoInput = new JSONObject();
                historyInfoInput.put("role", "user");
                historyInfoInput.put("content_type", "string".equals(questionObj.getString("type")) ? "text" : questionObj.getString("type"));
                historyInfoInput.put("content", questionObj.getString("default"));
                historyList.add(historyInfoInput);
                for (Object questionItem : questionItems) {
                    JSONObject questionObj2 = JSONObject.parseObject(questionItem.toString());
                    if ("image".equals(questionObj2.getString("allowedFileType"))) {
                        JSONObject historyInfoInput2 = new JSONObject();
                        JSONArray aDefault = questionObj2.getJSONArray("default");
                        if (aDefault != null && !aDefault.isEmpty()) {
                            String content = aDefault.getJSONObject(0).getString("url");
                            historyInfoInput2.put("role", "user");
                            historyInfoInput2.put("content_type", "image");
                            historyInfoInput2.put("content", content);
                            historyList.add(historyInfoInput2);
                        }
                        break;
                    }
                }
                JSONObject historyInfoOutput = new JSONObject();
                historyInfoOutput.put("role", "assistant");
                historyInfoOutput.put("content_type", "text");
                historyInfoOutput.put("content", workflowDialog.getAnswerItem());
                historyList.add(historyInfoOutput);

            }
            sysReq.setHistory(historyList);
        }
        sysReq.setChatId(bizReq.getChatId());
    }

    /**
     * Whether multi-round conversation is supported
     *
     * @param node
     * @return
     */
    private boolean isMultiRoundEnabled(BizWorkflowNode node) {
        BizNodeData data = node.getData();
        String prefix = node.getId().split("::")[0];
        ConfigInfo configInfo = configInfoMapper.selectOne(new LambdaQueryWrapper<ConfigInfo>()
                .eq(ConfigInfo::getCategory, "MULTI_ROUNDS_ALIAS_NAME")
                .eq(ConfigInfo::getIsValid, 1));
        List<String> list = Arrays.asList(configInfo.getValue().split(","));
        // Currently only decision nodes and large model nodes support enabling multi-round conversation
        if (!CollUtil.contains(list, prefix)) {
            return false;
        }
        JSONObject nodeParam = data.getNodeParam();
        if (nodeParam == null) {
            return false;
        }
        JSONObject enableChatHistoryV2 = nodeParam.getJSONObject("enableChatHistoryV2");
        if (enableChatHistoryV2 != null) {
            // Whether multi-round conversation is enabled
            Boolean enable = enableChatHistoryV2.getBoolean("isEnabled");
            return Boolean.TRUE.equals(enable);
        }
        return false;
    }

    /**
     * Get number of rounds
     *
     * @param node
     * @return
     */
    private int getMaxRounds(BizWorkflowNode node) {
        BizNodeData data = node.getData();
        JSONObject nodeParam = data.getNodeParam();
        if (nodeParam == null) {
            return 0;
        }
        JSONObject enableChatHistoryV2 = nodeParam.getJSONObject("enableChatHistoryV2");
        if (enableChatHistoryV2 == null) {
            return 0;
        }
        Integer rounds = enableChatHistoryV2.getInteger("rounds");
        return rounds == null ? 0 : rounds;
    }

    public Object trainableNodes(Long id) {
        Workflow workflow = getById(id);
        if (workflow == null) {
            throw new BusinessException(ResponseEnum.WORKFLOW_NOT_EXIST);
        }
        if (StringUtils.isBlank(workflow.getData())) {
            throw new BusinessException(ResponseEnum.WORKFLOW_PROTOCOL_EMPTY);
        }

        List<NodeSimpleDto> llm = new ArrayList<>();
        List<NodeSimpleDto> intent = new ArrayList<>();
        List<NodeSimpleDto> vExtractor = new ArrayList<>();

        BizWorkflowData bizWorkflowData = JSON.parseObject(workflow.getData(), BizWorkflowData.class);
        bizWorkflowData.getNodes().forEach(n -> {
            if (n.getId().startsWith(WorkflowConst.NodeType.SPARK_LLM)) {
                llm.add(new NodeSimpleDto(n.getId(), n.getData().getLabel(), n.getData().getNodeParam().getString("domain")));
            }
            if (n.getId().startsWith(WorkflowConst.NodeType.DECISION_MAKING)) {
                intent.add(new NodeSimpleDto(n.getId(), n.getData().getLabel(), n.getData().getNodeParam().getString("domain")));
            }
            if (n.getId().startsWith(WorkflowConst.NodeType.EXTRACTOR_PARAMETER)) {
                vExtractor.add(new NodeSimpleDto(n.getId(), n.getData().getLabel(), n.getData().getNodeParam().getString("domain")));
            }
        });

        List<NodeSimpleDto> all = new ArrayList<>(llm.size() + intent.size() + vExtractor.size());
        all.addAll(llm);
        all.addAll(intent);
        // all.addAll(vExtractor);

        return all;
    }


    public Object evalPageFirstTime(Long id) {
        return update(Wrappers.lambdaUpdate(Workflow.class)
                .eq(Workflow::getId, id)
                .set(Workflow::getEvalPageFirstTime, false));
    }

    public Object getInputsType(String flowId) {
        Workflow workflow = getOne(Wrappers.lambdaQuery(Workflow.class).eq(Workflow::getFlowId, flowId));
        if (workflow == null) {
            log.error("Flow not found, id=" + flowId);
            throw new BusinessException(ResponseEnum.NO_WORKFLOW);
        }

        String data = workflow.getData();
        if (StringUtils.isBlank(data)) {
            log.error("Workflow protocol is empty, id=" + flowId);
            throw new BusinessException(ResponseEnum.WORKFLOW_PROTOCOL_EMPTY);
        }

        BizWorkflowData bizWorkflowData = JSON.parseObject(data, BizWorkflowData.class);
        List<BizWorkflowNode> nodes = bizWorkflowData.getNodes();
        for (BizWorkflowNode node : nodes) {
            if (node.getId().startsWith(WorkflowConst.NodeType.START)) {
                // Parse input
                List<BizInputOutput> outputs = node.getData().getOutputs();
                return JsonConverter.flowInputTypeConvert(JSON.toJSONString(outputs));
            }
        }

        throw new BusinessException(ResponseEnum.PARSE_INPUT_PARAM_TYPE_FAILED);
    }

    public Object getInputsInfo(String flowId) {
        Workflow workflow = getOne(Wrappers.lambdaQuery(Workflow.class).eq(Workflow::getFlowId, flowId));
        if (workflow == null) {
            log.error("Flow not found, id=" + flowId);
            throw new BusinessException(ResponseEnum.NO_WORKFLOW);
        }

        String data = workflow.getData();
        if (StringUtils.isBlank(data)) {
            log.error("Workflow protocol is empty, id=" + flowId);
            throw new BusinessException(ResponseEnum.WORKFLOW_PROTOCOL_EMPTY);
        }

        BizWorkflowData bizWorkflowData = JSON.parseObject(data, BizWorkflowData.class);
        List<BizWorkflowNode> nodes = bizWorkflowData.getNodes();
        for (BizWorkflowNode node : nodes) {
            if (node.getId().startsWith(WorkflowConst.NodeType.START)) {
                // Parse input
                List<BizInputOutput> outputs = node.getData().getOutputs();
                return ApiResult.success(JSON.toJSONString(outputs));
            }
        }
        throw new BusinessException(ResponseEnum.PARSE_INPUT_PARAM_TYPE_FAILED);
    }

    public Object uploadFile(MultipartFile[] files, String flowId) {
        if (files == null || files.length == 0) {
            return ApiResult.error(ResponseEnum.FILE_EMPTY);
        }
        Workflow workflow = getOne(Wrappers.lambdaQuery(Workflow.class).eq(Workflow::getFlowId, flowId));
        Assert.notNull(workflow);
        AkSk akSk = appService.remoteCallAkSk(workflow.getAppId());
        Assert.notNull(akSk);
        Assert.notEmpty(akSk.getApiKey());
        Assert.notEmpty(akSk.getApiSecret());

        List<String> urls = new LinkedList<>();
        for (MultipartFile file : files) {
            String url = coreSystemService.uploadFile(file, akSk.getApiKey(), akSk.getApiSecret());
            urls.add(url);
        }
        return urls;
    }

    public Object getModelInfo(WorkflowModelReq workflowReq) {
        if (workflowReq == null || StringUtils.isBlank(workflowReq.getFlowId()) || workflowReq.getType() == null) {
            return ApiResult.error(ResponseEnum.PARAM_ERROR);
        }
        if (!workflowReq.getType().equals(0) && !workflowReq.getType().equals(1)) {
            return ApiResult.error(ResponseEnum.PARAM_ERROR);
        }
        List<WorkflowModelVo> result = new ArrayList<>();
        Workflow workflow = getOne(Wrappers.lambdaQuery(Workflow.class).eq(Workflow::getFlowId, workflowReq.getFlowId()));
        if (workflow == null) {
            throw new BusinessException(ResponseEnum.WORKFLOW_NOT_EXIST);
        }
        BizWorkflowData bizWorkflowData;
        // Parse flow protocol
        if (workflowReq.getType().equals(0)) {
            bizWorkflowData = JSON.parseObject(workflow.getData(), BizWorkflowData.class);
        } else {
            if (workflow.getPublishedData() == null) {
                throw new BusinessException(ResponseEnum.WORKFLOW_NOT_PUBLISH);
            }
            bizWorkflowData = JSON.parseObject(workflow.getPublishedData(), BizWorkflowData.class);
        }
        for (BizWorkflowNode node : bizWorkflowData.getNodes()) {
            if (node.getId() != null && node.getId().startsWith("spark-llm")) {
                WorkflowModelVo workflowModelVo = new WorkflowModelVo();
                workflowModelVo.setNodeId(node.getId());
                workflowModelVo.setNodeName(node.getData().getNodeParam().getString("domain"));
                result.add(workflowModelVo);
            }
        }
        return result;
    }

    public Object getNodeErrorInfo(WorkflowModelErrorReq workflowModelErrorReq) {
        if (workflowModelErrorReq == null || StringUtils.isBlank(workflowModelErrorReq.getFlowId())) {
            return ApiResult.error(ResponseEnum.PARAM_ERROR);
        }
        // Query flow corresponding node running data
        List<WorkflowErrorModelVo> errorModelVo = nodeInfoMapper.getNodeErrorInfo(workflowModelErrorReq);
        for (WorkflowErrorModelVo modelVo : errorModelVo) {
            long callNum = nodeInfoMapper.getNodeCallNum(workflowModelErrorReq, modelVo.getNodeName());
            modelVo.setCallNum(callNum);
            List<String> sidList = nodeInfoMapper.getSidList(workflowModelErrorReq, modelVo.getNodeName());
            modelVo.setErrorNum((long) sidList.size());
            List<WorkflowErrorVo> errorInfo = new ArrayList<>();
            if (!sidList.isEmpty()) {
                errorInfo = chatInfoMapper.getErrorBySidList(sidList);
            }
            modelVo.setInfo(errorInfo);
        }
        return errorModelVo;
    }

    public Object getUserFeedbackErrorInfo(WorkflowModelErrorReq workflowModelErrorReq) {
        if (workflowModelErrorReq == null || StringUtils.isBlank(workflowModelErrorReq.getFlowId())) {
            return ApiResult.error(ResponseEnum.PARAM_ERROR);
        }
        // Query flow corresponding user feedback running data
        return chatInfoMapper.getUserFeedBackErrorInfo(workflowModelErrorReq);
    }


    public Object getAgentStrategy() {
        List<ConfigInfo> configInfos = configInfoMapper.selectList(new LambdaQueryWrapper<ConfigInfo>()
                .eq(ConfigInfo::getCategory, "WORKFLOW_AGENT_STRATEGY")
                .eq(ConfigInfo::getCode, "agentStrategy"));
        List<AgentStrategy> result = new ArrayList<>();
        for (ConfigInfo configInfo : configInfos) {
            AgentStrategy agentStrategy = new AgentStrategy();
            agentStrategy.setName(configInfo.getName());
            agentStrategy.setDescription(configInfo.getValue());
            agentStrategy.setCode(Integer.valueOf(configInfo.getRemarks()));
            result.add(agentStrategy);
        }
        return result;
    }

    public Object getKnowledgeProStrategy() {
        List<ConfigInfo> configInfos = configInfoMapper.selectList(new LambdaQueryWrapper<ConfigInfo>()
                .eq(ConfigInfo::getCategory, "WORKFLOW_KNOWLEDGE_PRO_STRATEGY")
                .eq(ConfigInfo::getCode, "knowledgeProStrategy"));
        List<AgentStrategy> result = new ArrayList<>();
        for (ConfigInfo configInfo : configInfos) {
            AgentStrategy agentStrategy = new AgentStrategy();
            agentStrategy.setName(configInfo.getName());
            agentStrategy.setDescription(configInfo.getValue());
            agentStrategy.setCode(Integer.valueOf(configInfo.getRemarks()));
            result.add(agentStrategy);
        }
        return result;
    }

    public Object getMcpServerList(String categoryId, Integer page, Integer pageSize, HttpServletRequest request) {
        String uid = UserInfoManagerHandler.getUserId();
        List<McpServerTool> mcpToolList = mcpServerHandler.getMcpToolList(categoryId, page, pageSize, uid);
        List<McpToolConfig> configs = mcpToolConfigMapper.selectList(new LambdaQueryWrapper<McpToolConfig>().eq(McpToolConfig::getUid, uid));
        if (CollUtil.isNotEmpty(mcpToolList)) {
            for (McpServerTool mcpServerTool : mcpToolList) {
                Map<String, McpToolConfig> collect = configs.stream().collect(Collectors.toMap(McpToolConfig::getMcpId, s -> s));
                if (CollUtil.isNotEmpty(collect)) {
                    if (collect.containsKey(mcpServerTool.getId())) {
                        McpToolConfig config = collect.get(mcpServerTool.getId());
                        mcpServerTool.setHasConfig(true);
                        if (!StringUtils.isBlank(config.getParameters()) && config.getCustomize()) {
                            mcpServerTool.setParam(true);
                        }
                        mcpServerTool.setSparkId(config.getServerId());
                    }
                }
            }
        }
        return mcpToolList;
    }

    /**
     * Debug tool
     *
     * @param req
     * @return
     */
    public Object debugServerTool(McpToolReq req) {
        JSONObject reqObj = new JSONObject();
        reqObj.put("mcp_server_id", req.getMcpServerId());
        reqObj.put("mcp_server_url", req.getMcpServerUrl());
        reqObj.put("tool_name", req.getToolName());
        reqObj.put("tool_args", req.getToolArgs());
        // Add plugin debug history
        ToolBoxOperateHistory toolBoxOperateHistory = new ToolBoxOperateHistory();
        toolBoxOperateHistory.setToolId(req.getToolId());
        toolBoxOperateHistory.setUid(UserInfoManagerHandler.getUserId());
        toolBoxOperateHistory.setType(1);
        toolBoxOperateHistoryMapper.insert(toolBoxOperateHistory);
        return mcpServerHandler.debugServerTool(reqObj);
    }

    public JSONObject getServerToolDetail(String serverId) {
        return mcpServerHandler.getMcpServerInfo(serverId);
    }

    /**
     * Add secret key
     *
     * @param serverId
     * @return
     */
    public Object andEnvKey(String serverId, HttpServletRequest request) {
        String uid = UserInfoManagerHandler.getUserId();
        McpToolConfig mcpToolConfig = mcpToolConfigMapper.selectOne(new LambdaQueryWrapper<McpToolConfig>()
                .eq(McpToolConfig::getUid, uid)
                .eq(McpToolConfig::getMcpId, serverId));
        // 1. Check if it's an update or new AK
        JSONObject ret = mcpServerHandler.checkMcpToolsIsNeedEnvKeys(serverId);
        JSONArray parameters = ret.getJSONArray("parameters");
        Map<String, String> existMap = new HashMap<>(parameters.size());
        for (Object parameter : parameters) {
            JSONObject param = (JSONObject) parameter;
            if (StringUtils.isNotBlank(param.getString("default"))) {
                param.put("hasDefault", true);
                param.put("default", null);
                // Has default value
                existMap.put(param.getString("name"), param.getString("default"));
            } else {
                param.put("hasDefault", false);
            }
        }
        if (CollUtil.isNotEmpty(existMap)) {
            String key = "mcp_list:mcp_id_".concat(serverId);
            String mapString = JSON.toJSONString(existMap);
            redisTemplate.opsForValue().set(key, mapString, 30, TimeUnit.MINUTES);
        }
        if (mcpToolConfig != null && StringUtils.isNotBlank(mcpToolConfig.getParameters())) {
            JSONObject jsonObject = JSON.parseObject(mcpToolConfig.getParameters());
            ret.put("oldParameters", jsonObject);
        }
        return ApiResult.success(ret);
    }

    public Object pushEnvKey(McpPushDto req, HttpServletRequest rq) {
        String key = "mcp_list:mcp_id_".concat(req.getMcpId());
        String storedJson = (String) redisTemplate.opsForValue().get(key);
        if (StringUtils.isNotBlank(storedJson)) {
            Map<String, String> existMap = JSON.parseObject(storedJson, new TypeReference<Map<String, String>>() {});
            Map<String, String> envMap = req.getEnv();
            if (MapUtils.isNotEmpty(envMap)) {
                envMap.forEach(existMap::put);
            }
            req.setEnv(existMap);
        }
        String uid = UserInfoManagerHandler.getUserId();
        // 1. Generate short link
        JSONObject request = new JSONObject();
        // request.put("name",req.getServerName());
        request.put("env", req.getEnv());
        if (StringUtils.isNotEmpty(req.getRecordId())) {
            request.put("record", Long.parseLong(req.getRecordId()));
        }
        String url = mcpServerHandler.getMcpUrl(request, commonConfig.getAppId());
        // 2. Generate ID
        String mcpServerId = generateServerId(req, url);
        // 3. Authorization
        mcpAuth(req.getRecordId(), null);
        // Save local configuration
        saveLocalConfig(req, uid, url, mcpServerId, req.getEnv(), req.getCustomize());
        return ApiResult.success(mcpServerId);
    }

    private String generateServerId(McpPushDto req, String url) {
        JSONObject linkReq = new JSONObject();
        linkReq.put("app_id", commonConfig.getAppId());
        linkReq.put("name", req.getServerName());
        linkReq.put("type", "docker");
        //
        linkReq.put("mcp_server_url", url);
        linkReq.put("description", req.getServerDesc());
        linkReq.put("mcp_schema", "");
        JSONObject linkRep = mcpServerHandler.mcpPublish(linkReq);
        String mcpServerId = linkRep.getString("id");
        return mcpServerId;
    }

    public void mcpAuth(String recordId, String flowId) {
        long effectTime = LocalDateTime.now().plusYears(50).toEpochSecond(ZoneOffset.of("+8"));
        long orderTime = LocalDateTime.now().toEpochSecond(ZoneOffset.of("+8"));
        JSONObject authReq = new JSONObject();
        authReq.put("operate_type", "add_lic");
        authReq.put("order_id", "REQ" + UUID.randomUUID());
        authReq.put("auth_id", UUID.randomUUID());
        authReq.put("app_id", commonConfig.getAppId());
        authReq.put("channel", "mcp");
        authReq.put("limit", "-1");
        authReq.put("type", "cnt");
        authReq.put("account", "mcp");
        if (recordId != null) {
            authReq.put("function", recordId);
        } else {
            authReq.put("function", flowId);
        }
        authReq.put("order_time", String.valueOf(orderTime));
        authReq.put("effect_etime", String.valueOf(effectTime));
        mcpServerHandler.McpAuth(authReq);
    }

    private void saveLocalConfig(McpPushDto req, String uid, String url, String mcpServerId, Map<String, String> env, Boolean customize) {
        McpToolConfig config = mcpToolConfigMapper.selectOne(new LambdaQueryWrapper<McpToolConfig>()
                .eq(McpToolConfig::getUid, uid)
                .eq(McpToolConfig::getMcpId, req.getMcpId()));
        if (config == null) {
            McpToolConfig mcpToolConfig = new McpToolConfig();
            mcpToolConfig.setMcpId(req.getMcpId());
            mcpToolConfig.setUid(uid);
            mcpToolConfig.setSortLink(url);
            mcpToolConfig.setServerId(mcpServerId);
            mcpToolConfig.setCustomize(customize);
            mcpToolConfig.setCreateTime(new Date());
            mcpToolConfig.setUpdateTime(new Date());
            if (env != null) {
                mcpToolConfig.setParameters(JSON.toJSONString(env));
            }
            mcpToolConfigMapper.insert(mcpToolConfig);
        } else {
            config.setSortLink(url);
            config.setServerId(mcpServerId);
            if (env != null) {
                config.setParameters(JSON.toJSONString(env));
            }
            config.setCustomize(customize);
            config.setCreateTime(new Date());
            config.setUpdateTime(new Date());
            mcpToolConfigMapper.updateById(config);
        }
    }


    public Object replaceAppId(String appId, String flowId) {
        log.info("replace appid {}, origin flowId:{}", appId, flowId);
        Workflow one = this.getOne(new LambdaQueryWrapper<Workflow>().eq(Workflow::getFlowId, flowId));
        if (one == null) {
            throw new BusinessException(ResponseEnum.WORKFLOW_NOT_EXIST);
        }
        if (StringUtils.isBlank(appId)) {
            throw new BusinessException(ResponseEnum.APPID_CANNOT_EMPTY);
        }
        String data = one.getData();
        if (StringUtils.isNotBlank(data)) {
            String newData = data.replaceAll(one.getAppId(), appId);
            one.setData(newData);
        }
        String publishedData = one.getPublishedData();
        if (StringUtils.isNotBlank(publishedData)) {
            String newPublishedData = publishedData.replaceAll(one.getAppId(), appId);
            one.setPublishedData(newPublishedData);
        }

        one.setAppId(appId);

        return ApiResult.success(this.updateById(one));
    }

    public Object hasQaNode(Integer botId) {
        UserLangChainInfo userLangChainInfo = userLangChainInfoDao.selectOne(new LambdaQueryWrapper<UserLangChainInfo>().eq(UserLangChainInfo::getBotId, botId));

        if (Objects.isNull(userLangChainInfo)) {
            log.error("----- Assistant protocol not found, botId: {}", botId);
            throw new BusinessException(ResponseEnum.BOT_NOT_EXIST);
        }
        String flowId = userLangChainInfo.getFlowId();
        Workflow workflow = this.getOne(new LambdaQueryWrapper<Workflow>().eq(Workflow::getFlowId, flowId));
        if (workflow == null) {
            throw new BusinessException(ResponseEnum.WORKFLOW_NOT_EXIST);
        }
        Boolean flag = checkFlowHasQaNode(workflow);
        return ApiResult.success(flag);
    }

    private static @NotNull Boolean checkFlowHasQaNode(Workflow workflow) {
        BizWorkflowData bizWorkflowData = JSON.parseObject(workflow.getData(), BizWorkflowData.class);
        if (bizWorkflowData == null) {
            return false;
        }
        List<BizWorkflowNode> nodes = bizWorkflowData.getNodes();
        Boolean flag = false;
        for (BizWorkflowNode node : nodes) {
            // Check if it contains Q&A nodes
            if (node.getId().startsWith("question-answer")) {
                flag = true;
            }
        }
        return flag;
    }

    public Object addComparisons(WorkflowComparisonReq workflowComparisonReq) {
        try {
            // Workflow protocol conversion
            WorkflowReq workflowReq = new WorkflowReq();
            workflowReq.setData(workflowComparisonReq.getData());
            workflowReq.setName(workflowComparisonReq.getName());
            FlowProtocol protocol = buildWorkflowData(workflowReq, workflowComparisonReq.getFlowId());
            // Call core system to add comparison group protocol
            coreSystemService.addComparisons(protocol, workflowComparisonReq.getFlowId(), workflowComparisonReq.getVersion());
        } catch (Exception ex) {
            log.error("Failed to add comparison group protocol, flowId={}, version={}, error={}", workflowComparisonReq.getFlowId(), workflowComparisonReq.getVersion(), ex.getMessage(), ex);
            throw new BusinessException(ResponseEnum.PROMPT_GROUP_SAVE_FAILED);
        }
        return ApiResult.success();
    }

    public Object deleteComparisons(WorkflowComparisonReq workflowComparisonReq) {
        try {
            // Call core system to delete comparison group protocol
            coreSystemService.deleteComparisons(workflowComparisonReq.getFlowId(), workflowComparisonReq.getVersion());
        } catch (Exception ex) {
            log.error("Failed to delete comparison group protocol, flowId={}, version={}, error={}", workflowComparisonReq.getFlowId(), workflowComparisonReq.getVersion(), ex.getMessage(), ex);
            return new BusinessException(ResponseEnum.RESPONSE_FAILED, "Failed to delete comparison group protocol: " + ex.getMessage());
        }
        return ApiResult.success();
    }

    public Object listByStatus(HttpServletRequest request, String name) {
        BotMarketForm botMarketForm = new BotMarketForm();
        botMarketForm.setSearchValue(name);
        botMarketForm.setPageIndex(1);
        botMarketForm.setPageSize(10000);
        Map<String, Object> pageMap = chatBotMarketService.getBotListCheckNextPage(request,
                botMarketForm, UserInfoManagerHandler.getUserId(), SpaceInfoUtil.getSpaceId());
        LinkedList<Map<String, Object>> botList = (LinkedList<Map<String, Object>>) pageMap.get("pageList");
        List<WorkflowListVo> result = new ArrayList<>();
        if (CollUtil.isEmpty(botList)) {
            return ApiResult.success(result);
        } else {
            for (Map<String, Object> bot : botList) {
                if (bot.get("maasId") != null) {
                    Long flowId = Long.valueOf(bot.get("maasId").toString());
                    Workflow workflow = workflowMapper.selectById(flowId);
                    if (workflow == null) {
                        continue;
                    }
                    WorkflowListVo workflowListVo = new WorkflowListVo();
                    workflowListVo.setId(Long.valueOf(bot.get("botId").toString()));
                    workflowListVo.setWorkflowId(workflow.getId());
                    workflowListVo.setName(bot.get("botName").toString());
                    workflowListVo.setFlowId(workflow.getFlowId());
                    workflowListVo.setIsCanPublish(workflow.getCanPublish());
                    workflowListVo.setIsLLm(false);
                    if (StringUtils.isNotBlank(workflow.getData())) {
                        BizWorkflowData bizWorkflowData = JSONObject.parseObject(workflow.getData(), BizWorkflowData.class);
                        Map<String, Boolean> workflowType = getWorkflowType(bizWorkflowData);
                        workflowListVo.setIsLLm(workflowType.get("isLLm"));
                        workflowListVo.setIsMultiParams(workflowType.get("isMultiParams"));
                    }
                    workflowListVo.setDescription(bot.get("botDesc").toString());
                    result.add(workflowListVo);
                }
            }
            return ApiResult.success(result);
        }
    }

    private Map<String, Boolean> getWorkflowType(BizWorkflowData bizWorkflowData) {
        Map<String, Boolean> result = new HashMap<>();
        result.put("isLLm", false);
        result.put("isMultiParams", false);
        bizWorkflowData.getNodes()
                .stream()
                .filter(n -> n.getId().startsWith(WorkflowConst.NodeType.SPARK_LLM))
                .findFirst()
                .ifPresent(n -> {
                    result.put("isLLm", true);
                });

        BizWorkflowNode startNode = bizWorkflowData.getNodes()
                .stream()
                .filter(n -> n.getId().startsWith(WorkflowConst.NodeType.START))
                .findFirst()
                .get();

        if (startNode.getData().getOutputs().size() > 2) {
            result.put("isMultiParams", true);
        } else if (startNode.getData().getOutputs().size() == 2) {
            int fileCount = 0;
            int textCount = 0;
            for (BizInputOutput output : startNode.getData().getOutputs()) {
                if (output.getSchema().getType().startsWith("array")) {
                    result.put("isMultiParams", true);
                    break;
                } else {
                    if (output.getFileType() != null && "file".equals(output.getFileType())) {
                        fileCount++;
                    } else {
                        textCount++;
                    }
                }
            }
            if (fileCount > 1 || textCount > 1) {
                result.put("isMultiParams", true);
            }
        }
        return result;
    }

    public Map<String, Boolean> getWorkflowPromptStatus(Long workflowId) {
        Map<String, Boolean> result = new HashMap<>();
        Workflow workflow = workflowMapper.selectById(workflowId);
        if (StringUtils.isNotBlank(workflow.getData())) {
            BizWorkflowData bizWorkflowData = JSONObject.parseObject(workflow.getData(), BizWorkflowData.class);
            Map<String, Boolean> workflowType = getWorkflowType(bizWorkflowData);
            result.put("isLLm", workflowType.get("isLLm"));
            result.put("isMultiParams", workflowType.get("isMultiParams"));
        }
        result.put("isCanPublish", workflow.getCanPublish());
        result.put("isDeleted", workflow.getDeleted());
        return result;
    }

    public Object getFlowAdvancedConfig(Integer botId) {
        UserLangChainInfo userLangChainInfo = userLangChainInfoDao.selectOne(new LambdaQueryWrapper<UserLangChainInfo>().eq(UserLangChainInfo::getBotId, botId));
        String flowId = userLangChainInfo.getFlowId();
        Workflow flow = this.getOne(new LambdaQueryWrapper<Workflow>().eq(Workflow::getFlowId, flowId));
        if (StringUtils.isNotBlank(flow.getAdvancedConfig())) {
            JSONObject advancedConfig = JSONObject.parseObject(flow.getAdvancedConfig());
            return advancedConfig.getJSONObject("chatBackground");
        }
        return null;
    }

    public String saveComparisons(List<WorkflowComparisonSaveReq> workflowComparisonReqList) {
        try {
            if (workflowComparisonReqList == null || workflowComparisonReqList.isEmpty()) {
                throw new BusinessException(ResponseEnum.PROMPT_GROUP_PROMPT_CANNOT_EMPTY);
            }
            Workflow workflow = workflowMapper.selectOne(Wrappers.lambdaQuery(Workflow.class)
                    .eq(Workflow::getFlowId, workflowComparisonReqList.get(0).getFlowId()));
            dataPermissionCheckTool.checkWorkflowBelong(workflow, SpaceInfoUtil.getSpaceId());
            // Delete old comparison group protocol
            workflowComparisonMapper.delete(Wrappers.lambdaQuery(WorkflowComparison.class)
                    .eq(WorkflowComparison::getFlowId, workflowComparisonReqList.get(0).getFlowId()));
            // Save new comparison group protocol
            Date date = new Date();
            workflowComparisonReqList.forEach(data -> {
                WorkflowComparison workflowComparison = new WorkflowComparison();
                workflowComparison.setFlowId(data.getFlowId());
                workflowComparison.setType(data.getType());
                workflowComparison.setPromptId(data.getPromptId());
                workflowComparison.setData(JSONObject.toJSONString(data.getData()));
                workflowComparison.setCreateTime(date);
                workflowComparison.setUpdateTime(date);
                workflowComparisonMapper.insert(workflowComparison);
            });
            // Define formatter
            SimpleDateFormat formatter = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
            return formatter.format(date);
        } catch (Exception ex) {
            log.error("Failed to save comparison group protocol, flowId={}, error={}", workflowComparisonReqList.get(0).getFlowId(), ex.getMessage(), ex);
            throw new BusinessException(ResponseEnum.PROMPT_GROUP_SAVE_FAILED);
        }


    }

    public List<WorkflowComparison> listComparisons(String promptId) {
        return workflowComparisonMapper.selectList(Wrappers.lambdaQuery(WorkflowComparison.class)
                .eq(WorkflowComparison::getPromptId, promptId));

    }

    public void feedback(WorkflowFeedbackReq workflowFeedbackReq, HttpServletRequest request) {
        try {
            WorkflowFeedback workflowFeedback = new WorkflowFeedback();
            BeanUtils.copyProperties(workflowFeedbackReq, workflowFeedback);
            String uid = RequestContextUtil.getUID();
            UserInfo userInfo = userInfoDataService.findByUid(uid).orElseThrow();
            workflowFeedback.setUserName(userInfo.getNickname());
            workflowFeedback.setUid(uid);
            workflowFeedback.setCreateTime(new Date());
            workflowFeedbackMapper.insert(workflowFeedback);
        } catch (Exception ex) {
            log.error("Workflow feedback failed, sid={}, error={}", workflowFeedbackReq.getSid(), ex.getMessage(), ex);
            throw new BusinessException(ResponseEnum.WORKFLOW_FEEDBACK_FAILED);
        }

    }

    public List<WorkflowFeedback> getFeedbackList(String flowId) {
        return workflowFeedbackMapper.selectList(Wrappers.lambdaQuery(WorkflowFeedback.class)
                .eq(WorkflowFeedback::getFlowId, flowId)
                .eq(WorkflowFeedback::getUid, UserInfoManagerHandler.getUserId())
                .orderByDesc(WorkflowFeedback::getCreateTime));
    }

    private static void dealWithSearchPromptTemplate(String search, LambdaQueryWrapper<PromptTemplate> wrapper) {
        try {
            String decode = URLDecoder.decode(search, StandardCharsets.UTF_8.name());
            String escaped = decode
                    .replace("\\", "\\\\")
                    .replace("_", "\\_")
                    .replace("%", "\\%");
            wrapper.and(w -> w.like(PromptTemplate::getName, escaped)
                    .or()
                    .like(PromptTemplate::getDescription, escaped)
                    .or()
                    .like(PromptTemplate::getPrompt, escaped));
        } catch (Exception e) {
            log.warn("Invalid search parameter: {}", search, e);
            // Query a non-existent ID to return an empty list
            wrapper.and(w -> w.eq(PromptTemplate::getId, -1L));
        }
    }

    public static List<Input> extractInputs(String prompt) {
        List<Input> inputs = new ArrayList<>();
        // Regular expression to match content in {{...}}
        Pattern pattern = Pattern.compile("\\{\\{(.*?)\\}\\}");
        Matcher matcher = pattern.matcher(prompt);

        while (matcher.find()) {
            String inputName = matcher.group(1).trim();
            Input input = new Input(inputName);
            inputs.add(input);
        }

        return inputs;
    }

    @Transactional(rollbackFor = Exception.class)
    public Object copyFlow(String sourceFlowId, String targetFlowId) {
        Workflow sourceFlow = this.getOne(new LambdaQueryWrapper<Workflow>().eq(Workflow::getFlowId, sourceFlowId));
        Workflow targetFlow = this.getOne(new LambdaQueryWrapper<Workflow>().eq(Workflow::getFlowId, targetFlowId));
        if (sourceFlow != null && targetFlow != null) {
            log.info("Start copying flow, sourceFlowId{}, targetFlowId{}, targetFlow source data {}", sourceFlowId, targetFlowId, targetFlow.getData());
            targetFlow.setData(sourceFlow.getData());
            targetFlow.setUpdateTime(new Date());
            this.updateById(targetFlow);
            return true;
        } else {
            return false;
        }
    }

    public McpServerToolDetailVO getServerToolDetailLocally(String serverId) {
        // Get directly from cache
        JSONObject jsonObject = MCP_SERVER_CACHE.get(serverId);
        if (jsonObject != null) {
            return convertJson2DetailVO(jsonObject);
        }
        return null;
    }

    /**
     * Convert JSON object to McpServerToolDetailVO
     */
    private McpServerToolDetailVO convertJson2DetailVO(JSONObject jsonObject) {
        McpServerToolDetailVO detailVO = new McpServerToolDetailVO();

        // Set root level properties
        detailVO.setId(jsonObject.getString("id"));

        // Handle mcp object
        JSONObject mcpObject = jsonObject.getJSONObject("mcp");
        if (mcpObject != null) {
            detailVO.setTools(mcpObject.getJSONArray("tools"));
            setBasicProperties(detailVO, mcpObject);
        }

        return detailVO;
    }

    /**
     * Set basic properties
     */
    private void setBasicProperties(McpServerToolDetailVO detailVO, JSONObject mcpObject) {
        detailVO.setBrief(mcpObject.getString("brief"));
        detailVO.setOverview(mcpObject.getString("overview"));
        detailVO.setCreator(mcpObject.getString("creator"));
        detailVO.setCreateTime(mcpObject.getString("createTime"));
        detailVO.setLogoUrl(mcpObject.getString("logo"));
        detailVO.setMcpType(mcpObject.getString("mcpType"));
        detailVO.setContent(mcpObject.getString("content"));
        JSONArray tags = mcpObject.getJSONArray("tags");
        if (tags != null) {
            detailVO.setTags(tags.toJavaList(String.class));
        }
        detailVO.setRecordId(mcpObject.getString("recordId"));
        detailVO.setName(mcpObject.getString("name"));
        detailVO.setServerUrl(mcpObject.getString("server"));
    }


    public static class Input {
        private String name;

        public Input(String name) {
            this.name = name;
        }

        public String getName() {
            return name;
        }

        // If JSON serialization is needed, setter methods can be added
        public void setName(String name) {
            this.name = name;
        }
    }

    public PageData<PromptTemplate> listPagePromptTemplate(Integer current, Integer pageSize, String search) {
        // 1. Build query conditions
        LambdaQueryWrapper<PromptTemplate> wrapper = Wrappers.lambdaQuery(PromptTemplate.class)
                .eq(PromptTemplate::getDeleted, false)
                .orderByDesc(PromptTemplate::getCreatedTime);

        // 2. Handle search
        if (search != null) {
            if (search.length() > 30) {
                throw new BusinessException(ResponseEnum.WORKFLOW_QUERY_LENGTH_OUTRANGE);
            }
            dealWithSearchPromptTemplate(search, wrapper);
        }

        // 3. Use MyBatis-Plus pagination query (efficient)
        Page<PromptTemplate> page = new Page<>(current, pageSize);
        Page<PromptTemplate> result = promptTemplateMapper.selectPage(page, wrapper);

        for (PromptTemplate record : result.getRecords()) {
            // {"characterSettings": "characterSettings", "thinkStep": "thinkStep", "userQuery": "userQuery"}
            JSONObject json = JSON.parseObject(record.getPrompt());
            record.setCharacterSettings(json.get("characterSettings").toString());
            record.setThinkStep(json.get("thinkStep").toString());
            record.setUserQuery(json.get("userQuery").toString());
            record.setJsonAdaptationModel(JSON.parseObject(record.getAdaptationModel()));
            record.setInputs(extractInputs(record.getPrompt()));
        }

        // 4. Convert to custom PageData
        PageData<PromptTemplate> pageData = new PageData<>();
        pageData.setPageData(result.getRecords());
        pageData.setTotalCount(result.getTotal());
        return pageData;
    }

    public List<McpServerTool> getMcpServerListLocally(String categoryId, Integer pageNo, Integer pageSize, Boolean authorized,
            HttpServletRequest request) {
        // Check if cache has expired, reload if expired
        checkAndRefreshCache();

        List<McpServerTool> filteredList = MCP_SERVER_CACHE.values()
                .stream()
                .filter(jsonObject -> {
                    if (StringUtils.isNotBlank(categoryId)) {
                        String objCategoryId = jsonObject.getString("categoryId");
                        if (!categoryId.equals(objCategoryId)) {
                            return false;
                        }
                    }

                    if (authorized != null) {
                        Boolean objAuthorized = jsonObject.getBoolean("authorized");
                        return objAuthorized != null && objAuthorized == authorized;
                    }

                    return true;
                })
                .map(this::convertJson2McpServerTool)
                .collect(Collectors.toList());

        int total = filteredList.size();
        int startIndex = (pageNo - 1) * pageSize;
        int endIndex = Math.min(startIndex + pageSize, total);

        if (startIndex >= total || startIndex < 0) {
            return new ArrayList<>();
        }

        return filteredList.subList(startIndex, endIndex);
    }

    private McpServerTool convertJson2McpServerTool(JSONObject jsonObject) {
        McpServerTool mcpServerTool = new McpServerTool();
        mcpServerTool.setId(jsonObject.getString("id"));

        // Get properties from mcp object
        JSONObject mcpObject = jsonObject.getJSONObject("mcp");
        if (mcpObject != null) {
            mcpServerTool.setBrief(mcpObject.getString("brief"));
            mcpServerTool.setOverview(mcpObject.getString("overview"));
            mcpServerTool.setCreator(mcpObject.getString("creator"));
            mcpServerTool.setCreateTime(mcpObject.getString("createTime"));
            mcpServerTool.setLogoUrl(mcpObject.getString("logo"));
            mcpServerTool.setMcpType(mcpObject.getString("mcpType"));
            mcpServerTool.setContent(mcpObject.getString("content"));
            mcpServerTool.setTools(mcpObject.getJSONArray("tools"));
            mcpServerTool.setTags(mcpObject.getJSONArray("tags"));
            mcpServerTool.setAuthorized(mcpObject.getBoolean("authorized"));
        }

        return mcpServerTool;
    }

    /**
     * Check if cache has expired, and refresh cache if it has
     */
    private void checkAndRefreshCache() {
        long now = System.currentTimeMillis();
        // If cache is empty or has expired, reload
        if (MCP_SERVER_CACHE.isEmpty() || (now - lastCacheLoadTime) > CACHE_EXPIRE_TIME) {
            synchronized (CACHE_LOAD_LOCK) {
                // Double check to prevent other threads from already loading
                if (MCP_SERVER_CACHE.isEmpty() || (now - lastCacheLoadTime) > CACHE_EXPIRE_TIME) {
                    loadMcpServersFromFiles();
                    lastCacheLoadTime = System.currentTimeMillis();
                }
            }
        }
    }

    /**
     * Load MCP server configuration from files, using the id field in files as cache key
     */
    private void loadMcpServersFromFiles() {
        // Use regular HashMap as it's single-threaded construction and atomic replacement
        Map<String, JSONObject> tempCache = new HashMap<>();

        List<JSONObject> jsonObjects = readAllJsonFiles();
        for (JSONObject jsonObject : jsonObjects) {
            // Use the id field in file
            String id = jsonObject.getString("id");
            if (StringUtils.isNotBlank(id)) {
                tempCache.put(id, jsonObject);
            }
        }

        // Atomic replacement of entire cache
        MCP_SERVER_CACHE = tempCache;
        log.info("Loaded and cached {} MCP tools", MCP_SERVER_CACHE.size());
    }

    private List<JSONObject> readAllJsonFiles() {
        List<JSONObject> jsonObjects = new ArrayList<>();
        PathMatchingResourcePatternResolver resolver = new PathMatchingResourcePatternResolver();

        // Read all JSON files under the path
        org.springframework.core.io.Resource[] resources = null;
        try {
            resources = resolver.getResources(mcpServerFilePath + "/*.json");

            for (org.springframework.core.io.Resource resource : resources) {
                try (InputStream inputStream = resource.getInputStream()) {
                    // Read file content and convert to JSONObject;
                    JSONObject jsonObject = JSON.parseObject(inputStream, JSONObject.class);

                    jsonObjects.add(jsonObject);
                }
            }
        } catch (IOException e) {
            log.error("Failed to read file for MCP-Server registration, file path={}", mcpServerFilePath, e);
            throw new BusinessException(ResponseEnum.WORKFLOW_MCP_SERVER_REGISTRY_FAILED);
        }

        return jsonObjects;
    }

    public void removeAllCanvasHold() {
        // Clear canvas multi-open count
        Long wc = count(Wrappers.lambdaQuery(Workflow.class).eq(Workflow::getDeleted, false));
        Long l = redisUtil.removeScan("spark_bot:workflow:canvas_heartbeat:*", Math.toIntExact(wc));
        log.info("remove all canvas count {}", l);
    }
}
