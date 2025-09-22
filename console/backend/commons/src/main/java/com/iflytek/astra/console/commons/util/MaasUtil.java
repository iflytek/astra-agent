package com.iflytek.astra.console.commons.util;

import cn.hutool.core.collection.ListUtil;
import cn.hutool.core.util.ObjectUtil;
import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.iflytek.astra.console.commons.constant.ResponseEnum;
import com.iflytek.astra.console.commons.entity.bot.*;
import com.iflytek.astra.console.commons.entity.workflow.MaasApi;
import com.iflytek.astra.console.commons.enums.bot.BotUploadEnum;
import com.iflytek.astra.console.commons.exception.BusinessException;
import com.iflytek.astra.console.commons.mapper.bot.ChatBotBaseMapper;
import com.iflytek.astra.console.commons.service.bot.ChatBotTagService;
import com.iflytek.astra.console.commons.service.data.UserLangChainDataService;
import com.iflytek.astra.console.commons.service.workflow.impl.WorkflowBotParamServiceImpl;
import com.iflytek.astra.console.commons.util.I18nUtil;
import jakarta.annotation.Resource;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import okhttp3.*;
import org.apache.commons.lang3.StringUtils;
import org.redisson.api.RedissonClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.time.Duration;
import java.util.*;
import java.util.stream.Collectors;


@Slf4j
@Service
public class MaasUtil {
    private static final String X_AUTH_SOURCE_HEADER = "x-auth-source";
    private static final String X_AUTH_SOURCE_VALUE = "xfyun";

    private static final OkHttpClient HTTP_CLIENT = new OkHttpClient.Builder()
            .connectTimeout(Duration.ofSeconds(10))
            .readTimeout(Duration.ofSeconds(30))
            .writeTimeout(Duration.ofSeconds(30))
            .retryOnConnectionFailure(true)
            .build();

    @Resource
    private ChatBotBaseMapper chatBotBaseMapper;

    @Value("${maas.synchronizeWorkFlow}")
    private String synchronizeUrl;

    @Value("${maas.publish}")
    private String publishUrl;

    @Value("${maas.canPublishUrl}")
    private String trailStatusUrl;

    @Value("${maas.cloneWorkFlow}")
    private String cloneWorkFlowUrl;

    @Value("${maas.getInputs}")
    private String getInputsUrl;

    @Value("${maas.appId}")
    private String maasAppId;

    @Value("${maas.consumerId}")
    private String consumerId;

    @Value("${maas.consumerSecret}")
    private String consumerSecret;

    @Value("${maas.consumerKey}")
    private String consumerKey;

    @Value("${maas.publishApi}")
    private String publishApi;

    @Value("${maas.authApi}")
    private String authApi;

    @Value("${maas.mcpHost}")
    private String mcpHost;

    @Value("${maas.mcpRegister}")
    private String mcpReleaseUrl;

    public static final String PREFIX_MASS_COPY = "mass_copy_";
    private static final String BOT_TAG_LIST = "bot_tag_list";

    @Autowired
    private UserLangChainDataService userLangChainDataService;

    @Autowired
    private ChatBotTagService chatBotTagService;

    @Autowired
    private RedissonClient redissonClient;

    private final OkHttpClient client = new OkHttpClient();

    public static final List<String> NO_SUPPORT_TYPE = ListUtil.of("string", "integer", "boolean", "number",
            "object", "array-string", "array-integer",
            "array-boolean", "array-number", "array-object");

    public JSONObject deleteSynchronize(Integer botId, Long spaceId, HttpServletRequest request) {
        if (botId == null || spaceId == null || request == null) {
            log.error("Parameters cannot be null: botId={}, spaceId={}, request={}", botId, spaceId, request);
            return new JSONObject();
        }

        ChatBotBase base = chatBotBaseMapper.selectById(botId);
        if (base == null || 3 != base.getVersion()) {
            return new JSONObject();
        }

        List<UserLangChainInfo> botInfo = userLangChainDataService.findListByBotId(botId);
        if (Objects.isNull(botInfo) || botInfo.isEmpty()) {
            return new JSONObject();
        }

        UserLangChainInfo firstInfo = botInfo.get(0);
        if (firstInfo.getMaasId() == null) {
            log.error("MaasId is null, botId: {}", botId);
            return new JSONObject();
        }

        String maasId = String.valueOf(firstInfo.getMaasId());
        String authHeader = getAuthorizationHeader(request);

        // Build form data
        FormBody formBody = new FormBody.Builder()
                .add("id", maasId)
                .add("spaceId", String.valueOf(spaceId))
                .build();

        // Build request
        Request deleteRequest = new Request.Builder()
                .url(synchronizeUrl)
                .delete(formBody)
                .addHeader("Authorization", authHeader)
                .addHeader(X_AUTH_SOURCE_HEADER, X_AUTH_SOURCE_VALUE)
                .build();

        String response;
        try (Response httpResponse = HTTP_CLIENT.newCall(deleteRequest).execute()) {
            ResponseBody responseBody = httpResponse.body();
            if (responseBody != null) {
                response = responseBody.string();
            } else {
                log.error("Delete mass workflow request response is empty");
                return new JSONObject();
            }
        } catch (IOException e) {
            log.error("Delete mass workflow request failed: {}", e.getMessage());
            return new JSONObject();
        }
        JSONObject res = JSON.parseObject(response);
        if (res.getInteger("code") != 0) {
            log.info("------ Delete mass workflow failed, reason: {}", response);
            return new JSONObject();
        }
        return res;

    }

    public JSONObject synchronizeWorkFlow(UserLangChainInfo userLangChainInfo, BotCreateForm botCreateForm,
                                          HttpServletRequest request, Long spaceId) {
        AdvancedConfig advancedConfig = new AdvancedConfig(botCreateForm.getPrologue(), botCreateForm.getInputExample(), botCreateForm.getAppBackground());
        JSONObject param = new JSONObject();
        param.put("avatarIcon", botCreateForm.getAvatar());
        param.put("avatarColor", "");
        param.put("description", botCreateForm.getBotDesc());
        param.put("advancedConfig", advancedConfig);
        param.put("appId", maasAppId);
        param.put("domain", "generalv3.5");
        param.put("name", botCreateForm.getName());
        param.put("spaceId", spaceId);
        JSONObject ext = new JSONObject();
        ext.put("botId", botCreateForm.getBotId());
        param.put("ext", ext);
        String authHeader = getAuthorizationHeader(request);

        // Not empty, use PUT request for update
        String httpMethod;
        if (Objects.nonNull(userLangChainInfo)) {
            Long maasId = userLangChainInfo.getMaasId();
            param.put("id", maasId);
            param.put("flowId", userLangChainInfo.getFlowId());
            httpMethod = "PUT";
            redissonClient.getBucket(generatePrefix(maasId.toString(), botCreateForm.getBotId())).set(maasId, Duration.ofSeconds(60));
        } else {
            // If it's newly created, then it's empty, use POST request
            httpMethod = "POST";
        }
        log.info("----- mass synchronization request body: {}", JSONObject.toJSONString(param));

        // Build request body
        RequestBody requestBody = RequestBody.create(
                JSONObject.toJSONString(param),
                MediaType.parse("application/json; charset=utf-8"));

        // Build request
        Request.Builder requestBuilder = new Request.Builder()
                .url(synchronizeUrl)
                .addHeader("Authorization", authHeader)
                .addHeader(X_AUTH_SOURCE_HEADER, X_AUTH_SOURCE_VALUE)
                .addHeader("Lang-Code", I18nUtil.getLanguage());

        if ("PUT".equals(httpMethod)) {
            requestBuilder.put(requestBody);
        } else {
            requestBuilder.post(requestBody);
        }

        Request synchronizeRequest = requestBuilder.build();

        String response;
        try (Response httpResponse = HTTP_CLIENT.newCall(synchronizeRequest).execute()) {
            ResponseBody responseBody = httpResponse.body();
            if (responseBody != null) {
                response = responseBody.string();
            } else {
                log.error("Synchronize mass workflow request response is empty");
                return new JSONObject();
            }
        } catch (IOException e) {
            log.error("Synchronize mass workflow request failed: {}", e.getMessage());
            return new JSONObject();
        }

        JSONObject res = JSONObject.parseObject(response);
        if (res.getInteger("code") != 0) {
            log.error("------ Synchronize mass workflow failed, reason: {}", res);
            return new JSONObject();
        }
        return res;
    }

    @Deprecated(since = "1.0.0", forRemoval = true)
    public static String getRequestCookies(HttpServletRequest request) {
        Cookie[] cookies = request.getCookies();
        if (cookies != null) {
            return Arrays.stream(cookies)
                    .map(cookie -> cookie.getName() + "=" + cookie.getValue())
                    .collect(Collectors.joining("; "));
        }
        return "";
    }

    public static String getAuthorizationHeader(HttpServletRequest request) {
        String authHeader = request.getHeader("Authorization");
        if (StringUtils.isNotBlank(authHeader)) {
            return authHeader;
        }
        log.debug("MaaSUtil.getAuthorizationToken(): Authorization header is empty");
        return "";
    }

    /**
     * Handle file type
     *
     * @param type
     * @param param
     * @return
     */
    public static int getFileType(String type, JSONObject param) {
        if (StringUtils.isBlank(type)) {
            return BotUploadEnum.NONE.getValue();
        }
        switch (type.toLowerCase()) {
            case "pdf":
                return WorkflowBotParamServiceImpl.isFileArray(param) ? BotUploadEnum.DOC_ARRAY.getValue() : BotUploadEnum.DOC.getValue();
            case "image":
                return WorkflowBotParamServiceImpl.isFileArray(param) ? BotUploadEnum.IMG_ARRAY.getValue() : BotUploadEnum.IMG.getValue();
            case "doc":
                return WorkflowBotParamServiceImpl.isFileArray(param) ? BotUploadEnum.DOC2_ARRAY.getValue() : BotUploadEnum.DOC2.getValue();
            case "ppt":
                return WorkflowBotParamServiceImpl.isFileArray(param) ? BotUploadEnum.PPT_ARRAY.getValue() : BotUploadEnum.PPT.getValue();
            case "excel":
                return WorkflowBotParamServiceImpl.isFileArray(param) ? BotUploadEnum.EXCEL_ARRAY.getValue() : BotUploadEnum.EXCEL.getValue();
            case "txt":
                return WorkflowBotParamServiceImpl.isFileArray(param) ? BotUploadEnum.TXT_ARRAY.getValue() : BotUploadEnum.TXT.getValue();
            case "audio":
                return WorkflowBotParamServiceImpl.isFileArray(param) ? BotUploadEnum.AUDIO_ARRAY.getValue() : BotUploadEnum.AUDIO.getValue();
            default:
                return BotUploadEnum.NONE.getValue();
        }
    }

    public static String generatePrefix(String uid, Integer botId) {
        return PREFIX_MASS_COPY + uid + "_" + botId;
    }

    /**
     * 编排助手设置标签
     */
    @Transactional
    public void setBotTag(JSONObject botInfo) {
        try {
            // 从 redis 中拿到助手标签映射表
            // 大致结构如下 [{"name":"知识库","tag":["知识库"]}, 省略..................]
            String botTagList = redissonClient.getBucket(BOT_TAG_LIST).get().toString();
            if (StringUtils.isNotBlank(botTagList)) {
                JSONArray jsonBotTag = JSONArray.parseArray(botTagList);
                Integer botId = botInfo.getInteger("botId");
                JSONArray nodes = botInfo.getJSONObject("data").getJSONArray("nodes");
                // 统计 node 名称出现的次数， 因为一个节点出现一次与多次时对应的标签不一定相同
                Map<String, Integer> nodeNameCountMap = new HashMap<>();
                for (int i = 0; i < nodes.size(); i++) {
                    String name = nodes.getJSONObject(i).getJSONObject("data").getJSONObject("nodeMeta").getString("aliasName");
                    nodeNameCountMap.put(name, nodeNameCountMap.getOrDefault(name, 0) + 1);
                }
                // 最终的标签列表，确保没有重复的标签
                HashSet<BotTag> tags = new HashSet<>();
                for (int i = 0; i < nodes.size(); i++) {
                    String name = nodes.getJSONObject(i).getJSONObject("data").getJSONObject("nodeMeta").getString("aliasName");
                    for (int j = 0; j < jsonBotTag.size(); j++) {
                        JSONObject botTag = (JSONObject) jsonBotTag.get(j);
                        if (botTag.getString("name").equals(name)) {
                            if (nodeNameCountMap.get(name) > 1) {
                                BotTag multiNodeTag = botTag.getJSONObject("tag").getObject("multiNode", BotTag.class);
                                tags.add(multiNodeTag);
                            } else {
                                BotTag tag = botTag.getObject("tag", BotTag.class);
                                tags.add(tag);
                            }
                        }
                    }
                }
                // 重新发布的时候先将原来的标签变的不可用
                ChatBotTag updateChatBotTag = new ChatBotTag();
                updateChatBotTag.setIsAct(0);
                chatBotTagService.update(updateChatBotTag, Wrappers.lambdaQuery(ChatBotTag.class).eq(ChatBotTag::getBotId, botId));
                // 发布此次的标签, 最多只需要3条标签
                List<ChatBotTag> chatBotTagList = new ArrayList<>();
                List<BotTag> list = new ArrayList<>(tags);
                // 根据index降序排序
                list.sort((a, b) -> b.getIndex() - a.getIndex());
                for (int i = 0; i < list.size(); i++) {
                    BotTag item = list.get(i);
                    ChatBotTag chatBotTag = new ChatBotTag();
                    chatBotTag.setBotId(botId);
                    chatBotTag.setTag(item.getTagName());
                    chatBotTag.setOrder(item.getIndex());
                    chatBotTagList.add(chatBotTag);
                }
                chatBotTagService.saveBatch(chatBotTagList);
            } else {
                log.error("助手标签映射表在 Redis 中为null");
            }
        } catch (Exception e) {
            log.error("助手标签解析失败， 请求入参为：{}， error：{}", botInfo.toJSONString(0), e.getMessage());
            throw e;
        }
    }

    public JSONObject createApi(String flowId, String appid) {
        log.info("----- 发布mass 工作流flowId: {}", flowId);
        MaasApi maasApi = new MaasApi(flowId, appid);
        Map<String, String> pubAuth = AuthStringUtil.authMap(publishApi, "POST", consumerKey, consumerSecret, JSONObject.toJSONString(maasApi));
        // Build request body
        RequestBody requestBody = RequestBody.create(
                JSONObject.toJSONString(pubAuth),
                MediaType.parse("application/json; charset=utf-8"));
        Request pubRequest = new Request.Builder()
                .url(publishApi)
                .post(requestBody)
                .addHeader("X-Consumer-Username", consumerId)
                .addHeader("Lang-Code", I18nUtil.getLanguage())
                .headers(Headers.of(pubAuth))
                .addHeader(X_AUTH_SOURCE_HEADER, X_AUTH_SOURCE_VALUE)
                .build();

        String response;
        try (Response httpResponse = HTTP_CLIENT.newCall(pubRequest).execute()) {
            ResponseBody responseBody = httpResponse.body();
            if (responseBody != null) {
                response = responseBody.string();
            } else {
                log.error("Delete mass workflow request response is empty");
                return new JSONObject();
            }
        } catch (IOException e) {
            throw new BusinessException(ResponseEnum.CREATE_BOT_FAILED);
        }
        log.info("----- 发布maas api 响应: {}", response);
        JSONObject res = JSONObject.parseObject(response);
        if (res.getInteger("code") != 0) {
            log.info("------ 发布maas api 失败, massId: {},appid: {}, reason: {}", flowId, appid, response);
            throw new BusinessException(ResponseEnum.CREATE_BOT_FAILED);
        }

        Map<String, String> authMap = AuthStringUtil.authMap(authApi, "POST", consumerKey, consumerSecret, JSONObject.toJSONString(maasApi));
        // Build request body
        requestBody = RequestBody.create(
                JSONObject.toJSONString(authMap),
                MediaType.parse("application/json; charset=utf-8"));
        Request authRequest = new Request.Builder()
                .url(authApi)
                .post(requestBody)
                .addHeader("X-Consumer-Username", consumerId)
                .addHeader("Lang-Code", I18nUtil.getLanguage())
                .headers(Headers.of(pubAuth))
                .addHeader(X_AUTH_SOURCE_HEADER, X_AUTH_SOURCE_VALUE)
                .build();

        String authResponse = "";
        try (Response httpResponse = HTTP_CLIENT.newCall(authRequest).execute()) {
            ResponseBody responseBody = httpResponse.body();
            if (responseBody != null) {
                authResponse = responseBody.string();
            } else {
                log.error("Delete mass workflow request response is empty");
                return new JSONObject();
            }
        } catch (IOException e) {
            throw new BusinessException(ResponseEnum.CREATE_BOT_FAILED);
        }
        log.info("----- 绑定maas api 响应: {}", authResponse);
        JSONObject authResJson = JSONObject.parseObject(authResponse);
        if (authResJson.getInteger("code") != 0) {
            log.info("------ 绑定maas api 失败, massId: {},appid: {}, reason: {}", flowId, appid, authResJson);
            throw new BusinessException(ResponseEnum.CREATE_BOT_FAILED);
        }
        return new JSONObject();
    }

    public JSONObject copyWorkFlow(Long maasId, String uid) {
        log.info("----- 复制maas 工作流id: {}", maasId);
        HttpUrl httpUrl = HttpUrl.parse(cloneWorkFlowUrl + "/workflow/internal-clone")
                .newBuilder()
                .addQueryParameter("id", String.valueOf(maasId)) // 使用您的 massId
                .addQueryParameter("password", "xfyun") // 根据目标方法逻辑设置密码
                .build();
        Request httpRequest = new Request.Builder()
                .url(httpUrl)
                .get() // 指定 GET 方法
                .build();
        String responseBody = "";
        try (Response response = client.newCall(httpRequest).execute()) {
            if (!response.isSuccessful()) {
                // 处理请求失败的情况
                throw new IOException("Unexpected code " + response);
            }
            responseBody = response.body().string();
        } catch (IOException e) {
            // 处理异常
            log.error("Failed to call internal-clone endpoint", e);
            throw new BusinessException(ResponseEnum.CLONE_BOT_FAILED);
        }
        JSONObject resClone = JSON.parseObject(responseBody);

        if (resClone == null) {
            log.info("------ 复制maas 工作流失败, maasId: {}, reason: {}", maasId, resClone);
            return null;
        }
        return resClone;
    }

    @Transactional
    public JSONObject getInputsType(Integer botId, UserLangChainInfo chainInfo, String authorizationHeaderValue) {
        String flowId = chainInfo.getFlowId();

        // Build URL with query parameter
        String urlWithParams = getInputsUrl + "?flowId=" + flowId;

        // Build request
        Request getInputsRequest = new Request.Builder()
                .url(urlWithParams)
                .get()
                .addHeader("Authorization", authorizationHeaderValue)
                .addHeader(X_AUTH_SOURCE_HEADER, X_AUTH_SOURCE_VALUE)
                .build();

        String response;
        try (Response httpResponse = HTTP_CLIENT.newCall(getInputsRequest).execute()) {
            ResponseBody responseBody = httpResponse.body();
            if (responseBody != null) {
                response = responseBody.string();
            } else {
                log.error("Get inputs type request response is empty");
                return null;
            }
        } catch (IOException e) {
            log.error("Get inputs type request failed: {}", e.getMessage());
            return null;
        }
        JSONObject res = JSON.parseObject(response);
        if (res.getInteger("code") != 0) {
            log.info("------ 获取工作流入参类型失败, flowId: {}, reason: {}", flowId, response);
            return null;
        }
        log.info("----- flowId: {} 工作流的入参: {}", flowId, response);
        JSONArray dataArray = res.getJSONArray("data");
        // 先把固定输入删掉
        List<JSONObject> filteredParams = new ArrayList<>();
        for (int i = 0; i < dataArray.size(); i++) {
            JSONObject param = dataArray.getJSONObject(i);
            if ("AGENT_USER_INPUT".equals(param.getString("name"))) {
                continue;
            }
            filteredParams.add(param);
        }
        LambdaUpdateWrapper<ChatBotBase> wrapper = new LambdaUpdateWrapper<>();
        wrapper.eq(ChatBotBase::getId, botId);
        List<JSONObject> extraInputs = new ArrayList<>();
        if (!filteredParams.isEmpty()) {
            // 获得把这个参数的输入类型
            for (JSONObject param : filteredParams) {
                String type;
                JSONObject extraInput = new JSONObject();
                if (Objects.nonNull(param.getJSONArray("allowedFileType"))) {
                    type = param.getJSONArray("allowedFileType").getString(0).toLowerCase();
                    extraInput.put(param.getString("name"), type);
                    extraInput.put("required", param.getBoolean("required"));

                    extraInput.put("schema", param.get("schema"));
                    extraInput.put("name", param.getString("name"));
                    extraInput.put("type", type);
                    extraInput.put("fullParam", param);
                } else {
                    // 非文件&非String类型的参数（eg；integer/boolean...）处理
                    extraInput.put(param.getString("name"), param.getJSONObject("schema").getString("type"));
                    extraInput.put(param.getString("name") + "_required", param.getBoolean("required"));
                    extraInput.put("name", param.getString("name"));
                    extraInput.put("type", param.getJSONObject("schema").getString("type"));
                    extraInput.put("schema", param.get("schema"));
                }

                extraInputs.add(extraInput);
            }
            JSONObject oldExtraInputs = keepOldValue(extraInputs);
            wrapper.set(ChatBotBase::getSupportUpload, getFileType(oldExtraInputs.getString("type"), oldExtraInputs));
        } else {
            wrapper.set(ChatBotBase::getSupportUpload, BotUploadEnum.NONE.getValue());
        }
        // 更新字段
        if (!Objects.isNull(wrapper.getSqlSet())) {
            chatBotBaseMapper.update(null, wrapper);
        }
        // 更新记录
        chainInfo.setExtraInputsConfig(JSON.toJSONString(extraInputs));
        chainInfo.setExtraInputs(JSON.toJSONString(keepOldValue(extraInputs)));
        userLangChainDataService.updateByBotId(botId, chainInfo);
        return res;
    }

    /**
     * 保持老的逻辑, 找到第一个不为文件数组类型的参数，并返回
     *
     * @param extraInputs
     * @return
     */
    public static JSONObject keepOldValue(List<JSONObject> extraInputs) {
        if (ObjectUtil.isEmpty(extraInputs)) {
            return new JSONObject();
        }
        for (JSONObject extraInput : extraInputs) {
            // 不是文件数组& 不是其他基础类型
            if (!isFileArray(extraInput)) {
                if (!NO_SUPPORT_TYPE.contains(extraInput.getString("type"))) {
                    return extraInput;
                }
            }
        }
        return new JSONObject();
    }

    /**
     * 判断参数是否为数组类型
     *
     * @param param
     * @return
     */
    public static boolean isFileArray(JSONObject param) {
        try {
            if ("array-string".equalsIgnoreCase(param.getJSONObject("schema").getString("type"))) {
                return true;
            } else {
                return false;
            }
        } catch (Exception e) {
            log.error("判断参数是否为数组类型异常: {}", e.getMessage());
            return false;
        }
    }
}
