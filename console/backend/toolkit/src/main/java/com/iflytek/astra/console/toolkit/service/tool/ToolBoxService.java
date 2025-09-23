package com.iflytek.astra.console.toolkit.service.tool;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.bean.copier.CopyOptions;
import cn.hutool.core.codec.Base64;
import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.RandomUtil;
import cn.hutool.core.util.URLUtil;
import com.alibaba.fastjson2.*;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.google.common.collect.Lists;
import com.iflytek.astra.console.commons.constant.ResponseEnum;
import com.iflytek.astra.console.commons.exception.BusinessException;
import com.iflytek.astra.console.commons.response.ApiResult;
import com.iflytek.astra.console.commons.util.space.SpaceInfoUtil;
import com.iflytek.astra.console.toolkit.common.*;
import com.iflytek.astra.console.toolkit.common.constant.*;
import com.iflytek.astra.console.toolkit.config.properties.*;
import com.iflytek.astra.console.toolkit.entity.common.PageData;
import com.iflytek.astra.console.toolkit.entity.core.openapi.*;
import com.iflytek.astra.console.toolkit.entity.dto.*;
import com.iflytek.astra.console.toolkit.entity.enumVo.ToolboxStatusEnum;
import com.iflytek.astra.console.toolkit.entity.table.ConfigInfo;
import com.iflytek.astra.console.toolkit.entity.table.relation.BotToolRel;
import com.iflytek.astra.console.toolkit.entity.table.relation.FlowToolRel;
import com.iflytek.astra.console.toolkit.entity.table.tool.*;
import com.iflytek.astra.console.toolkit.entity.table.users.SystemUser;
import com.iflytek.astra.console.toolkit.entity.tool.*;
import com.iflytek.astra.console.toolkit.handler.*;
import com.iflytek.astra.console.toolkit.handler.language.LanguageContext;
import com.iflytek.astra.console.toolkit.mapper.bot.SparkBotMapper;
import com.iflytek.astra.console.toolkit.mapper.relation.FlowToolRelMapper;
import com.iflytek.astra.console.toolkit.mapper.tool.*;
import com.iflytek.astra.console.toolkit.mapper.trace.ChatInfoMapper;
import com.iflytek.astra.console.toolkit.mapper.users.SystemUserMapper;
import com.iflytek.astra.console.toolkit.service.bot.BotToolRelService;
import com.iflytek.astra.console.toolkit.service.common.ConfigInfoService;
import com.iflytek.astra.console.toolkit.service.workflow.WorkflowService;
import com.iflytek.astra.console.toolkit.tool.DataPermissionCheckTool;
import com.iflytek.astra.console.toolkit.tool.UrlCheckTool;
import com.iflytek.astra.console.toolkit.util.*;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.CollectionUtils;

import java.lang.reflect.Field;
import java.net.URL;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

/**
 * <p>
 * 服务实现类
 * </p>
 *
 * @author xxzhang23
 * @since 2024-01-09
 */
@Service
@Slf4j
public class ToolBoxService extends ServiceImpl<ToolBoxMapper, ToolBox> {

    @Resource
    BizConfig bizConfig;

    @Resource
    ChatInfoMapper chatInfoMapper;

    @Autowired
    UrlCheckTool urlCheckTool;

    public static final String ARRAY = "array";
    public static final String OBJECT = "object";
    public static final String STRING = "string";
    public static final String NUMBER = "number";
    public static final String INTEGER = "integer";

    public static final String BOOLEAN = "boolean";

    private static final String TAG_STRING = "TOOL_TAGS_V2";


    public ToolBox getOnly(QueryWrapper<ToolBox> wrapper) {
        wrapper.last("limit 1");
        return getOne(wrapper);
    }

    public ToolBox getOnly(LambdaQueryWrapper<ToolBox> wrapper) {
        wrapper.last("limit 1");
        return getOne(wrapper);
    }

    @Resource
    ToolBoxMapper toolBoxMapper;
    @Resource
    RepoAuthorizedConfig repoAuthorizedConfig;
    @Resource
    ToolServiceCallHandler toolServiceCallHandler;
    @Resource
    ConfigInfoService configInfoService;
    @Resource
    S3Util s3UtilClient;
    @Resource
    SparkBotMapper sparkBotMapper;
    @Resource
    BotToolRelService botToolRelService;

    @Resource
    RedisTemplate<String, Object> redisTemplate;
    @Resource
    UserFavoriteToolMapper userFavoriteToolMapper;
    @Resource
    DataPermissionCheckTool dataPermissionCheckTool;
    @Resource
    SystemUserMapper systemUserMapper;
    @Resource
    FlowToolRelMapper flowToolRelMapper;
    @Autowired
    McpServerHandler mcpServerHandler;
    @Autowired
    ToolBoxOperateHistoryMapper toolBoxOperateHistoryMapper;
    @Autowired
    ToolBoxFeedbackMapper toolBoxFeedbackMapper;
    @Resource
    WorkflowService workflowService;
    @Autowired
    private CommonConfig commonConfig;

    private static final String FAVORITE_KEY_PREFIX = "new:user:favorite:tool:";

    private static final String CONFIG_KEY_PREFIX = "spark_bot:tool_config:";
    private static final String TOOL_HEAT_VALUE_PREFIX = "spark_bot:tool:heat_value:";



    @Transactional
    public ToolBox createTool(ToolBoxDto toolBoxDto) {

        ToolBox toolBox;
        if (toolBoxDto.getId() != null) {
            toolBox = getById(toolBoxDto.getId());
            if (toolBox != null) {
                // 添加越权校验
                dataPermissionCheckTool.checkToolBelong(toolBox);
            } else {
                throw new BusinessException(ResponseEnum.TOOLBOX_NOT_EXIST_MODIFY);
            }
        } else {
            toolBox = new ToolBox();
        }
        // 校验endpoint地址合法性
        if (StringUtils.isNotBlank(toolBox.getEndPoint())) {
            urlCheckTool.checkUrl(toolBox.getEndPoint());
        }
        toolBoxDto.setVersion("V1.0");
        String schemaString = buildToolBox(toolBox, toolBoxDto);
        ToolProtocolDto toolProtocolDto = buildToolRequest(toolBoxDto, schemaString);
        ToolResp toolCreateResp = toolServiceCallHandler.toolCreate(toolProtocolDto);
        toolServiceCallHandler.dealResult(toolCreateResp);
        String toolId = ((JSONObject) toolCreateResp.getData()).getJSONArray("tools").getObject(0, Tool.class).getId();
        toolBox.setToolId(toolId);
        // 清除暂存数据
        toolBox.setTemporaryData(StringUtils.EMPTY);
        if (toolBoxDto.getId() != null) {
            updateById(toolBox);
        } else {
            save(toolBox);
        }
        // tool鉴权数据写入redis
        if (toolBoxDto.getAuthType() != ToolConst.AuthType.NONE) {
            writeAuthInfoToRedis(toolId, toolBoxDto);
        }

        return toolBox;
    }

    private ToolProtocolDto buildToolRequest(ToolBoxDto toolBoxDto, String schemaString) {
        // 构建请求
        ToolProtocolDto request = new ToolProtocolDto();

        ToolHeader header = new ToolHeader();
        header.setAppId(commonConfig.getAppId());
        request.setHeader(header);

        ToolPayload payload = new ToolPayload();
        Tool tool = new Tool();
        BeanUtils.copyProperties(toolBoxDto, tool);
        tool.setSchemaType(0);
        tool.setOpenapiSchema(Base64.encode(schemaString));
        tool.setVersion(toolBoxDto.getVersion());
        if (StringUtils.isNotBlank(toolBoxDto.getToolId())) {
            tool.setId(toolBoxDto.getToolId());
        }
        payload.setTools(Collections.singletonList(tool));
        request.setPayload(payload);
        return request;
    }

    private String buildToolBox(ToolBox toolBox, ToolBoxDto toolBoxDto) {
        BeanUtils.copyProperties(toolBoxDto, toolBox);
        toolBox.setIcon(toolBoxDto.getAvatarIcon());
        toolBox.setUserId(UserInfoManagerHandler.getUserId());
        toolBox.setSpaceId(SpaceInfoUtil.getSpaceId());
        toolBox.setAppId(commonConfig.getAppId());
        toolBox.setDeleted(false);
        toolBox.setCreateTime(new Timestamp(System.currentTimeMillis()));
        toolBox.setUpdateTime(new Timestamp(System.currentTimeMillis()));
        toolBox.setSource(CommonConst.PlatformCode.COMMON);
        toolBox.setAvatarColor(toolBoxDto.getAvatarColor());
        toolBox.setVisibility(0);
        toolBox.setStatus(1);
        toolBox.setToolTag(toolBoxDto.getToolTag());
        toolBox.setIsPublic(toolBoxDto.getIsPublic());
        OpenApiSchema toolSchema = convertToolBoxVoToToolSchema(toolBoxDto, null);
        String schemaString = JSON.toJSONString(toolSchema);

        toolBox.setSchema(schemaString);

        // set operation id
        Map<String, Map<String, Operation>> paths = toolSchema.getPaths();
        Map<String, Operation> methodOperationMap = paths.get(getPathCompatible(toolBox.getEndPoint()));
        Operation operation = methodOperationMap.get(toolBox.getMethod());
        toolBox.setOperationId(operation.getOperationId());
        return schemaString;
    }

    public ToolBox temporaryTool(ToolBoxDto toolBoxDto) {
        ToolBox toolBox;
        if (toolBoxDto.getId() != null) {
            toolBox = getById(toolBoxDto.getId());
            if (toolBox != null) {
                // 添加越权校验
                dataPermissionCheckTool.checkToolBelong(toolBox);
            }
        } else {
            toolBox = new ToolBox();
        }

        BeanUtil.copyProperties(toolBoxDto, toolBox, CopyOptions.create().ignoreNullValue());
        toolBox.setIcon(toolBoxDto.getAvatarIcon());
        toolBox.setUserId(UserInfoManagerHandler.getUserId());
        toolBox.setSpaceId(SpaceInfoUtil.getSpaceId());
        toolBox.setAppId(commonConfig.getAppId());
        toolBox.setDeleted(false);
        // 生成临时toolId
        if (StringUtils.isBlank(toolBox.getToolId())) {
            toolBox.setToolId("temp_tool_" + RandomUtil.randomString(10));
        }
        toolBox.setCreateTime(new Timestamp(System.currentTimeMillis()));
        toolBox.setUpdateTime(new Timestamp(System.currentTimeMillis()));
        toolBox.setSource(CommonConst.PlatformCode.COMMON);
        toolBox.setAvatarColor(toolBoxDto.getAvatarColor());

        toolBox.setVisibility(0);

        if (ToolboxStatusEnum.FORMAL.getCode().equals(toolBox.getStatus())) {
            // 正式工具存到缓存字段
            String temporary = JSONObject.toJSONString(toolBox);
            toolBoxMapper.update(null, new UpdateWrapper<ToolBox>().lambda()
                    .set(ToolBox::getTemporaryData, temporary)
                    .set(ToolBox::getUpdateTime, new Timestamp(System.currentTimeMillis()))
                    .eq(ToolBox::getId, toolBox.getId()));
        } else {
            // 草稿态直接更新
            toolBox.setStatus(ToolboxStatusEnum.DRAFT.getCode());
            if (toolBoxDto.getId() != null) {
                updateById(toolBox);
            } else {
                save(toolBox);
            }
        }
        return toolBox;
    }

    private void writeAuthInfoToRedis(String toolId, ToolBoxDto toolBoxDto) {
        if (toolBoxDto.getAuthType() == ToolConst.AuthType.SERVICE) {
            ServiceAuthInfo serviceAuthInfo = JSON.parseObject(toolBoxDto.getAuthInfo(), ServiceAuthInfo.class);
            JSONObject jsonObject = new JSONObject();
            jsonObject.put("apiKey", new JSONObject().fluentPut(serviceAuthInfo.getParameterName(), serviceAuthInfo.getServiceToken()));
            redisTemplate.opsForValue().set(CONFIG_KEY_PREFIX.concat(toolId).concat(":").concat(toolBoxDto.getVersion()), new JSONObject().fluentPut("authentication", jsonObject));
        }
    }

    @Transactional
    public ToolBox updateTool(ToolBoxDto toolBoxDto) {
        try {
            ToolBox toolBox = getById(toolBoxDto.getId());
            if (toolBox == null) {
                throw new BusinessException(ResponseEnum.TOOLBOX_NOT_EXIST_MODIFY);
            }
            // 添加越权校验
            dataPermissionCheckTool.checkToolBelong(toolBox);

            ToolBoxDto originToolBoxDto = new ToolBoxDto();
            toolBoxDto.setToolId(toolBox.getToolId());

            if (StringUtils.isBlank(toolBoxDto.getWebSchema())) {
                toolBoxDto.setWebSchema(toolBox.getWebSchema());
            }
            if (StringUtils.isBlank(toolBoxDto.getEndPoint())) {
                toolBoxDto.setEndPoint(toolBox.getEndPoint());
            }
            if (toolBoxDto.getAuthType() == null) {
                toolBoxDto.setAuthType(toolBox.getAuthType());
                toolBoxDto.setAuthInfo(toolBox.getAuthInfo());
            }

            if (StringUtils.isBlank(toolBoxDto.getMethod())) {
                toolBoxDto.setMethod(toolBox.getMethod());
            }

            BeanUtils.copyProperties(toolBox, originToolBoxDto);
            originToolBoxDto.setAvatarIcon(toolBox.getIcon());

            // 比较插件协议是否发生更新
            if (isEqual(toolBoxDto, originToolBoxDto, "creationMethod", "version", "temporaryData", "isPublic")) {
                return toolBox;
            } else {
                String version = buildVersion(toolBox);
                toolBoxDto.setVersion(version);
                toolBoxDto.setId(null);
                toolBoxDto.setToolTag(toolBox.getToolTag());
                toolBoxDto.setIsPublic(toolBox.getIsPublic());
                ToolBox newToolBox = new ToolBox();
                String schemaString = buildToolBox(newToolBox, toolBoxDto);
                // 清除暂存数据
                newToolBox.setTemporaryData(StringUtils.EMPTY);
                // 校验endpoint地址合法性
                if (StringUtils.isNotBlank(newToolBox.getEndPoint())) {
                    urlCheckTool.checkUrl(newToolBox.getEndPoint());
                }
                save(newToolBox);
                // 工具侧新增版本接口
                ToolProtocolDto toolProtocolDto = buildToolRequest(toolBoxDto, schemaString);
                ToolResp toolCreateResp = toolServiceCallHandler.toolUpdate(toolProtocolDto);
                toolServiceCallHandler.dealResult(toolCreateResp);
                return newToolBox;
            }
        } catch (BusinessException e) {
            log.error("插件新增版本失败: toolId:{}", toolBoxDto.getId(), e);
            throw new BusinessException(ResponseEnum.TOOLBOX_ADD_VERSION_FAILED);
        }
    }

    private static String buildVersion(ToolBox toolBox) {
        String version = toolBox.getVersion();
        if (version == null || version.isEmpty()) {
            version = "V2.0";
        } else {
            String numberPart = version.substring(1);
            String[] versionParts = numberPart.split("\\.");
            if (versionParts.length > 0) {
                int majorVersion = Integer.parseInt(versionParts[0]) + 1;
                version = "V" + majorVersion + "." + versionParts[1];
            } else {
                version = "V2.0";
            }
        }
        return version;
    }

    public static boolean isEqual(Object a, Object b, String... ignoreFields) {
        if (a == b)
            return true;
        if (a == null || b == null)
            return false;
        if (!a.getClass().equals(b.getClass()))
            return false;

        Set<String> ignoreSet = new HashSet<>();
        if (ignoreFields != null) {
            for (String field : ignoreFields) {
                ignoreSet.add(field);
            }
        }

        Class<?> clazz = a.getClass();
        Field[] fields = clazz.getDeclaredFields();

        for (Field field : fields) {
            if (ignoreSet.contains(field.getName())) {
                continue;
            }
            field.setAccessible(true);
            try {
                Object valueA = field.get(a);
                Object valueB = field.get(b);
                if (!Objects.equals(valueA, valueB)) {
                    return false;
                }
            } catch (IllegalAccessException e) {
                throw new RuntimeException("Failed to compare field: " + field.getName(), e);
            }
        }

        return true;
    }

    @Transactional
    public Object deleteTool(Long id) {
        ToolBox toolBox = getById(id);
        if (toolBox == null) {
            throw new BusinessException(ResponseEnum.TOOLBOX_NOT_EXIST_DELETE);
        }
        dataPermissionCheckTool.checkToolBelong(toolBox);
        // 草稿态工具直接删除
        if (toolBox.getStatus().equals(0)) {
            toolBox.setDeleted(true);
            toolBox.setUpdateTime(new Timestamp(System.currentTimeMillis()));
            updateById(toolBox);
            return ApiResult.success();
        }

        long flowListCount = flowToolRelMapper.selectCount(Wrappers.lambdaQuery(FlowToolRel.class).eq(FlowToolRel::getToolId, toolBox.getToolId()));
        if (flowListCount > 0) {
            throw new BusinessException(ResponseEnum.TOOLBOX_CANNOT_DELETE_RELATED_WORKFLOW);
        }

        long modelListCount = botToolRelService.count(Wrappers.lambdaQuery(BotToolRel.class).eq(BotToolRel::getToolId, toolBox.getToolId()));
        if (modelListCount > 0) {
            throw new BusinessException(ResponseEnum.TOOLBOX_CANNOT_DELETE_RELATED);
        }

        toolBoxMapper.update(null, new UpdateWrapper<ToolBox>().lambda()
                .set(ToolBox::getDeleted, true)
                .set(ToolBox::getUpdateTime, new Timestamp(System.currentTimeMillis()))
                .eq(ToolBox::getToolId, toolBox.getToolId()));

        String paramStr = "?app_id=" + commonConfig.getAppId() + "&tool_ids=" + toolBox.getToolId();
        ToolResp toolDelResp = toolServiceCallHandler.toolDelete(paramStr);
        toolServiceCallHandler.dealResult(toolDelResp);
        return ApiResult.success();
    }

    public Object debugTool(Long id, JSONObject reqData) {
        ToolBox toolBox = getById(id);
        if (toolBox == null) {
            throw new BusinessException(ResponseEnum.TOOLBOX_NOT_EXIST);
        }

        // 添加越权校验
        dataPermissionCheckTool.checkToolBelong(toolBox);
        ToolProtocolDto request = new ToolProtocolDto();

        ToolHeader header = new ToolHeader();
        header.setUid(toolBox.getUserId());
        header.setAppId(commonConfig.getAppId());
        request.setHeader(header);

        ToolParameter parameter = new ToolParameter();
        parameter.setToolId(toolBox.getToolId());
        parameter.setOperationId(toolBox.getOperationId());

        request.setParameter(parameter);

        ToolPayload payload = new ToolPayload();
        Message message = new Message();
        JSONObject headerObj = extractToolRunHeader(reqData);
        if (!headerObj.isEmpty()) {
            message.setHeader(Base64.encode(headerObj.toString()));
        }
        JSONObject queryObj = extractToolRunQuery(reqData);

        if (!queryObj.isEmpty()) {
            message.setQuery(Base64.encode(queryObj.toString()));
        }
        JSONObject bodyObj = extractToolRunBody(reqData);
        if (!bodyObj.isEmpty()) {
            message.setBody(Base64.encode(bodyObj.toString()));
        }
        payload.setMessage(message);

        request.setPayload(payload);

        ToolProtocolDto toolRunResp = toolServiceCallHandler.toolRun(request);
        if (toolRunResp.getHeader().getCode() != 0) {
            return ApiResult.error(toolRunResp.getHeader().getCode(), toolRunResp.getHeader().getMessage());
        }
        String text = toolRunResp.getPayload().getText().getText();
        try {
            return JSON.parseObject(text);
        } catch (Exception e) {
            return text;
        }
    }

    public Object debugToolV2(ToolBoxDto toolBoxDto) {
        ToolDebugRequest request = new ToolDebugRequest();
        if (toolBoxDto.getId() != null) {
            ToolBox toolBox = toolBoxMapper.selectById(toolBoxDto.getId());
            if (toolBox == null) {
                throw new BusinessException(ResponseEnum.TOOLBOX_NOT_EXIST);
            }
            // 判断官方插件
            if ((toolBox.getIsPublic() || toolBox.getUserId().equals(bizConfig.getAdminUid().toString())) && !toolBox.getUserId().equals(UserInfoManagerHandler.getUserId())) {
                toolBoxDto.setWebSchema(buildDisPlaySchema(toolBoxDto.getId(), toolBoxDto.getWebSchema()));
            }
            // 添加插件调试历史
            ToolBoxOperateHistory ToolBoxOperateHistory = new ToolBoxOperateHistory();
            ToolBoxOperateHistory.setToolId(toolBox.getToolId());
            ToolBoxOperateHistory.setUid(UserInfoManagerHandler.getUserId());
            ToolBoxOperateHistory.setType(1);
            toolBoxOperateHistoryMapper.insert(ToolBoxOperateHistory);
        }

        // 参数校验
        urlCheckTool.checkUrl(toolBoxDto.getEndPoint());


        OpenApiSchema openApiSchema = convertToolBoxVoToToolSchema(toolBoxDto, null);


        request.setOpenapiSchema(JSON.toJSONString(openApiSchema));

        request.setServer(toolBoxDto.getEndPoint());
        request.setMethod(toolBoxDto.getMethod().toUpperCase());

        WebSchema webSchema = JSON.parseObject(toolBoxDto.getWebSchema(), WebSchema.class);

        List<WebSchemaItem> toolRequestInput = webSchema.getToolRequestInput();
        List<WebSchemaItem> toolHttpHeaders = toolRequestInput.stream().filter(e -> e.getLocation().equalsIgnoreCase("header")).collect(Collectors.toList());
        List<WebSchemaItem> toolUrlParams = toolRequestInput.stream().filter(e -> e.getLocation().equalsIgnoreCase("query")).collect(Collectors.toList());
        List<WebSchemaItem> toolRequestBody = toolRequestInput.stream().filter(e -> e.getLocation().equalsIgnoreCase("body")).collect(Collectors.toList());
        List<WebSchemaItem> toolPathParams = toolRequestInput.stream().filter(e -> e.getLocation().equalsIgnoreCase("path")).collect(Collectors.toList());

        request.setQuery(extractToolRunParams(toolUrlParams));
        request.setHeader(extractToolRunParams(toolHttpHeaders));
        request.setBody(extractToolRunParams(toolRequestBody));
        request.setPath(extractToolRunParams(toolPathParams));

        if (toolBoxDto.getAuthType() != ToolConst.AuthType.NONE) {
            if (toolBoxDto.getAuthType() == ToolConst.AuthType.SERVICE) {
                ServiceAuthInfo serviceAuthInfo = JSON.parseObject(toolBoxDto.getAuthInfo(), ServiceAuthInfo.class);
                String location = serviceAuthInfo.getLocation();
                switch (location) {
                    case OpenApiConst.PARAMETER_IN_HEADER:
                        request.getHeader().put(serviceAuthInfo.getParameterName(), serviceAuthInfo.getServiceToken());
                        break;
                    case OpenApiConst.PARAMETER_IN_QUERY:
                        request.getQuery().put(serviceAuthInfo.getParameterName(), serviceAuthInfo.getServiceToken());
                        break;
                    default:
                        throw new IllegalArgumentException("unsupported location : " + location);
                }
            }
        }

        ToolProtocolDto toolRunResp = toolServiceCallHandler.toolDebug(request);
        if (toolRunResp.getHeader().getCode() != 0) {
            return ApiResult.error(toolRunResp.getHeader().getCode(), toolRunResp.getHeader().getMessage());
        }
        String text = toolRunResp.getPayload().getText().getText();
        try {
            return JSON.parseObject(text);
        } catch (Exception e) {
            return ApiResult.success(text);
        }
    }

    public PageData<ToolBoxVo> pageListTools(Integer pageNo, Integer pageSize, String content, Integer status) {
        int listCount = toolBoxMapper.getModelListCountByCondition(UserInfoManagerHandler.getUserId(), SpaceInfoUtil.getSpaceId(), content, status);
        List<ToolBox> toolBoxList = toolBoxMapper.getModelListByCondition(UserInfoManagerHandler.getUserId(), SpaceInfoUtil.getSpaceId(), content, (pageNo - 1) * pageSize, pageSize, status);

        List<ToolBoxVo> toolBoxVoList = new ArrayList<>();
        for (ToolBox toolBox : toolBoxList) {
            ToolBoxVo toolBoxVo = new ToolBoxVo();
            BeanUtils.copyProperties(toolBox, toolBoxVo);
            toolBoxVo.setAddress(s3UtilClient.getS3Prefix());
            long count = botToolRelService.count(Wrappers.lambdaQuery(BotToolRel.class).eq(BotToolRel::getToolId, toolBox.getToolId()));
            long count1 = flowToolRelMapper.selectCountByToolId(toolBox.getToolId());
            toolBoxVo.setBotUsedCount((int) count + (int) count1);
            SystemUser systemUser = systemUserMapper.selectById(toolBox.getUserId());
            String creator = null;
            if (systemUser != null) {
                if (StringUtils.isBlank(systemUser.getNickname())) {
                    creator = systemUser.getLogin();
                } else {
                    creator = systemUser.getNickname();
                }
            }
            toolBoxVo.setCreator(creator);
            // 暂存名称替换
            if (status == null) {
                if (StringUtils.isNotBlank(toolBox.getTemporaryData())) {
                    JSONObject jsonObject = JSONObject.parseObject(toolBox.getTemporaryData());
                    if (jsonObject.getString("name") != null) {
                        toolBoxVo.setName(jsonObject.getString("name"));
                    }
                    if (jsonObject.getString("description") != null) {
                        toolBoxVo.setDescription(jsonObject.getString("description"));
                    }
                    if (jsonObject.getString("icon") != null) {
                        toolBoxVo.setIcon(jsonObject.getString("icon"));
                    }
                    if (jsonObject.getString("avatarColor") != null) {
                        toolBoxVo.setAvatarColor(jsonObject.getString("avatarColor"));
                    }
                }
            }
            toolBoxVoList.add(toolBoxVo);
        }
        PageData<ToolBoxVo> pageData = new PageData<>();
        pageData.setPageData(toolBoxVoList);
        pageData.setTotalCount((long) listCount);
        return pageData;
    }

    public ToolBoxVo getDetail(Long id, Boolean temporary) {
        ToolBox toolBox = getById(id);
        if (toolBox == null) {
            throw new BusinessException(ResponseEnum.TOOLBOX_NOT_EXIST);
        }
        dataPermissionCheckTool.checkToolVisible(toolBox);

        ToolBoxVo toolBoxVo = new ToolBoxVo();
        BeanUtils.copyProperties(toolBox, toolBoxVo);
        if (temporary != null && temporary) {
            if (StringUtils.isNotBlank(toolBox.getTemporaryData())) {
                ToolBox temporaryToolBox = JSONObject.parseObject(toolBox.getTemporaryData(), ToolBox.class);
                BeanUtils.copyProperties(temporaryToolBox, toolBoxVo);
                toolBoxVo.setIsPublic(toolBox.getIsPublic());
            }
        }

        toolBoxVo.setAddress(s3UtilClient.getS3Prefix());
        String userId = UserInfoManagerHandler.getUserId();
        Set<String> favorites;
        favorites = getFavoritesId(userId);
        // 是否被收藏
        boolean contains = favorites.contains(toolBox.getId().toString());
        toolBoxVo.setIsFavorite(contains);
        long count = botToolRelService.count(Wrappers.lambdaQuery(BotToolRel.class).eq(BotToolRel::getToolId, toolBox.getToolId()));
        long count1 = flowToolRelMapper.selectCount(Wrappers.lambdaQuery(FlowToolRel.class).eq(FlowToolRel::getToolId, toolBox.getToolId()));
        toolBoxVo.setBotUsedCount((int) count + (int) count1);
        SystemUser systemUser = systemUserMapper.selectById(toolBox.getUserId());
        if (systemUser != null) {
            String creator = systemUser.getNickname();
            if (StringUtils.isBlank(creator)) {
                creator = systemUser.getLogin();
            }
            toolBoxVo.setCreator(creator);
        }
        // 官方工具不可见参数隐藏
        if ((toolBoxVo.getIsPublic() || toolBoxVo.getUserId().equals(bizConfig.getAdminUid())) && !toolBoxVo.getUserId().equals(UserInfoManagerHandler.getUserId())) {
            toolBoxVo.setWebSchema(filterDisPlaySchema(toolBox.getWebSchema()));
            toolBoxVo.setSchema(StringUtils.EMPTY);
            toolBoxVo.setAuthInfo(StringUtils.EMPTY);
        } ;
        return toolBoxVo;
    }

    private String filterDisPlaySchema(String webSchemaString) {
        WebSchema webSchema = JSON.parseObject(webSchemaString, WebSchema.class);

        List<WebSchemaItem> toolRequestInput = webSchema.getToolRequestInput();
        List<WebSchemaItem> filteredItems = toolRequestInput.stream()
                .filter(item -> item.getOpen() == null || item.getOpen())
                .collect(Collectors.toList());
        webSchema.setToolRequestInput(filteredItems);

        List<WebSchemaItem> toolRequestOutput = webSchema.getToolRequestOutput();
        List<WebSchemaItem> toolRequestOutputFilter = toolRequestOutput.stream()
                .filter(item -> item.getOpen() == null || item.getOpen())
                .collect(Collectors.toList());
        webSchema.setToolRequestOutput(toolRequestOutputFilter);
        return JSON.toJSONString(webSchema);
    }

    private String buildDisPlaySchema(Long id, String webSchemaString) {
        ToolBox toolBox = toolBoxMapper.selectById(id);
        String originWebSchemaString = toolBox.getWebSchema();
        WebSchema originWebSchema = JSON.parseObject(originWebSchemaString, WebSchema.class);
        WebSchema webSchema = JSON.parseObject(webSchemaString, WebSchema.class);
        List<WebSchemaItem> originToolRequestInput = originWebSchema.getToolRequestInput();
        List<WebSchemaItem> toolRequestInput = webSchema.getToolRequestInput();
        Map<String, WebSchemaItem> inputMap = toolRequestInput.stream()
                .collect(Collectors.toMap(WebSchemaItem::getName, item -> item));

        originToolRequestInput = originToolRequestInput.stream()
                .map(item -> inputMap.getOrDefault(item.getName(), item))
                .collect(Collectors.toList());
        originWebSchema.setToolRequestInput(originToolRequestInput);
        return JSON.toJSONString(originWebSchema);
    }


    public JSONObject getToolDefaultIcon() {
        List<ConfigInfo> configInfoList = configInfoService.list(Wrappers.lambdaQuery(ConfigInfo.class).eq(ConfigInfo::getCategory, "TOOL_ICON").eq(ConfigInfo::getIsValid, 1));
        if (!CollectionUtils.isEmpty(configInfoList)) {
            JSONObject jsonObject = new JSONObject();
            Random random = new Random();
            int randomNumber = random.nextInt(configInfoList.size());
            ConfigInfo configInfo = configInfoList.get(randomNumber);
            jsonObject.put("address", configInfo.getName());
            jsonObject.put("value", configInfo.getValue());
            return jsonObject;
        }
        return null;
    }

    /**
     * 工具广场查询列表
     *
     * @param dto
     * @return
     */

    public PageData<ToolBoxVo> listToolSquare(ToolSquareDto dto) {
        String uid = "3";
        String content = dealHtmlXss(dto.getContent());

        // 处理收藏筛选
        Set<String> favorites = handleFavoriteFilter(uid, dto.getFavoriteFlag());
        if (dto.getFavoriteFlag() != null && dto.getFavoriteFlag() == 1 && CollUtil.isEmpty(favorites)) {
            return createEmptyPageData();
        }

        // 获取工具列表
        List<ToolBoxVo> toolBoxVoList = getToolBoxList(uid, content, favorites, dto);
        if (CollUtil.isEmpty(toolBoxVoList)) {
            return createEmptyPageData();
        }

        long totalSize = toolBoxVoList.size();

        // 填充元数据信息
        fillToolBoxMetadata(uid, toolBoxVoList);

        // 排序和分页
        List<ToolBoxVo> sortedAndPagedList = sortAndPaginate(toolBoxVoList, dto, uid);

        // 构建分页结果
        return buildPageData(sortedAndPagedList, dto.getPage(), dto.getPageSize(), totalSize);
    }

    /**
     * 处理收藏筛选逻辑
     */
    private Set<String> handleFavoriteFilter(String uid, Integer favoriteFlag) {
        Set<String> favorites = new HashSet<>();
        if (favoriteFlag != null && favoriteFlag == 1) {
            favorites = getFavoritesId(uid);
        }
        return favorites;
    }

    /**
     * 创建空的分页数据
     */
    private PageData<ToolBoxVo> createEmptyPageData() {
        PageData<ToolBoxVo> pageData = new PageData<>();
        pageData.setPageData(Lists.newArrayList());
        pageData.setTotalCount(0L);
        return pageData;
    }

    /**
     * 获取工具列表（包含普通工具和MCP工具）
     */
    private List<ToolBoxVo> getToolBoxList(String uid, String content, Set<String> favorites, ToolSquareDto dto) {
        List<ToolBoxVo> toolBoxVoList = new ArrayList<>();

        // 获取普通工具
        List<ToolBox> toolBoxList = toolBoxMapper.getModelListSquareByCondition(
                uid, content, null, null, favorites, dto.getOrderFlag(),
                dto.getTagFlag(), dto.getTags(), bizConfig.getAdminUid(), CommonConst.Platform.COMMON);

        toolBoxVoList.addAll(toolBoxList.stream()
                .map(this::convert2ToolBoxVo)
                .collect(Collectors.toList()));

        // 处理MCP工具
        if (shouldIncludeMcpTools(dto)) {
            List<ToolBoxVo> mcpTools = getMcpTools(dto);
            if (!CollectionUtils.isEmpty(mcpTools)) {
                toolBoxVoList.addAll(mcpTools);
            }
        }

        return toolBoxVoList;
    }

    /**
     * 判断是否需要包含MCP工具
     */
    private boolean shouldIncludeMcpTools(ToolSquareDto dto) {
        if (dto.getTagFlag() != null && dto.getTagFlag() == 0) {
            return true;
        }
        if (dto.getTags() != null) {
            ConfigInfo config = configInfoService.getById(dto.getTags());
            return config != null && Arrays.asList("MCP工具", "MCP Tools").contains(config.getName());
        }
        return false;
    }

    /**
     * 转换ToolBox为ToolBoxVo
     */
    private ToolBoxVo convert2ToolBoxVo(ToolBox toolBox) {
        ToolBoxVo toolBoxVo = new ToolBoxVo();
        BeanUtils.copyProperties(toolBox, toolBoxVo);
        toolBoxVo.setWebSchema(filterDisPlaySchema(toolBoxVo.getWebSchema()));
        toolBoxVo.setSchema(StringUtils.EMPTY);
        toolBoxVo.setAuthInfo(StringUtils.EMPTY);
        return toolBoxVo;
    }

    /**
     * 填充工具元数据信息
     */
    private void fillToolBoxMetadata(String uid, List<ToolBoxVo> toolBoxVoList) {
        Set<String> favoritesId = getFavoritesId(uid);
        List<ConfigInfo> configInfoList = getTagConfigList();

        for (ToolBoxVo toolBoxVo : toolBoxVoList) {
            fillSingleToolMetadata(toolBoxVo, favoritesId, configInfoList);
        }
    }

    /**
     * 获取标签配置列表
     */
    private List<ConfigInfo> getTagConfigList() {
        return configInfoService.list(Wrappers.lambdaQuery(ConfigInfo.class)
                .eq(ConfigInfo::getCategory, "TAG")
                .eq(ConfigInfo::getCode, TAG_STRING)
                .eq(ConfigInfo::getIsValid, 1));
    }

    /**
     * 填充单个工具的元数据
     */
    private void fillSingleToolMetadata(ToolBoxVo toolBoxVo, Set<String> favoritesId, List<ConfigInfo> configInfoList) {
        // 设置地址前缀
        if (!toolBoxVo.getIsMcp()) {
            toolBoxVo.setAddress(s3UtilClient.getS3Prefix());
        }

        // 设置收藏状态
        boolean isFavorite = toolBoxVo.getIsMcp() ? favoritesId.contains(toolBoxVo.getMcpTooId()) : favoritesId.contains(toolBoxVo.getToolId());
        toolBoxVo.setIsFavorite(isFavorite);

        // 设置标签
        fillToolTags(toolBoxVo, configInfoList);

    }

    /**
     * 填充工具标签
     */
    private void fillToolTags(ToolBoxVo toolBoxVo, List<ConfigInfo> configInfoList) {
        if (!StringUtils.isEmpty(toolBoxVo.getToolTag())) {
            List<String> tags = Arrays.asList(toolBoxVo.getToolTag().split(","));
            List<String> nameList = configInfoList.stream()
                    .filter(config -> tags.contains(config.getId().toString()))
                    .map(ConfigInfo::getName)
                    .collect(Collectors.toList());
            toolBoxVo.setTags(nameList);
        }
    }


    /**
     * 排序和分页处理
     */
    private List<ToolBoxVo> sortAndPaginate(List<ToolBoxVo> toolBoxVoList, ToolSquareDto dto, String uid) {
        Integer orderFlag = dto.getOrderFlag();
        Integer pageNo = dto.getPage();
        Integer pageSize = dto.getPageSize();

        if (orderFlag == 0) {
            return sortByHeatValueAndPaginate(toolBoxVoList, pageNo, pageSize);
        } else if (orderFlag == 1) {
            return sortByRecentUseAndPaginate(toolBoxVoList, pageNo, pageSize, uid);
        } else {
            return paginateOnly(toolBoxVoList, pageNo, pageSize);
        }
    }

    /**
     * 按热度值排序并分页
     */
    private List<ToolBoxVo> sortByHeatValueAndPaginate(List<ToolBoxVo> toolBoxVoList, Integer pageNo, Integer pageSize) {
        return toolBoxVoList.stream()
                .sorted(Comparator.comparing(ToolBoxVo::getHeatValue).reversed())
                .skip((long) (pageNo - 1) * pageSize)
                .limit(pageSize)
                .collect(Collectors.toList());
    }

    /**
     * 按最近使用排序并分页
     */
    private List<ToolBoxVo> sortByRecentUseAndPaginate(List<ToolBoxVo> toolBoxVoList, Integer pageNo, Integer pageSize, String uid) {
        Map<String, Integer> orderMap = buildRecentUseOrderMap(uid);

        toolBoxVoList.sort(Comparator.comparingInt(vo -> {
            String toolId = vo.getIsMcp() ? vo.getMcpTooId() : vo.getToolId();
            return orderMap.getOrDefault(toolId, Integer.MAX_VALUE);
        }));

        return paginateOnly(toolBoxVoList, pageNo, pageSize);
    }

    /**
     * 构建最近使用的顺序映射
     */
    private Map<String, Integer> buildRecentUseOrderMap(String uid) {
        List<ToolBoxOperateHistory> operateHistories = toolBoxOperateHistoryMapper.selectList(
                Wrappers.lambdaQuery(ToolBoxOperateHistory.class)
                        .eq(ToolBoxOperateHistory::getUid, uid)
                        .orderByDesc(ToolBoxOperateHistory::getCreateTime));

        LinkedHashSet<String> toolIdSet = operateHistories.stream()
                .map(ToolBoxOperateHistory::getToolId)
                .filter(Objects::nonNull)
                .collect(Collectors.toCollection(LinkedHashSet::new));

        Map<String, Integer> orderMap = new HashMap<>();
        int index = 0;
        for (String id : toolIdSet) {
            orderMap.put(id, index++);
        }
        return orderMap;
    }

    /**
     * 仅分页处理
     */
    private List<ToolBoxVo> paginateOnly(List<ToolBoxVo> toolBoxVoList, Integer pageNo, Integer pageSize) {
        return toolBoxVoList.stream()
                .skip((long) (pageNo - 1) * pageSize)
                .limit(pageSize)
                .collect(Collectors.toList());
    }

    /**
     * 构建分页数据
     */
    private PageData<ToolBoxVo> buildPageData(List<ToolBoxVo> toolBoxVoList, Integer pageNo, Integer pageSize, long totalSize) {
        PageData<ToolBoxVo> pageData = new PageData<>();
        pageData.setPageData(toolBoxVoList);
        pageData.setPageSize(pageSize);
        pageData.setPage(pageNo);
        pageData.setTotalCount(totalSize);
        pageData.setTotalPages(totalSize / pageSize + (totalSize % pageSize == 0 ? 0 : 1));
        return pageData;
    }

    // 每隔5分钟执行一次
    @Scheduled(fixedRate = 300000, initialDelay = 600000)
    public void executeToolHeatValueSelect() {
        LambdaQueryWrapper<ToolBox> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(ToolBox::getDeleted, 0) // delete = 1
                .and(wrapper -> wrapper.eq(ToolBox::getIsPublic, 1)
                        .or()
                        .eq(ToolBox::getUserId, bizConfig.getAdminUid()));
        List<ToolBox> toolBoxes = toolBoxMapper.selectList(queryWrapper);
        List<String> tooIds = toolBoxes.stream().map(ToolBox::getToolId).collect(Collectors.toList());
        List<ToolUseDto> flowToolUseList = chatInfoMapper.selectWorkflowUseCount(tooIds);
        List<ToolUseDto> botToolUseList = chatInfoMapper.selectBotUseCount(tooIds);
        // 收藏人数
        List<UserFavoriteTool> userFavoriteTools = userFavoriteToolMapper.selectAllList();
        for (ToolBox toolBox : toolBoxes) {
            Long workflowUseCount = flowToolUseList.stream()
                    .filter(tool -> tool.getToolId() != null && tool.getToolId().contains(toolBox.getToolId()))
                    .mapToLong(ToolUseDto::getUseCount)
                    .sum();
            Long botUseCount = botToolUseList.stream()
                    .filter(tool -> tool.getToolId() != null && tool.getToolId().contains(toolBox.getToolId()))
                    .mapToLong(ToolUseDto::getUseCount)
                    .sum();
            List<UserFavoriteTool> favoriteTools = userFavoriteTools.stream()
                    .filter(tool -> tool.getPluginToolId() != null && tool.getPluginToolId().equals(toolBox.getToolId()))
                    .collect(Collectors.toList());
            // 收藏次数
            long favoriteToolCount = favoriteTools.size();
            long favoriteUserCount = favoriteTools.stream()
                    .filter(tool -> !tool.getDeleted() && tool.getUseFlag() == 1)
                    .count();
            long heatValue = (workflowUseCount + botUseCount - 1) * 3 + (favoriteUserCount - 1) * 10 + favoriteToolCount * 10 + workflowUseCount + botUseCount;
            if (heatValue < 0) {
                heatValue = 0L;
            }
            redisTemplate.opsForValue().set(TOOL_HEAT_VALUE_PREFIX + toolBox.getToolId(), heatValue);
        }
        // mcp工具热度值
        List<ToolBoxVo> mcpTools = getMcpTools(new ToolSquareDto());
        for (ToolBoxVo mcpTool : mcpTools) {
            // 查询同名插件
            // 处理字符串：忽略大小写匹配 "-mcp"，并移除它
            String mcpName = mcpTool.getName().replaceAll("(?i)-mcp", "");
            LambdaQueryWrapper<ToolBox> newQueryWrapper = new LambdaQueryWrapper<>();
            newQueryWrapper.eq(ToolBox::getDeleted, 0)
                    .like(ToolBox::getName, mcpName)
                    .and(wrapper -> wrapper.eq(ToolBox::getIsPublic, 1)
                            .or()
                            .eq(ToolBox::getUserId, bizConfig.getAdminUid()));
            List<ToolBox> toolBoxList = toolBoxMapper.selectList(newQueryWrapper);
            if (!toolBoxList.isEmpty()) {
                // 查询同名插件的热度值
                Long heatValue = (Long) redisTemplate.opsForValue().get(TOOL_HEAT_VALUE_PREFIX + toolBoxList.get(0).getToolId());
                if (heatValue == null) {
                    heatValue = 0L;
                }
                redisTemplate.opsForValue().set(TOOL_HEAT_VALUE_PREFIX + mcpTool.getMcpTooId(), heatValue);
            } else {
                // 查表-查询mcp工具热度
                Long mcpHeatValue = toolBoxMapper.getMcpHeatValueByName(mcpTool.getName());
                if (mcpHeatValue == null) {
                    mcpHeatValue = 0L;
                }
                redisTemplate.opsForValue().set(TOOL_HEAT_VALUE_PREFIX + mcpTool.getMcpTooId(), mcpHeatValue);
            }
        }
        log.info("tool heat value select - Current Time: " + LocalDateTime.now());
    }

    private List<ToolBoxVo> getMcpTools(ToolSquareDto dto) {
        List<ToolBoxVo> toolBoxVoList = new ArrayList<>();
        // MCP工具
        List<McpServerTool> mcpToolList = workflowService.getMcpServerListLocally(null, 1, 1000, dto.getAuthorized(), null);
        // List<McpServerTool> mcpToolList = mcpServerHandler.getMcpToolList(null, 1, 10000, null);
        if (mcpToolList == null || mcpToolList.isEmpty()) {
            return toolBoxVoList;
        }
        for (McpServerTool mcp : mcpToolList) {
            ToolBoxVo toolBoxVo = new ToolBoxVo();
            List<String> tags = new ArrayList<>();
            if (LanguageContext.isZh()) {
                tags.add("MCP工具");
            } else {
                tags.add("MCP Tools");
            }
            toolBoxVo.setTags(tags);
            toolBoxVo.setName(mcp.getName());
            toolBoxVo.setDescription(mcp.getBrief());
            toolBoxVo.setAddress(mcp.getLogoUrl());
            toolBoxVo.setHeatValue(0L);
            toolBoxVo.setIsFavorite(false);
            toolBoxVo.setMcpTooId(mcp.getId());
            toolBoxVo.setToolId(mcp.getSparkId());
            toolBoxVo.setIsMcp(true);
            toolBoxVo.setAuthorized(mcp.getAuthorized());
            toolBoxVoList.add(toolBoxVo);
        }
        // 手动筛选名称或描述
        if (StringUtils.isNotBlank(dto.getContent())) {
            toolBoxVoList = toolBoxVoList.stream()
                    .filter(toolBoxVo -> toolBoxVo.getName().contains(dto.getContent()) || toolBoxVo.getDescription().contains(dto.getContent()))
                    .collect(Collectors.toList());
        }
        return toolBoxVoList;
    }


    private static String dealHtmlXss(String content) {
        if (StringUtils.isNotEmpty(content)) {
            String sanitize = XssSanitizer.sanitize(content);
            content = sanitize;
        }
        return content;
    }

    /**
     * 缓存获取用户收藏的工具id，缓存没有数据库获取
     *
     * @param userId
     * @return
     */
    private Set<String> getFavoritesId(String userId) {
        Set<Object> favorites;
        String redisKey = FAVORITE_KEY_PREFIX + userId;
        favorites = redisTemplate.opsForSet().members(redisKey);
        if (favorites == null || favorites.isEmpty()) {
            QueryWrapper<UserFavoriteTool> queryWrapper = new QueryWrapper<>();
            queryWrapper.eq("user_id", userId);
            queryWrapper.eq("use_flag", 1);
            queryWrapper.eq("is_delete", 0);
            List<ToolFavoriteToolDto> userFavoriteTools = userFavoriteToolMapper.findAllTooIdByUserId(userId);
            List<String> favoriteToolIds = userFavoriteTools.stream()
                    .map(ToolFavoriteToolDto::getPluginToolId)
                    .filter(Objects::nonNull)
                    .map(String::valueOf)
                    .collect(Collectors.toList());
            List<String> favoriteMcpToolIds = userFavoriteTools.stream()
                    .map(ToolFavoriteToolDto::getMcpToolId)
                    .filter(Objects::nonNull)
                    .map(String::valueOf)
                    .collect(Collectors.toList());
            favoriteToolIds.addAll(favoriteMcpToolIds);
            if (CollUtil.isNotEmpty(favoriteToolIds)) {
                favorites = new HashSet<>(favoriteToolIds);
                redisTemplate.opsForSet().add(redisKey, favorites.toArray());
            }
        }
        if (CollectionUtils.isEmpty(favorites)) {
            return new HashSet<>();
        }
        return favorites.stream().map(String::valueOf).collect(Collectors.toSet());
    }


    /**
     * 收藏工具
     *
     * @param toolId
     * @param favoriteFlag
     * @return
     */

    public Integer favorite(String toolId, Integer favoriteFlag, Boolean isMcp) {
        AtomicReference<Integer> result = new AtomicReference<>();
        result.set(0);
        String userId = UserInfoManagerHandler.getUserId();
        String redisKey = FAVORITE_KEY_PREFIX + userId;
        Optional<UserFavoriteTool> existingFavorite;
        if (isMcp) {
            existingFavorite = userFavoriteToolMapper.findByUserIdAndMcpToolId(userId, toolId);
        } else {
            existingFavorite = userFavoriteToolMapper.findByUserIdAndToolId(userId, toolId);
        }
        // 0-收藏，1-取消收藏
        if (favoriteFlag == 0) {
            // 已被收藏
            if (existingFavorite.isPresent()) {
                throw new BusinessException(ResponseEnum.TOOLBOX_ALREADY_COLLECT);
            }
            UserFavoriteTool userFavorite = new UserFavoriteTool();
            userFavorite.setUserId(userId);
            userFavorite.setToolId(0L);
            if (isMcp) {
                userFavorite.setMcpToolId(toolId);
            } else {
                userFavorite.setPluginToolId(toolId);
            }
            userFavorite.setCreatedTime(new Timestamp(System.currentTimeMillis()));
            // 1-表示收藏
            userFavorite.setUseFlag(1);
            userFavoriteToolMapper.save(userFavorite);
            redisTemplate.opsForSet().add(redisKey, toolId);
        } else if (favoriteFlag == 1) {
            if (existingFavorite.isPresent()) {
                UserFavoriteTool userFavorite = existingFavorite.get();
                userFavorite.setDeleted(true);
                userFavoriteToolMapper.updateFavoriteStatus(userFavorite);
                redisTemplate.opsForSet().remove(redisKey, toolId);
                // 检查集合是否为空
                Set<Object> favorites = redisTemplate.opsForSet().members(redisKey);
                if (favorites == null || favorites.isEmpty()) {
                    redisTemplate.delete(redisKey);
                }
            } else {
                throw new BusinessException(ResponseEnum.TOOLBOX_NO_COLLECT);
            }
        }
        return result.get();
    }


    @Deprecated
    public JSONObject extractToolRunHeader(JSONObject reqData) {
        JSONObject jsonObject = new JSONObject();
        JSONArray toolHttpHeaders = reqData.getJSONArray("toolHttpHeaders");
        if (toolHttpHeaders != null && !toolHttpHeaders.isEmpty()) {
            List<WebSchemaItem> items = toolHttpHeaders.toJavaList(WebSchemaItem.class);
            JSONObject obj = recurGenRunParam(items);
            jsonObject.putAll(obj);
        }
        return jsonObject;
    }

    @Deprecated
    public JSONObject extractToolRunQuery(JSONObject reqData) {
        JSONObject jsonObject = new JSONObject();
        JSONArray toolHttpHeaders = reqData.getJSONArray("toolUrlParams");
        if (toolHttpHeaders != null && !toolHttpHeaders.isEmpty()) {
            List<WebSchemaItem> items = toolHttpHeaders.toJavaList(WebSchemaItem.class);
            JSONObject obj = recurGenRunParam(items);
            jsonObject.putAll(obj);
        }
        return jsonObject;
    }

    @Deprecated
    public JSONObject extractToolRunPath(JSONObject reqData) {
        JSONObject jsonObject = new JSONObject();
        JSONArray toolHttpHeaders = reqData.getJSONArray("toolUrlPathParams");
        if (toolHttpHeaders != null && !toolHttpHeaders.isEmpty()) {
            List<WebSchemaItem> items = toolHttpHeaders.toJavaList(WebSchemaItem.class);
            JSONObject obj = recurGenRunParam(items);
            jsonObject.putAll(obj);
        }
        return jsonObject;
    }

    @Deprecated
    public JSONObject extractToolRunBody(JSONObject reqData) {
        JSONObject jsonObject = new JSONObject();
        JSONArray toolUrlParams = reqData.getJSONArray("toolRequestBody");
        if (toolUrlParams != null && !toolUrlParams.isEmpty()) {
            List<WebSchemaItem> items = toolUrlParams.toJavaList(WebSchemaItem.class);
            JSONObject obj = recurGenRunParam(items);
            jsonObject.putAll(obj);
        }
        return jsonObject;
    }

    public JSONObject extractToolRunParams(List<WebSchemaItem> webSchemaItems) {
        JSONObject jsonObject = new JSONObject();
        if (webSchemaItems != null && !webSchemaItems.isEmpty()) {
            JSONObject obj = recurGenRunParam(webSchemaItems);
            jsonObject.putAll(obj);
        }
        return jsonObject;
    }

    @Deprecated
    private JSONObject extractToolRunParameter(JSONObject reqData) {
        JSONObject jsonObject = new JSONObject();
        JSONArray toolHttpHeaders = reqData.getJSONArray("toolHttpHeaders");
        if (toolHttpHeaders != null && !toolHttpHeaders.isEmpty()) {
            List<WebSchemaItem> items = toolHttpHeaders.toJavaList(WebSchemaItem.class);
            JSONObject obj = recurGenRunParam(items);
            jsonObject.putAll(obj);
        }

        JSONArray toolUrlParams = reqData.getJSONArray("toolUrlParams");
        if (toolUrlParams != null && !toolUrlParams.isEmpty()) {
            List<WebSchemaItem> items = toolUrlParams.toJavaList(WebSchemaItem.class);
            JSONObject obj = recurGenRunParam(items);
            jsonObject.putAll(obj);
        }

        JSONArray toolUrlPathParams = reqData.getJSONArray("toolUrlPathParams");
        if (toolUrlPathParams != null && !toolUrlPathParams.isEmpty()) {
            List<WebSchemaItem> items = toolUrlPathParams.toJavaList(WebSchemaItem.class);
            JSONObject obj = recurGenRunParam(items);
            jsonObject.putAll(obj);
        }

        JSONArray toolRequestBody = reqData.getJSONArray("toolRequestBody");
        if (toolRequestBody != null && !toolRequestBody.isEmpty()) {
            List<WebSchemaItem> items = toolRequestBody.toJavaList(WebSchemaItem.class);
            JSONObject obj = recurGenRunParam(items);
            jsonObject.putAll(obj);
        }

        return jsonObject;

    }

    private JSONObject recurGenRunParam(List<WebSchemaItem> headerItems) {
        JSONObject jsonObject = new JSONObject();
        headerItems.forEach(item -> {
            switch (item.getType()) {
                case OBJECT:
                    JSONObject obj = recurGenRunParam(item.getChildren());
                    jsonObject.put(item.getName(), obj);
                    break;
                case ARRAY:
                    JSONArray array = new JSONArray();
                    for (WebSchemaItem childItem : item.getChildren()) {
                        if (OBJECT.equals(childItem.getType())) {
                            JSONObject objItem = recurGenRunParam(childItem.getChildren());
                            array.add(objItem);
                        } else {
                            Object value = childItem.getDft();
                            switch (childItem.getType()) {
                                case NUMBER:
                                    try {
                                        array.add(Double.valueOf(String.valueOf(value)));
                                    } catch (Exception e) {
                                        log.error(value + "不是Number类型");
                                        throw new BusinessException(ResponseEnum.TOOLBOX_NOT_NUMBER_TYPE);
                                    }
                                    break;
                                case INTEGER:
                                    try {
                                        array.add(Long.valueOf(String.valueOf(value)));
                                    } catch (Exception e) {
                                        log.error(value + "不是Integer类型");
                                        throw new BusinessException(ResponseEnum.TOOLBOX_NOT_INTEGER_TYPE);
                                    }
                                    break;
                                case BOOLEAN:
                                    try {
                                        array.add(Boolean.valueOf(String.valueOf(value)));
                                    } catch (Exception e) {
                                        log.error(value + "不是Boolean类型");
                                        throw new BusinessException(ResponseEnum.TOOLBOX_NOT_BOOLEAN_TYPE);
                                    }
                                    break;
                                case STRING:
                                default:
                                    array.add(value);
                            }
                        }
                    }
                    jsonObject.put(item.getName(), array);
                    break;
                default:
                    Object value = item.getDft();
                    switch (item.getType()) {
                        case NUMBER:
                            try {
                                jsonObject.put(item.getName(), Double.valueOf(String.valueOf(value)));
                            } catch (Exception e) {
                                log.error(value + "不是Number类型");
                                throw new BusinessException(ResponseEnum.TOOLBOX_NOT_NUMBER_TYPE);
                            }
                            break;
                        case INTEGER:
                            try {
                                jsonObject.put(item.getName(), Long.valueOf(String.valueOf(value)));
                            } catch (Exception e) {
                                log.error(value + "不是Integer类型");
                                throw new BusinessException(ResponseEnum.TOOLBOX_NOT_INTEGER_TYPE);
                            }
                            break;
                        case BOOLEAN:
                            try {
                                jsonObject.put(item.getName(), Boolean.valueOf(String.valueOf(value)));
                            } catch (Exception e) {
                                log.error(value + "不是Boolean类型");
                                throw new BusinessException(ResponseEnum.TOOLBOX_NOT_NUMBER_TYPE);
                            }
                            break;
                        case STRING:
                        default:
                            jsonObject.put(item.getName(), item.getDft());
                    }
            }
        });
        return jsonObject;
    }

    private JSONObject convertWebSchemaTORequestJSON(JSONObject webSchemaObject) {
        JSONObject retObject = new JSONObject();
        // 输入
        JSONArray toolUrlParams = webSchemaObject.getJSONArray("toolUrlParams");
        JSONObject toolUrlParamsTarget = new JSONObject();
        convertRequestParams(toolUrlParams, toolUrlParamsTarget);
        retObject.put("toolUrlParams", toolUrlParamsTarget);

        JSONArray toolUrlPathParams = webSchemaObject.getJSONArray("toolUrlPathParams");
        JSONObject toolUrlPathParamsTarget = new JSONObject();
        convertRequestParams(toolUrlPathParams, toolUrlPathParamsTarget);
        retObject.put("toolUrlPathParams", toolUrlPathParamsTarget);

        JSONArray toolHttpHeaders = webSchemaObject.getJSONArray("toolHttpHeaders");
        JSONObject toolHttpHeadersTarget = new JSONObject();
        convertRequestParams(toolHttpHeaders, toolHttpHeadersTarget);
        retObject.put("toolHttpHeaders", toolHttpHeadersTarget);

        JSONArray toolRequestBody = webSchemaObject.getJSONArray("toolRequestBody");
        JSONObject toolRequestBodyTarget = new JSONObject();
        convertRequestParams(toolRequestBody, toolRequestBodyTarget);
        retObject.put("toolRequestBody", toolRequestBodyTarget);

        return retObject;
    }

    private void convertRequestParams(JSONArray paramArray, JSONObject targetObject) {
        if (CollectionUtils.isEmpty(paramArray)) {
            return;
        }
        for (int i = 0; i < paramArray.size(); i++) {
            JSONObject jsonObject = paramArray.getJSONObject(i);
            String type = jsonObject.getString("type");
            if (StringUtils.isEmpty(type)) {
                throw new BusinessException(ResponseEnum.TOOLBOX_PARAM_TYPE_CANNOT_EMPTY);
            }

            String params = jsonObject.getString("title");
            if (STRING.equals(type) || NUMBER.equals(type) || BOOLEAN.equals(type)) {// 单属性
                Object defaultValue = jsonObject.get("default");
                targetObject.put(params, defaultValue);
            } else if (OBJECT.equals(type) || ARRAY.equals(type)) {// 复合属性 object array
                JSONArray jsonArray = jsonObject.getJSONArray("children");
                if (OBJECT.equals(type)) {
                    JSONObject prop = new JSONObject();
                    targetObject.put(params, prop);

                    convertRequestParams(jsonArray, prop);
                }
            }
        }
    }

    private JSONObject convertWebSchemaTOCoreProtocol(String webSchema) {
        JSONObject retObject = new JSONObject();
        JSONObject webSchemaObject = JSONObject.parseObject(webSchema);
        // 输入
        JSONArray toolUrlParams = webSchemaObject.getJSONArray("toolUrlParams");
        JSONObject toolUrlParamsTarget = new JSONObject();
        convertParams(toolUrlParams, toolUrlParamsTarget, 0, true);
        retObject.put("toolUrlParams", toolUrlParamsTarget.isEmpty() ? null : toolUrlParamsTarget);

        JSONArray toolUrlPathParams = webSchemaObject.getJSONArray("toolUrlPathParams");
        JSONObject toolUrlPathParamsTarget = new JSONObject();
        convertParams(toolUrlPathParams, toolUrlPathParamsTarget, 0, true);
        retObject.put("toolUrlPathParams", toolUrlPathParamsTarget.isEmpty() ? null : toolUrlPathParamsTarget);

        JSONArray toolHttpHeaders = webSchemaObject.getJSONArray("toolHttpHeaders");
        JSONObject toolHttpHeadersTarget = new JSONObject();
        convertParams(toolHttpHeaders, toolHttpHeadersTarget, 0, true);
        retObject.put("toolHttpHeaders", toolHttpHeadersTarget.isEmpty() ? null : toolHttpHeadersTarget);

        JSONArray toolRequestBody = webSchemaObject.getJSONArray("toolRequestBody");
        JSONObject toolRequestBodyTarget = new JSONObject();
        convertParams(toolRequestBody, toolRequestBodyTarget, 0, true);
        retObject.put("toolRequestBody", toolRequestBodyTarget.isEmpty() ? null : toolRequestBodyTarget);

        // 输出
        JSONArray toolRequestOutput = webSchemaObject.getJSONArray("toolRequestOutput");
        JSONObject toolRequestOutputTarget = new JSONObject();
        convertParams(toolRequestOutput, toolRequestOutputTarget, 0, false);
        retObject.put("toolRequestOutput", toolRequestOutputTarget.isEmpty() ? null : toolRequestOutputTarget);

        return retObject;
    }

    private void convertParams(JSONArray paramArray, JSONObject targetObject, Integer previewType, boolean input) {
        if (CollectionUtils.isEmpty(paramArray)) {
            return;
        }
        for (int i = 0; i < paramArray.size(); i++) {
            JSONObject jsonObject = paramArray.getJSONObject(i);
            processParam(jsonObject, targetObject, previewType, input);
        }
    }

    /**
     * 处理单个参数
     */
    private void processParam(JSONObject jsonObject, JSONObject targetObject, Integer previewType, boolean input) {
        // 验证参数基本信息
        validateParamBasicInfo(jsonObject, previewType);

        String type = jsonObject.getString("type");
        String params = jsonObject.getString("title");
        String title = jsonObject.getString("paramName");
        String description = jsonObject.getString("description");

        if (isSimpleType(type)) {
            processSimpleTypeParam(jsonObject, targetObject, params, title, description, type, input);
        } else if (isComplexType(type)) {
            processComplexTypeParam(jsonObject, targetObject, previewType, params, title, description, type, input);
        } else {
            throw new BusinessException(ResponseEnum.TOOLBOX_PARAM_TYPE_NOT_MATCH);
        }
    }

    /**
     * 验证参数基本信息
     */
    private void validateParamBasicInfo(JSONObject jsonObject, Integer previewType) {
        String type = jsonObject.getString("type");
        if (StringUtils.isEmpty(type)) {
            throw new BusinessException(ResponseEnum.TOOLBOX_PARAM_CANNOT_EMPTY);
        }

        String params = jsonObject.getString("title");
        if (previewType == 0 && StringUtils.isEmpty(params)) {
            throw new BusinessException(ResponseEnum.TOOLBOX_PARAM_CANNOT_EMPTY);
        }

        String title = jsonObject.getString("paramName");
        String description = jsonObject.getString("description");
        if (StringUtils.isEmpty(title) || StringUtils.isEmpty(description)) {
            throw new BusinessException(ResponseEnum.TOOLBOX_PARAM_AND_DESC_CANNOT_EMPTY);
        }
    }

    /**
     * 判断是否为简单类型
     */
    private boolean isSimpleType(String type) {
        return STRING.equals(type) || NUMBER.equals(type) || BOOLEAN.equals(type);
    }

    /**
     * 判断是否为复合类型
     */
    private boolean isComplexType(String type) {
        return OBJECT.equals(type) || ARRAY.equals(type);
    }

    /**
     * 处理简单类型参数
     */
    private void processSimpleTypeParam(JSONObject jsonObject, JSONObject targetObject,
            String params, String title, String description, String type, boolean input) {
        Integer from = jsonObject.getInteger("from");
        if (input) {
            validateFromValue(from);
        }

        boolean required = jsonObject.getBooleanValue("required");
        JSONObject paramObject = createBaseParamObject(title, description, type);

        if (input) {
            addInputSpecificFields(paramObject, from, required, jsonObject);
        }

        targetObject.put(params, paramObject);
    }

    /**
     * 验证from字段值
     */
    private void validateFromValue(Integer from) {
        if (from == null || (from != 0 && from != 1 && from != 2)) {
            throw new BusinessException(ResponseEnum.TOOLBOX_PARAM_GET_SOURCE_ILLEGAL);
        }
    }

    /**
     * 创建基础参数对象
     */
    private JSONObject createBaseParamObject(String title, String description, String type) {
        JSONObject paramObject = new JSONObject();
        paramObject.put("title", title);
        paramObject.put("description", description);
        paramObject.put("type", type);
        return paramObject;
    }

    /**
     * 添加输入特定字段
     */
    private void addInputSpecificFields(JSONObject paramObject, Integer from, boolean required, JSONObject jsonObject) {
        paramObject.put("from", from);
        paramObject.put("required", required);
        if (from == 2) {
            Object defaultValue = jsonObject.get("default");
            paramObject.put("default", defaultValue);
        }
    }

    /**
     * 处理复合类型参数
     */
    private void processComplexTypeParam(JSONObject jsonObject, JSONObject targetObject, Integer previewType,
            String params, String title, String description, String type, boolean input) {
        JSONObject multiParamObject = createBaseParamObject(title, description, type);

        if (previewType != 2) {
            targetObject.put(params, multiParamObject);
        }

        JSONArray jsonArray = jsonObject.getJSONArray("children");

        if (OBJECT.equals(type)) {
            processObjectType(multiParamObject, targetObject, previewType, jsonArray, input);
        } else if (ARRAY.equals(type)) {
            processArrayType(multiParamObject, jsonArray, input);
        }
    }

    /**
     * 处理对象类型
     */
    private void processObjectType(JSONObject multiParamObject, JSONObject targetObject,
            Integer previewType, JSONArray jsonArray, boolean input) {
        JSONObject prop = new JSONObject();
        multiParamObject.put("properties", prop);
        if (previewType == 2) {
            targetObject.putAll(multiParamObject);
        }
        convertParams(jsonArray, prop, 1, input);
    }

    /**
     * 处理数组类型
     */
    private void processArrayType(JSONObject multiParamObject, JSONArray jsonArray, boolean input) {
        JSONObject items = new JSONObject();
        multiParamObject.put("items", items);
        convertParams(jsonArray, items, 2, input);
    }

    private List<Parameter> genOpenApiParameters(List<WebSchemaItem> params, String parameterLocation) {
        List<Parameter> parameters = new ArrayList<>();
        for (WebSchemaItem item : params) {
            Parameter parameter = new Parameter();
            parameter.setIn(parameterLocation);
            parameter.setName(item.getName());
            parameter.setDescription(item.getDescription());
            parameter.setRequired(item.getRequired());
            Schema schema = new Schema();
            schema.setType(item.getType());
            schema.setXFrom(item.getFrom());
            schema.setXDisplay(item.getOpen());
            schema.setDft(defaultProcessor(schema.getType(), item.getDft()));
            parameter.setSchema(schema);

            if (item.getType().equals(ARRAY)) {
                createProperty(item, schema);
            }
            parameters.add(parameter);
        }
        return parameters;
    }

    private Map<String, MediaType> getStringMediaTypeMap(List<WebSchemaItem> toolRequestBody) {
        MediaType mediaType = new MediaType();
        Schema schema = new Schema();

        Map<String, Property> propertyMap = new HashMap<>();
        List<String> required = recurGenProperties(toolRequestBody, propertyMap);

        if (!required.isEmpty()) {
            schema.setRequired(required);
        }
        schema.setProperties(propertyMap);
        schema.setType(OpenApiConst.SCHEMA_TYPE_OBJECT);

        mediaType.setSchema(schema);
        Map<String, MediaType> content = new HashMap<>();
        content.put(org.springframework.http.MediaType.APPLICATION_JSON_VALUE, mediaType);
        return content;
    }

    private List<String> recurGenProperties(List<WebSchemaItem> webSchemaItems, Map<String, Property> propertyMap) {
        List<String> required = new ArrayList<>();

        if (webSchemaItems == null || webSchemaItems.isEmpty()) {
            return required;
        }

        for (WebSchemaItem webSchemaItem : webSchemaItems) {
            if (webSchemaItem.getRequired() != null && webSchemaItem.getRequired()) {
                required.add(webSchemaItem.getName());
            }
            Property property = new Property();
            property.setType(webSchemaItem.getType());
            property.setXFrom(webSchemaItem.getFrom());
            property.setXDisplay(webSchemaItem.getOpen());
            property.setDescription(webSchemaItem.getDescription());
            property.setDft(defaultProcessor(property.getType(), webSchemaItem.getDft()));

            if (webSchemaItem.getType().equals(ARRAY)) {
                Property arrP = new Property();
                WebSchemaItem arrChildItem = webSchemaItem.getChildren().get(0);
                arrP.setType(arrChildItem.getType());
                if (arrP.getType().equals(OBJECT)) {
                    Map<String, Property> properties = new HashMap<>();
                    List<String> childRequired = recurGenProperties(arrChildItem.getChildren(), properties);
                    arrP.setProperties(properties);
                    arrP.setRequired(childRequired);
                }

                property.setItems(arrP);
            } else {
                Map<String, Property> properties = new HashMap<>();
                List<String> childRequired = recurGenProperties(webSchemaItem.getChildren(), properties);

                if (!properties.isEmpty()) {
                    property.setProperties(properties);
                }
                if (!childRequired.isEmpty()) {
                    property.setRequired(childRequired);
                }
            }
            propertyMap.put(webSchemaItem.getName(), property);

        }

        return required;
    }


    private Object defaultProcessor(String type, Object dft) {
        if (dft == null) {
            return null;
        }

        String str = String.valueOf(dft);

        if (Arrays.asList("default", "", "[]").contains(str)) {
            return null;
        }

        switch (type) {
            case STRING:
                return str;
            case NUMBER:
                return Double.valueOf(str);
            case INTEGER:
                return Integer.valueOf(str);
            case BOOLEAN:
                return Boolean.valueOf(str);
            default:
                return dft;
        }

    }

    private String getPathCompatible(String url) {
        String path = URLUtil.getPath(url);
        if (StringUtils.isEmpty(path)) {
            path = "/";
        }
        return path;
    }


    /**
     * 创建openapi schema
     *
     * @param toolBoxDto
     * @param operationId
     * @return
     */
    private OpenApiSchema convertToolBoxVoToToolSchema(ToolBoxDto toolBoxDto, String operationId) {
        OpenApiSchema toolSchema = new OpenApiSchema();

        // 设置基本信息
        toolSchema.setInfo(createInfo());
        toolSchema.setServers(createServers(toolBoxDto.getEndPoint()));

        // 解析WebSchema
        WebSchema webSchema = parseWebSchema(toolBoxDto.getWebSchema());

        // 创建Operation
        Operation operation = createOperation(toolBoxDto, operationId, webSchema);

        // 设置安全配置
        if (hasAuthentication(toolBoxDto)) {
            setupAuthentication(toolSchema, operation, toolBoxDto);
        }

        // 设置路径
        toolSchema.setPaths(createPaths(toolBoxDto, operation));

        return toolSchema;
    }

    /**
     * 创建Info对象
     */
    private Info createInfo() {
        Info info = new Info();
        info.setTitle("agentBuilder工具集");
        info.setVersion("1.0.0");
        info.setXIsOfficial(false);
        return info;
    }

    /**
     * 创建服务器列表
     */
    private List<Server> createServers(String endPoint) {
        URL url = URLUtil.toUrlForHttp(endPoint);
        Server server = new Server();
        server.setUrl(URLUtil.getHost(url).toString() + (url.getPort() == -1 ? "" : ":" + url.getPort()));
        return Collections.singletonList(server);
    }

    /**
     * 解析WebSchema
     */
    private WebSchema parseWebSchema(String webSchemaJson) {
        return JSON.parseObject(webSchemaJson, WebSchema.class);
    }

    /**
     * 创建Operation对象
     */
    private Operation createOperation(ToolBoxDto toolBoxDto, String operationId, WebSchema webSchema) {
        Operation operation = new Operation();
        operation.setSummary(toolBoxDto.getName());
        operation.setOperationId(generateOperationId(toolBoxDto.getName(), operationId));
        operation.setDescription(toolBoxDto.getDescription());

        // 设置参数
        setupParameters(operation, webSchema.getToolRequestInput());

        // 设置请求体
        setupRequestBody(operation, webSchema.getToolRequestInput());

        // 设置响应
        setupResponse(operation, webSchema.getToolRequestOutput());

        return operation;
    }

    /**
     * 生成操作ID
     */
    private String generateOperationId(String name, String operationId) {
        return operationId == null ? name + "-" + RandomUtil.randomString(8) : operationId;
    }

    /**
     * 设置参数（Header、Query、Path）
     */
    private void setupParameters(Operation operation, List<WebSchemaItem> toolRequestInput) {
        List<Parameter> allParameters = new ArrayList<>();

        // 添加Header参数
        List<WebSchemaItem> headers = filterByLocation(toolRequestInput, "header");
        if (!CollectionUtils.isEmpty(headers)) {
            allParameters.addAll(genOpenApiParameters(headers, OpenApiConst.PARAMETER_IN_HEADER));
        }

        // 添加Query参数
        List<WebSchemaItem> queryParams = filterByLocation(toolRequestInput, "query");
        if (!CollectionUtils.isEmpty(queryParams)) {
            allParameters.addAll(genOpenApiParameters(queryParams, OpenApiConst.PARAMETER_IN_QUERY));
        }

        // 添加Path参数
        List<WebSchemaItem> pathParams = filterByLocation(toolRequestInput, "path");
        if (!CollectionUtils.isEmpty(pathParams)) {
            allParameters.addAll(genOpenApiParameters(pathParams, OpenApiConst.PARAMETER_IN_PATH));
        }

        if (!allParameters.isEmpty()) {
            operation.setParameters(allParameters);
        }
    }

    /**
     * 根据位置过滤参数
     */
    private List<WebSchemaItem> filterByLocation(List<WebSchemaItem> items, String location) {
        return items.stream()
                .filter(e -> e.getLocation().equalsIgnoreCase(location))
                .collect(Collectors.toList());
    }

    /**
     * 设置请求体
     */
    private void setupRequestBody(Operation operation, List<WebSchemaItem> toolRequestInput) {
        List<WebSchemaItem> bodyParams = filterByLocation(toolRequestInput, "body");
        if (!CollectionUtils.isEmpty(bodyParams)) {
            RequestBody requestBody = new RequestBody();
            requestBody.setContent(createMediaTypeMap(bodyParams));
            operation.setRequestBody(requestBody);
        }
    }

    /**
     * 设置响应
     */
    private void setupResponse(Operation operation, List<WebSchemaItem> toolRequestOutput) {
        if (CollectionUtils.isEmpty(toolRequestOutput)) {
            return;
        }

        Response response = new Response();
        MediaType mediaType = new MediaType();
        Schema schema = createResponseSchema(toolRequestOutput);

        mediaType.setSchema(schema);
        Map<String, MediaType> content = new HashMap<>();
        content.put(org.springframework.http.MediaType.APPLICATION_JSON_VALUE, mediaType);
        response.setContent(content);

        Map<String, Response> responses = new HashMap<>();
        responses.put(String.valueOf(HttpStatus.OK.value()), response);
        operation.setResponses(responses);
    }

    /**
     * 创建响应Schema
     */
    private Schema createResponseSchema(List<WebSchemaItem> toolRequestOutput) {
        Schema schema;

        if (toolRequestOutput.get(0).getType().equals(ARRAY)) {
            schema = createArraySchema(toolRequestOutput.get(0));
        } else {
            schema = createObjectSchema(toolRequestOutput);
        }

        return schema;
    }

    /**
     * 创建数组类型的Schema
     */
    private Schema createArraySchema(WebSchemaItem arrayItem) {
        Schema schema = new Schema();
        schema.setType(ARRAY);

        createProperty(arrayItem, schema);
        return schema;
    }

    private void createProperty(WebSchemaItem arrayItem, Schema schema) {
        Property arrProperty = new Property();
        WebSchemaItem childItem = arrayItem.getChildren().get(0);
        arrProperty.setType(childItem.getType());

        if (arrProperty.getType().equals(OBJECT)) {
            Map<String, Property> properties = new HashMap<>();
            List<String> required = recurGenProperties(childItem.getChildren(), properties);
            arrProperty.setProperties(properties);
            arrProperty.setRequired(required);
        }

        schema.setItems(arrProperty);
    }

    /**
     * 创建对象类型的Schema
     */
    private Schema createObjectSchema(List<WebSchemaItem> items) {
        Schema schema = new Schema();
        schema.setType(OpenApiConst.SCHEMA_TYPE_OBJECT);

        Map<String, Property> propertyMap = new HashMap<>();
        List<String> required = recurGenProperties(items, propertyMap);

        if (!required.isEmpty()) {
            schema.setRequired(required);
        }
        schema.setProperties(propertyMap);

        return schema;
    }

    /**
     * 判断是否有认证配置
     */
    private boolean hasAuthentication(ToolBoxDto toolBoxDto) {
        return toolBoxDto.getAuthType() != ToolConst.AuthType.NONE;
    }

    /**
     * 设置认证配置
     */
    private void setupAuthentication(OpenApiSchema toolSchema, Operation operation, ToolBoxDto toolBoxDto) {
        if (toolBoxDto.getAuthType() != ToolConst.AuthType.SERVICE) {
            return;
        }

        ServiceAuthInfo serviceAuthInfo = JSON.parseObject(toolBoxDto.getAuthInfo(), ServiceAuthInfo.class);

        // 设置Components
        Components components = createSecurityComponents(serviceAuthInfo);
        toolSchema.setComponents(components);

        // 设置Operation的安全配置
        Map<String, Object> securityMap = new HashMap<>();
        securityMap.put(serviceAuthInfo.getParameterName(), new ArrayList<>());
        operation.setSecurity(Collections.singletonList(securityMap));
    }

    /**
     * 创建安全组件
     */
    private Components createSecurityComponents(ServiceAuthInfo serviceAuthInfo) {
        Components components = new Components();
        SecurityScheme securityScheme = new SecurityScheme();
        securityScheme.setType(OpenApiConst.SecuritySchemeType.APIKEY);
        securityScheme.setName(serviceAuthInfo.getParameterName());
        securityScheme.setIn(serviceAuthInfo.getLocation().toLowerCase());

        Map<String, SecurityScheme> securitySchemes = new HashMap<>();
        securitySchemes.put(serviceAuthInfo.getParameterName(), securityScheme);
        components.setSecuritySchemes(securitySchemes);

        return components;
    }

    /**
     * 创建路径配置
     */
    private Map<String, Map<String, Operation>> createPaths(ToolBoxDto toolBoxDto, Operation operation) {
        Map<String, Operation> methodOperationMap = new HashMap<>();
        methodOperationMap.put(toolBoxDto.getMethod(), operation);

        Map<String, Map<String, Operation>> paths = new HashMap<>();
        String path = getPathCompatible(toolBoxDto.getEndPoint());
        paths.put(path, methodOperationMap);

        return paths;
    }

    /**
     * 创建MediaType映射
     */
    private Map<String, MediaType> createMediaTypeMap(List<WebSchemaItem> toolRequestBody) {
        MediaType mediaType = new MediaType();
        Schema schema = new Schema();

        Map<String, Property> propertyMap = new HashMap<>();
        List<String> required = recurGenProperties(toolRequestBody, propertyMap);

        if (!required.isEmpty()) {
            schema.setRequired(required);
        }
        schema.setProperties(propertyMap);
        schema.setType(OpenApiConst.SCHEMA_TYPE_OBJECT);
        mediaType.setSchema(schema);

        Map<String, MediaType> content = new HashMap<>();
        content.put(org.springframework.http.MediaType.APPLICATION_JSON_VALUE, mediaType);
        return content;
    }

    public List<ToolBoxVo> getToolVersion(String toolId) {
        List<ToolBox> toolBoxes = toolBoxMapper.selectList(
                Wrappers.<ToolBox>lambdaQuery()
                        .eq(ToolBox::getToolId, toolId)
                        .eq(ToolBox::getDeleted, false)
                        .orderByDesc(ToolBox::getCreateTime));
        if (CollectionUtils.isEmpty(toolBoxes)) {
            log.error("tool not exist, toolId={}", toolId);
            throw new BusinessException(ResponseEnum.TOOLBOX_NOT_EXIST);
        }
        ToolBox toolBox = toolBoxes.get(0);
        boolean flag = toolBox.getIsPublic() || bizConfig.getAdminUid().toString().equals(toolBox.getUserId());
        if (flag) {
            // 官方工具
            return toolBoxes.stream().map(toolBoxItem -> {
                ToolBoxVo toolBoxVo = new ToolBoxVo();
                BeanUtils.copyProperties(toolBoxItem, toolBoxVo);
                toolBoxVo.setWebSchema(filterDisPlaySchema(toolBoxItem.getWebSchema()));
                toolBoxVo.setSchema(StringUtils.EMPTY);
                toolBoxVo.setAuthInfo(StringUtils.EMPTY);
                toolBoxVo.setAddress(s3UtilClient.getS3Prefix());
                return toolBoxVo;
            }).collect(Collectors.toList());
        } else {
            Long spaceId = SpaceInfoUtil.getSpaceId();
            if (spaceId != null) {
                if (spaceId.equals(toolBox.getSpaceId())) {
                    return toolBoxes.stream().map(toolBoxItem -> {
                        ToolBoxVo toolBoxVo = new ToolBoxVo();
                        BeanUtils.copyProperties(toolBoxItem, toolBoxVo);
                        toolBoxVo.setAddress(s3UtilClient.getS3Prefix());
                        return toolBoxVo;
                    }).collect(Collectors.toList());
                } else {
                    return Collections.emptyList();
                }
            } else {
                if (UserInfoManagerHandler.getUserId().equals(toolBox.getUserId())) {
                    return toolBoxes.stream().map(toolBoxItem -> {
                        ToolBoxVo toolBoxVo = new ToolBoxVo();
                        BeanUtils.copyProperties(toolBoxItem, toolBoxVo);
                        toolBoxVo.setAddress(s3UtilClient.getS3Prefix());
                        return toolBoxVo;
                    }).collect(Collectors.toList());
                } else {
                    return Collections.emptyList();
                }
            }
        }
    }

    public Map<String, String> getToolLatestVersion(List<String> toolIds) {
        List<ToolBox> tools = toolBoxMapper.getToolsLastVersion(toolIds);
        Map<String, String> toolLastVersionMap = new LinkedHashMap<>();
        tools.forEach(tool -> toolLastVersionMap.put(tool.getToolId(), tool.getVersion()));
        return toolLastVersionMap;
    }

    public void addToolOperateHistory(String toolId) {
        ToolBoxOperateHistory toolBoxOperateHistory = new ToolBoxOperateHistory();
        toolBoxOperateHistory.setToolId(toolId);
        toolBoxOperateHistory.setUid(UserInfoManagerHandler.getUserId());
        toolBoxOperateHistory.setType(2);
        toolBoxOperateHistoryMapper.insert(toolBoxOperateHistory);
    }

    public void publishSquare(Long id) {
        ToolBox toolBox = toolBoxMapper.selectById(id);
        toolBox.setIsPublic(true);
        List<ConfigInfo> toolV2 = configInfoService.getTags("tool_v2");
        Long toolTagId = 0L;
        if (toolV2 != null && !toolV2.isEmpty()) {
            ConfigInfo configInfo = toolV2.get(0);
            // 遍历 value 等于 tool 的
            if ("tool".equals(configInfo.getValue())) {
                toolTagId = configInfo.getId();
            }
        }
        toolBox.setToolTag(toolTagId.toString());
        toolBox.setUpdateTime(new Timestamp(System.currentTimeMillis()));
        toolBoxMapper.updateById(toolBox);

    }

    public void feedback(ToolBoxFeedbackReq toolBoxFeedbackReq) {
        ToolBoxFeedback toolBoxFeedback = new ToolBoxFeedback();
        BeanUtils.copyProperties(toolBoxFeedbackReq, toolBoxFeedback);
        toolBoxFeedback.setUserId(UserInfoManagerHandler.getUserId());
        toolBoxFeedbackMapper.insert(toolBoxFeedback);
    }
}
