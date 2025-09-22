package com.iflytek.astra.console.toolkit.service.model;

import com.alibaba.fastjson2.JSONObject;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.iflytek.astra.console.commons.exception.BusinessException;
import com.iflytek.astra.console.commons.response.ApiResult;
import com.iflytek.astra.console.toolkit.entity.biz.modelconfig.LocalModelDto;
import com.iflytek.astra.console.toolkit.entity.biz.modelconfig.ModelDto;
import com.iflytek.astra.console.toolkit.entity.biz.modelconfig.ModelValidationRequest;
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
import com.iflytek.astra.console.toolkit.mapper.ConfigInfoMapper;
import com.iflytek.astra.console.toolkit.mapper.bot.SparkBotMapper;
import com.iflytek.astra.console.toolkit.mapper.model.ModelMapper;
import com.iflytek.astra.console.toolkit.mapper.workflow.WorkflowMapper;
import com.iflytek.astra.console.toolkit.util.S3Util;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.*;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ModelServiceTest {

    @Mock
    private ModelMapper mockMapper;
    @Mock
    private LLMService mockLlmService;
    @Mock
    private ConfigInfoMapper mockConfigInfoMapper;
    @Mock
    private RestTemplate mockRestTemplate;
    @Mock
    private S3Util mockS3UtilClient;
    @Mock
    private WorkflowMapper mockWorkflowMapper;
    @Mock
    private SparkBotMapper mockSparkBotMapper;
    @Mock
    private ModelCategoryService mockModelCategoryService;
    @Mock
    private ModelCommonService mockModelCommonService;
    @Mock
    private LocalModelHandler mockModelHandler;

    private ModelService modelServiceUnderTest;

    @BeforeEach
    void setUp() {
        modelServiceUnderTest = new ModelService(mockMapper, mockLlmService, mockConfigInfoMapper, mockRestTemplate,
                mockS3UtilClient, mockWorkflowMapper, mockSparkBotMapper, mockModelCategoryService,
                mockModelCommonService, mockModelHandler);
        modelServiceUnderTest.env = "env";
    }

    @Test
    void testValidateModel_ok_bypassDecrypt() {
        // 1) 构造请求：设置 id 且 apiKeyMasked=false -> 走“复用库里apiKey”分支
        ModelValidationRequest request = new ModelValidationRequest();
        // request.setId(100L);
        request.setApiKeyMasked(false);
        request.setEndpoint("https://api.deepseek.com/v1/chat/completions");
        request.setModelName("dsv3");
        request.setApiKey(
                "KKaiINFjdi1sOTIo2zqBZcQk8TZCRxJ11kYnB82vV6eYyonlhhXA9LsW0xtJX2vj92r7nd+hY7AiF83sQN0sC9K/LBU8uUOmcxm0clY0H2uBHqMPlH0aPv+RQQYwOBCeScFBVguZ73JOod/IgHr3DIw4r2zEfbWDGxXTUVS+/D99E8BRsM7kBZlL8oXbWj9EGRuv0DbFNxkkHhxK4jEo7Uyn3FKr7juk6BQx9xu2n6uyOlxyqVM/1vu/AzCQfE8Ksmq4vdcflYszMMqwwj3koh4umfLgCvCDW5VBz0Z9fjfu9o5BDnGO5wxW9Z0/yQvx58s3X9ZjYy83FYpKSAZchw==");
        request.setDomain("deepseek-chat");
        request.setDescription("deepSeek model");
        request.setUid("18879796086");

        // 2) mock 从库里拿旧模型，直接给一个明文 key
        Model dbModel = new Model();
        dbModel.setId(100L);
        dbModel.setUid("18879796086");
        dbModel.setApiKey("sk-test"); // 直接明文，避免 decrypt
        when(mockMapper.selectById(100L)).thenReturn(dbModel);

        // 3) SSRF黑名单配置（可以返回空，逻辑能兜住）
        when(mockConfigInfoMapper.getListByCategory("NETWORK_SEGMENT_BLACK_LIST"))
                .thenReturn(Collections.emptyList());

        // 4) 工作流前缀过滤配置（允许为空，为空时 just skip）
        when(mockConfigInfoMapper.getByCategoryAndCode("LLM_WORKFLOW_FILTER", "self-model"))
                .thenReturn(null);
        when(mockWorkflowMapper.selectList(any(LambdaQueryWrapper.class)))
                .thenReturn(Collections.emptyList());

        // 5) mock 调用大模型接口并返回一个合法 JSON（必须包含 choices + usage）
        String okJson = "{\"choices\":[], \"usage\": {\"prompt_tokens\":1, \"completion_tokens\":1, \"total_tokens\":2}}";
        when(mockRestTemplate.exchange(
                eq("https://api.deepseek.com/v1/chat/completions"),
                eq(HttpMethod.POST),
                any(HttpEntity.class),
                eq(String.class))).thenReturn(new ResponseEntity<>(okJson, HttpStatus.OK));

        // 6) 执行
        String result = modelServiceUnderTest.validateModel(request);
        assertEquals("模型校验通过", result);

        // 7) 因为是新增（id 只是为了 bypass decrypt，saveOrUpdate 里仍会判定 isNew 与否）
        // 这里不强求精确实体匹配，避免 verify 失败
        verify(mockMapper).insert(any(Model.class));
        verify(mockModelCategoryService).saveAll(any(ModelCategoryReq.class));
    }

    @Test
    void testValidateModel_ConfigInfoMapperSelectOneReturnsNull() {
        // Setup
        final ModelValidationRequest request = new ModelValidationRequest();
        request.setEndpoint("endpoint");
        request.setApiKey("apiKey");
        request.setModelName("modelName");
        request.setDomain("domain");
        request.setDescription("description");

        when(mockConfigInfoMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(null);

        // Run the test
        assertThrows(BusinessException.class, () -> modelServiceUnderTest.validateModel(request));
    }

    @Test
    void testValidateModel_ConfigInfoMapperGetListByCategoryReturnsNull() {
        // Setup
        final ModelValidationRequest request = new ModelValidationRequest();
        request.setEndpoint("endpoint");
        request.setApiKey("apiKey");
        request.setModelName("modelName");
        request.setDomain("domain");
        request.setDescription("description");

        // Configure ConfigInfoMapper.selectOne(...).
        final ConfigInfo configInfo = new ConfigInfo();
        configInfo.setId(0L);
        configInfo.setCategory("category");
        configInfo.setCode("code");
        configInfo.setName("name");
        configInfo.setValue("value");
        when(mockConfigInfoMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(configInfo);

        when(mockConfigInfoMapper.getListByCategory("NETWORK_SEGMENT_BLACK_LIST")).thenReturn(null);
        when(mockRestTemplate.exchange("url", HttpMethod.POST,
                new HttpEntity<>(Map.ofEntries(Map.entry("value", "value")), new HttpHeaders()),
                String.class)).thenReturn(new ResponseEntity<>("body", HttpStatus.OK));

        // Configure WorkflowMapper.selectList(...).
        final Workflow workflow = new Workflow();
        workflow.setId(0L);
        workflow.setAppId("appId");
        workflow.setFlowId("flowId");
        workflow.setName("name");
        workflow.setData("data");
        final List<Workflow> workflows = List.of(workflow);
        when(mockWorkflowMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(workflows);

        // Configure ConfigInfoMapper.getByCategoryAndCode(...).
        final ConfigInfo configInfo1 = new ConfigInfo();
        configInfo1.setId(0L);
        configInfo1.setCategory("category");
        configInfo1.setCode("code");
        configInfo1.setName("name");
        configInfo1.setValue("value");
        when(mockConfigInfoMapper.getByCategoryAndCode("LLM_WORKFLOW_FILTER", "self-model")).thenReturn(configInfo1);

        // Run the test
        final String result = modelServiceUnderTest.validateModel(request);

        // Verify the results
        assertEquals("模型校验通过", result);

        // Confirm WorkflowMapper.updateById(...).
        final Workflow entity = new Workflow();
        entity.setId(0L);
        entity.setAppId("appId");
        entity.setFlowId("flowId");
        entity.setName("name");
        entity.setData("data");
        verify(mockWorkflowMapper).updateById(entity);

        // Confirm ModelMapper.insert(...).
        final Model entity1 = new Model();
        entity1.setId(0L);
        entity1.setName("name");
        entity1.setDesc("desc");
        entity1.setSource(0);
        entity1.setUid("uid");
        verify(mockMapper).insert(entity1);

        // Confirm ModelMapper.updateById(...).
        final Model entity2 = new Model();
        entity2.setId(0L);
        entity2.setName("name");
        entity2.setDesc("desc");
        entity2.setSource(0);
        entity2.setUid("uid");
        verify(mockMapper).updateById(entity2);

        // Confirm ModelCategoryService.saveAll(...).
        final ModelCategoryReq req = new ModelCategoryReq();
        req.setModelId(0L);
        req.setCategorySystemIds(List.of(0L));
        final ModelCategoryReq.CustomItem categoryCustom = new ModelCategoryReq.CustomItem();
        categoryCustom.setPid(0L);
        categoryCustom.setCustomName("customName");
        req.setCategoryCustom(categoryCustom);
        verify(mockModelCategoryService).saveAll(req);
    }

    @Test
    void testValidateModel_ConfigInfoMapperGetListByCategoryReturnsNoItems() {
        // Setup
        final ModelValidationRequest request = new ModelValidationRequest();
        request.setEndpoint("endpoint");
        request.setApiKey("apiKey");
        request.setModelName("modelName");
        request.setDomain("domain");
        request.setDescription("description");

        // Configure ConfigInfoMapper.selectOne(...).
        final ConfigInfo configInfo = new ConfigInfo();
        configInfo.setId(0L);
        configInfo.setCategory("category");
        configInfo.setCode("code");
        configInfo.setName("name");
        configInfo.setValue("value");
        when(mockConfigInfoMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(configInfo);

        when(mockConfigInfoMapper.getListByCategory("NETWORK_SEGMENT_BLACK_LIST")).thenReturn(Collections.emptyList());
        when(mockRestTemplate.exchange("url", HttpMethod.POST,
                new HttpEntity<>(Map.ofEntries(Map.entry("value", "value")), new HttpHeaders()),
                String.class)).thenReturn(new ResponseEntity<>("body", HttpStatus.OK));

        // Configure WorkflowMapper.selectList(...).
        final Workflow workflow = new Workflow();
        workflow.setId(0L);
        workflow.setAppId("appId");
        workflow.setFlowId("flowId");
        workflow.setName("name");
        workflow.setData("data");
        final List<Workflow> workflows = List.of(workflow);
        when(mockWorkflowMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(workflows);

        // Configure ConfigInfoMapper.getByCategoryAndCode(...).
        final ConfigInfo configInfo1 = new ConfigInfo();
        configInfo1.setId(0L);
        configInfo1.setCategory("category");
        configInfo1.setCode("code");
        configInfo1.setName("name");
        configInfo1.setValue("value");
        when(mockConfigInfoMapper.getByCategoryAndCode("LLM_WORKFLOW_FILTER", "self-model")).thenReturn(configInfo1);

        // Run the test
        final String result = modelServiceUnderTest.validateModel(request);

        // Verify the results
        assertEquals("模型校验通过", result);

        // Confirm WorkflowMapper.updateById(...).
        final Workflow entity = new Workflow();
        entity.setId(0L);
        entity.setAppId("appId");
        entity.setFlowId("flowId");
        entity.setName("name");
        entity.setData("data");
        verify(mockWorkflowMapper).updateById(entity);

        // Confirm ModelMapper.insert(...).
        final Model entity1 = new Model();
        entity1.setId(0L);
        entity1.setName("name");
        entity1.setDesc("desc");
        entity1.setSource(0);
        entity1.setUid("uid");
        verify(mockMapper).insert(entity1);

        // Confirm ModelMapper.updateById(...).
        final Model entity2 = new Model();
        entity2.setId(0L);
        entity2.setName("name");
        entity2.setDesc("desc");
        entity2.setSource(0);
        entity2.setUid("uid");
        verify(mockMapper).updateById(entity2);

        // Confirm ModelCategoryService.saveAll(...).
        final ModelCategoryReq req = new ModelCategoryReq();
        req.setModelId(0L);
        req.setCategorySystemIds(List.of(0L));
        final ModelCategoryReq.CustomItem categoryCustom = new ModelCategoryReq.CustomItem();
        categoryCustom.setPid(0L);
        categoryCustom.setCustomName("customName");
        req.setCategoryCustom(categoryCustom);
        verify(mockModelCategoryService).saveAll(req);
    }

    @Test
    void testValidateModel_RestTemplateThrowsRestClientException() {
        // Setup
        final ModelValidationRequest request = new ModelValidationRequest();
        request.setEndpoint("endpoint");
        request.setApiKey("apiKey");
        request.setModelName("modelName");
        request.setDomain("domain");
        request.setDescription("description");

        // Configure ConfigInfoMapper.selectOne(...).
        final ConfigInfo configInfo = new ConfigInfo();
        configInfo.setId(0L);
        configInfo.setCategory("category");
        configInfo.setCode("code");
        configInfo.setName("name");
        configInfo.setValue("value");
        when(mockConfigInfoMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(configInfo);

        // Configure ConfigInfoMapper.getListByCategory(...).
        final ConfigInfo configInfo1 = new ConfigInfo();
        configInfo1.setId(0L);
        configInfo1.setCategory("category");
        configInfo1.setCode("code");
        configInfo1.setName("name");
        configInfo1.setValue("value");
        final List<ConfigInfo> configInfos = List.of(configInfo1);
        when(mockConfigInfoMapper.getListByCategory("NETWORK_SEGMENT_BLACK_LIST")).thenReturn(configInfos);

        when(mockRestTemplate.exchange("url", HttpMethod.POST,
                new HttpEntity<>(Map.ofEntries(Map.entry("value", "value")), new HttpHeaders()),
                String.class)).thenThrow(RestClientException.class);

        // Run the test
        assertThrows(BusinessException.class, () -> modelServiceUnderTest.validateModel(request));
    }

    @Test
    void testValidateModel_WorkflowMapperSelectListReturnsNoItems() {
        // Setup
        final ModelValidationRequest request = new ModelValidationRequest();
        request.setEndpoint("endpoint");
        request.setApiKey("apiKey");
        request.setModelName("modelName");
        request.setDomain("domain");
        request.setDescription("description");

        // Configure ConfigInfoMapper.selectOne(...).
        final ConfigInfo configInfo = new ConfigInfo();
        configInfo.setId(0L);
        configInfo.setCategory("category");
        configInfo.setCode("code");
        configInfo.setName("name");
        configInfo.setValue("value");
        when(mockConfigInfoMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(configInfo);

        // Configure ConfigInfoMapper.getListByCategory(...).
        final ConfigInfo configInfo1 = new ConfigInfo();
        configInfo1.setId(0L);
        configInfo1.setCategory("category");
        configInfo1.setCode("code");
        configInfo1.setName("name");
        configInfo1.setValue("value");
        final List<ConfigInfo> configInfos = List.of(configInfo1);
        when(mockConfigInfoMapper.getListByCategory("NETWORK_SEGMENT_BLACK_LIST")).thenReturn(configInfos);

        when(mockRestTemplate.exchange("url", HttpMethod.POST,
                new HttpEntity<>(Map.ofEntries(Map.entry("value", "value")), new HttpHeaders()),
                String.class)).thenReturn(new ResponseEntity<>("body", HttpStatus.OK));
        when(mockWorkflowMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(Collections.emptyList());

        // Configure ConfigInfoMapper.getByCategoryAndCode(...).
        final ConfigInfo configInfo2 = new ConfigInfo();
        configInfo2.setId(0L);
        configInfo2.setCategory("category");
        configInfo2.setCode("code");
        configInfo2.setName("name");
        configInfo2.setValue("value");
        when(mockConfigInfoMapper.getByCategoryAndCode("LLM_WORKFLOW_FILTER", "self-model")).thenReturn(configInfo2);

        // Run the test
        final String result = modelServiceUnderTest.validateModel(request);

        // Verify the results
        assertEquals("模型校验通过", result);

        // Confirm WorkflowMapper.updateById(...).
        final Workflow entity = new Workflow();
        entity.setId(0L);
        entity.setAppId("appId");
        entity.setFlowId("flowId");
        entity.setName("name");
        entity.setData("data");
        verify(mockWorkflowMapper).updateById(entity);

        // Confirm ModelMapper.insert(...).
        final Model entity1 = new Model();
        entity1.setId(0L);
        entity1.setName("name");
        entity1.setDesc("desc");
        entity1.setSource(0);
        entity1.setUid("uid");
        verify(mockMapper).insert(entity1);

        // Confirm ModelMapper.updateById(...).
        final Model entity2 = new Model();
        entity2.setId(0L);
        entity2.setName("name");
        entity2.setDesc("desc");
        entity2.setSource(0);
        entity2.setUid("uid");
        verify(mockMapper).updateById(entity2);

        // Confirm ModelCategoryService.saveAll(...).
        final ModelCategoryReq req = new ModelCategoryReq();
        req.setModelId(0L);
        req.setCategorySystemIds(List.of(0L));
        final ModelCategoryReq.CustomItem categoryCustom = new ModelCategoryReq.CustomItem();
        categoryCustom.setPid(0L);
        categoryCustom.setCustomName("customName");
        req.setCategoryCustom(categoryCustom);
        verify(mockModelCategoryService).saveAll(req);
    }

    @Test
    void testValidateModel_ConfigInfoMapperGetByCategoryAndCodeReturnsNull() {
        // Setup
        final ModelValidationRequest request = new ModelValidationRequest();
        request.setEndpoint("endpoint");
        request.setApiKey("apiKey");
        request.setModelName("modelName");
        request.setDomain("domain");
        request.setDescription("description");

        // Configure ConfigInfoMapper.selectOne(...).
        final ConfigInfo configInfo = new ConfigInfo();
        configInfo.setId(0L);
        configInfo.setCategory("category");
        configInfo.setCode("code");
        configInfo.setName("name");
        configInfo.setValue("value");
        when(mockConfigInfoMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(configInfo);

        // Configure ConfigInfoMapper.getListByCategory(...).
        final ConfigInfo configInfo1 = new ConfigInfo();
        configInfo1.setId(0L);
        configInfo1.setCategory("category");
        configInfo1.setCode("code");
        configInfo1.setName("name");
        configInfo1.setValue("value");
        final List<ConfigInfo> configInfos = List.of(configInfo1);
        when(mockConfigInfoMapper.getListByCategory("NETWORK_SEGMENT_BLACK_LIST")).thenReturn(configInfos);

        when(mockRestTemplate.exchange("url", HttpMethod.POST,
                new HttpEntity<>(Map.ofEntries(Map.entry("value", "value")), new HttpHeaders()),
                String.class)).thenReturn(new ResponseEntity<>("body", HttpStatus.OK));

        // Configure WorkflowMapper.selectList(...).
        final Workflow workflow = new Workflow();
        workflow.setId(0L);
        workflow.setAppId("appId");
        workflow.setFlowId("flowId");
        workflow.setName("name");
        workflow.setData("data");
        final List<Workflow> workflows = List.of(workflow);
        when(mockWorkflowMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(workflows);

        when(mockConfigInfoMapper.getByCategoryAndCode("LLM_WORKFLOW_FILTER", "self-model")).thenReturn(null);

        // Run the test
        final String result = modelServiceUnderTest.validateModel(request);

        // Verify the results
        assertEquals("模型校验通过", result);

        // Confirm WorkflowMapper.updateById(...).
        final Workflow entity = new Workflow();
        entity.setId(0L);
        entity.setAppId("appId");
        entity.setFlowId("flowId");
        entity.setName("name");
        entity.setData("data");
        verify(mockWorkflowMapper).updateById(entity);

        // Confirm ModelMapper.insert(...).
        final Model entity1 = new Model();
        entity1.setId(0L);
        entity1.setName("name");
        entity1.setDesc("desc");
        entity1.setSource(0);
        entity1.setUid("uid");
        verify(mockMapper).insert(entity1);

        // Confirm ModelMapper.updateById(...).
        final Model entity2 = new Model();
        entity2.setId(0L);
        entity2.setName("name");
        entity2.setDesc("desc");
        entity2.setSource(0);
        entity2.setUid("uid");
        verify(mockMapper).updateById(entity2);

        // Confirm ModelCategoryService.saveAll(...).
        final ModelCategoryReq req = new ModelCategoryReq();
        req.setModelId(0L);
        req.setCategorySystemIds(List.of(0L));
        final ModelCategoryReq.CustomItem categoryCustom = new ModelCategoryReq.CustomItem();
        categoryCustom.setPid(0L);
        categoryCustom.setCustomName("customName");
        req.setCategoryCustom(categoryCustom);
        verify(mockModelCategoryService).saveAll(req);
    }

    @Test
    void testGetConditionList() {
        // Setup
        final ModelDto dto = new ModelDto();
        dto.setType(0);
        dto.setFilter(0);
        dto.setName("name");
        dto.setPage(0);
        dto.setPageSize(0);

        final MockHttpServletRequest request = new MockHttpServletRequest();

        // Configure ConfigInfoMapper.getByCategoryAndCode(...).
        final ConfigInfo configInfo = new ConfigInfo();
        configInfo.setId(0L);
        configInfo.setCategory("category");
        configInfo.setCode("code");
        configInfo.setName("name");
        configInfo.setValue("value");
        when(mockConfigInfoMapper.getByCategoryAndCode("LLM_FILTER", "plan")).thenReturn(configInfo);

        // Configure ConfigInfoMapper.selectOne(...).
        final ConfigInfo configInfo1 = new ConfigInfo();
        configInfo1.setId(0L);
        configInfo1.setCategory("category");
        configInfo1.setCode("code");
        configInfo1.setName("name");
        configInfo1.setValue("value");
        when(mockConfigInfoMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(configInfo1);

        // Configure ModelMapper.selectList(...).
        final Model model = new Model();
        model.setId(0L);
        model.setName("name");
        model.setDesc("desc");
        model.setSource(0);
        model.setUid("uid");
        final List<Model> models = List.of(model);
        when(mockMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(models);

        when(mockS3UtilClient.getS3Prefix()).thenReturn("address");

        // Configure ModelCategoryService.getTree(...).
        final CategoryTreeVO categoryTreeVO = new CategoryTreeVO();
        categoryTreeVO.setId(0L);
        categoryTreeVO.setKey("key");
        categoryTreeVO.setName("name");
        categoryTreeVO.setSortOrder(0);
        categoryTreeVO.setChildren(List.of(new CategoryTreeVO()));
        final List<CategoryTreeVO> categoryTreeVOS = List.of(categoryTreeVO);
        when(mockModelCategoryService.getTree(0L)).thenReturn(categoryTreeVOS);

        // Configure ConfigInfoMapper.getListByCategory(...).
        final ConfigInfo configInfo2 = new ConfigInfo();
        configInfo2.setId(0L);
        configInfo2.setCategory("category");
        configInfo2.setCode("code");
        configInfo2.setName("name");
        configInfo2.setValue("value");
        final List<ConfigInfo> configInfos = List.of(configInfo2);
        when(mockConfigInfoMapper.getListByCategory("SPECIAL_MODEL")).thenReturn(configInfos);

        // Run the test
        final ApiResult result = modelServiceUnderTest.getConditionList(dto, request);

        // Verify the results
        // Confirm LLMService.getDataFromModelShelfList(...).
        final LLMInfoVo llmInfoVo = new LLMInfoVo();
        llmInfoVo.setLlmSource(0);
        llmInfoVo.setLlmId(0L);
        llmInfoVo.setStatus(0);
        llmInfoVo.setInfo("info");
        llmInfoVo.setIcon("icon");
        final List<LLMInfoVo> sceneSquareList = List.of(llmInfoVo);
        verify(mockLlmService).getDataFromModelShelfList(sceneSquareList, List.of("value"), "uid", "name");
    }

    @Test
    void testGetConditionList_ConfigInfoMapperGetByCategoryAndCodeReturnsNull() {
        // Setup
        final ModelDto dto = new ModelDto();
        dto.setType(0);
        dto.setFilter(0);
        dto.setName("name");
        dto.setPage(0);
        dto.setPageSize(0);

        final MockHttpServletRequest request = new MockHttpServletRequest();
        when(mockConfigInfoMapper.getByCategoryAndCode("LLM_FILTER", "plan")).thenReturn(null);

        // Run the test
        final ApiResult result = modelServiceUnderTest.getConditionList(dto, request);

        // Verify the results
        assertEquals(ApiResult.success(), result);
    }

    @Test
    void testGetConditionList_ConfigInfoMapperSelectOneReturnsNull() {
        // Setup
        final ModelDto dto = new ModelDto();
        dto.setType(0);
        dto.setFilter(0);
        dto.setName("name");
        dto.setPage(0);
        dto.setPageSize(0);

        final MockHttpServletRequest request = new MockHttpServletRequest();

        // Configure ConfigInfoMapper.getByCategoryAndCode(...).
        final ConfigInfo configInfo = new ConfigInfo();
        configInfo.setId(0L);
        configInfo.setCategory("category");
        configInfo.setCode("code");
        configInfo.setName("name");
        configInfo.setValue("value");
        when(mockConfigInfoMapper.getByCategoryAndCode("LLM_FILTER", "plan")).thenReturn(configInfo);

        when(mockConfigInfoMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(null);

        // Configure ModelMapper.selectList(...).
        final Model model = new Model();
        model.setId(0L);
        model.setName("name");
        model.setDesc("desc");
        model.setSource(0);
        model.setUid("uid");
        final List<Model> models = List.of(model);
        when(mockMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(models);

        when(mockS3UtilClient.getS3Prefix()).thenReturn("address");

        // Configure ModelCategoryService.getTree(...).
        final CategoryTreeVO categoryTreeVO = new CategoryTreeVO();
        categoryTreeVO.setId(0L);
        categoryTreeVO.setKey("key");
        categoryTreeVO.setName("name");
        categoryTreeVO.setSortOrder(0);
        categoryTreeVO.setChildren(List.of(new CategoryTreeVO()));
        final List<CategoryTreeVO> categoryTreeVOS = List.of(categoryTreeVO);
        when(mockModelCategoryService.getTree(0L)).thenReturn(categoryTreeVOS);

        // Configure ConfigInfoMapper.getListByCategory(...).
        final ConfigInfo configInfo1 = new ConfigInfo();
        configInfo1.setId(0L);
        configInfo1.setCategory("category");
        configInfo1.setCode("code");
        configInfo1.setName("name");
        configInfo1.setValue("value");
        final List<ConfigInfo> configInfos = List.of(configInfo1);
        when(mockConfigInfoMapper.getListByCategory("SPECIAL_MODEL")).thenReturn(configInfos);

        // Run the test
        final ApiResult result = modelServiceUnderTest.getConditionList(dto, request);

        // Verify the results
        // Confirm LLMService.getDataFromModelShelfList(...).
        final LLMInfoVo llmInfoVo = new LLMInfoVo();
        llmInfoVo.setLlmSource(0);
        llmInfoVo.setLlmId(0L);
        llmInfoVo.setStatus(0);
        llmInfoVo.setInfo("info");
        llmInfoVo.setIcon("icon");
        final List<LLMInfoVo> sceneSquareList = List.of(llmInfoVo);
        verify(mockLlmService).getDataFromModelShelfList(sceneSquareList, List.of("value"), "uid", "name");
    }

    @Test
    void testGetConditionList_ModelMapperReturnsNoItems() {
        // Setup
        final ModelDto dto = new ModelDto();
        dto.setType(0);
        dto.setFilter(0);
        dto.setName("name");
        dto.setPage(0);
        dto.setPageSize(0);

        final MockHttpServletRequest request = new MockHttpServletRequest();

        // Configure ConfigInfoMapper.getByCategoryAndCode(...).
        final ConfigInfo configInfo = new ConfigInfo();
        configInfo.setId(0L);
        configInfo.setCategory("category");
        configInfo.setCode("code");
        configInfo.setName("name");
        configInfo.setValue("value");
        when(mockConfigInfoMapper.getByCategoryAndCode("LLM_FILTER", "plan")).thenReturn(configInfo);

        // Configure ConfigInfoMapper.selectOne(...).
        final ConfigInfo configInfo1 = new ConfigInfo();
        configInfo1.setId(0L);
        configInfo1.setCategory("category");
        configInfo1.setCode("code");
        configInfo1.setName("name");
        configInfo1.setValue("value");
        when(mockConfigInfoMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(configInfo1);

        when(mockMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(Collections.emptyList());

        // Configure ConfigInfoMapper.getListByCategory(...).
        final ConfigInfo configInfo2 = new ConfigInfo();
        configInfo2.setId(0L);
        configInfo2.setCategory("category");
        configInfo2.setCode("code");
        configInfo2.setName("name");
        configInfo2.setValue("value");
        final List<ConfigInfo> configInfos = List.of(configInfo2);
        when(mockConfigInfoMapper.getListByCategory("SPECIAL_MODEL")).thenReturn(configInfos);

        // Run the test
        final ApiResult result = modelServiceUnderTest.getConditionList(dto, request);

        // Verify the results
        // Confirm LLMService.getDataFromModelShelfList(...).
        final LLMInfoVo llmInfoVo = new LLMInfoVo();
        llmInfoVo.setLlmSource(0);
        llmInfoVo.setLlmId(0L);
        llmInfoVo.setStatus(0);
        llmInfoVo.setInfo("info");
        llmInfoVo.setIcon("icon");
        final List<LLMInfoVo> sceneSquareList = List.of(llmInfoVo);
        verify(mockLlmService).getDataFromModelShelfList(sceneSquareList, List.of("value"), "uid", "name");
    }

    @Test
    void testGetConditionList_ModelCategoryServiceReturnsNoItems() {
        // Setup
        final ModelDto dto = new ModelDto();
        dto.setType(0);
        dto.setFilter(0);
        dto.setName("name");
        dto.setPage(0);
        dto.setPageSize(0);

        final MockHttpServletRequest request = new MockHttpServletRequest();

        // Configure ConfigInfoMapper.getByCategoryAndCode(...).
        final ConfigInfo configInfo = new ConfigInfo();
        configInfo.setId(0L);
        configInfo.setCategory("category");
        configInfo.setCode("code");
        configInfo.setName("name");
        configInfo.setValue("value");
        when(mockConfigInfoMapper.getByCategoryAndCode("LLM_FILTER", "plan")).thenReturn(configInfo);

        // Configure ConfigInfoMapper.selectOne(...).
        final ConfigInfo configInfo1 = new ConfigInfo();
        configInfo1.setId(0L);
        configInfo1.setCategory("category");
        configInfo1.setCode("code");
        configInfo1.setName("name");
        configInfo1.setValue("value");
        when(mockConfigInfoMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(configInfo1);

        // Configure ModelMapper.selectList(...).
        final Model model = new Model();
        model.setId(0L);
        model.setName("name");
        model.setDesc("desc");
        model.setSource(0);
        model.setUid("uid");
        final List<Model> models = List.of(model);
        when(mockMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(models);

        when(mockS3UtilClient.getS3Prefix()).thenReturn("address");
        when(mockModelCategoryService.getTree(0L)).thenReturn(Collections.emptyList());

        // Configure ConfigInfoMapper.getListByCategory(...).
        final ConfigInfo configInfo2 = new ConfigInfo();
        configInfo2.setId(0L);
        configInfo2.setCategory("category");
        configInfo2.setCode("code");
        configInfo2.setName("name");
        configInfo2.setValue("value");
        final List<ConfigInfo> configInfos = List.of(configInfo2);
        when(mockConfigInfoMapper.getListByCategory("SPECIAL_MODEL")).thenReturn(configInfos);

        // Run the test
        final ApiResult result = modelServiceUnderTest.getConditionList(dto, request);

        // Verify the results
        // Confirm LLMService.getDataFromModelShelfList(...).
        final LLMInfoVo llmInfoVo = new LLMInfoVo();
        llmInfoVo.setLlmSource(0);
        llmInfoVo.setLlmId(0L);
        llmInfoVo.setStatus(0);
        llmInfoVo.setInfo("info");
        llmInfoVo.setIcon("icon");
        final List<LLMInfoVo> sceneSquareList = List.of(llmInfoVo);
        verify(mockLlmService).getDataFromModelShelfList(sceneSquareList, List.of("value"), "uid", "name");
    }

    @Test
    void testGetConditionList_ConfigInfoMapperGetListByCategoryReturnsNoItems() {
        // Setup
        final ModelDto dto = new ModelDto();
        dto.setType(0);
        dto.setFilter(0);
        dto.setName("name");
        dto.setPage(0);
        dto.setPageSize(0);

        final MockHttpServletRequest request = new MockHttpServletRequest();

        // Configure ConfigInfoMapper.getByCategoryAndCode(...).
        final ConfigInfo configInfo = new ConfigInfo();
        configInfo.setId(0L);
        configInfo.setCategory("category");
        configInfo.setCode("code");
        configInfo.setName("name");
        configInfo.setValue("value");
        when(mockConfigInfoMapper.getByCategoryAndCode("LLM_FILTER", "plan")).thenReturn(configInfo);

        // Configure ConfigInfoMapper.selectOne(...).
        final ConfigInfo configInfo1 = new ConfigInfo();
        configInfo1.setId(0L);
        configInfo1.setCategory("category");
        configInfo1.setCode("code");
        configInfo1.setName("name");
        configInfo1.setValue("value");
        when(mockConfigInfoMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(configInfo1);

        // Configure ModelMapper.selectList(...).
        final Model model = new Model();
        model.setId(0L);
        model.setName("name");
        model.setDesc("desc");
        model.setSource(0);
        model.setUid("uid");
        final List<Model> models = List.of(model);
        when(mockMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(models);

        when(mockS3UtilClient.getS3Prefix()).thenReturn("address");

        // Configure ModelCategoryService.getTree(...).
        final CategoryTreeVO categoryTreeVO = new CategoryTreeVO();
        categoryTreeVO.setId(0L);
        categoryTreeVO.setKey("key");
        categoryTreeVO.setName("name");
        categoryTreeVO.setSortOrder(0);
        categoryTreeVO.setChildren(List.of(new CategoryTreeVO()));
        final List<CategoryTreeVO> categoryTreeVOS = List.of(categoryTreeVO);
        when(mockModelCategoryService.getTree(0L)).thenReturn(categoryTreeVOS);

        when(mockConfigInfoMapper.getListByCategory("SPECIAL_MODEL")).thenReturn(Collections.emptyList());

        // Run the test
        final ApiResult result = modelServiceUnderTest.getConditionList(dto, request);

        // Verify the results
        // Confirm LLMService.getDataFromModelShelfList(...).
        final LLMInfoVo llmInfoVo = new LLMInfoVo();
        llmInfoVo.setLlmSource(0);
        llmInfoVo.setLlmId(0L);
        llmInfoVo.setStatus(0);
        llmInfoVo.setInfo("info");
        llmInfoVo.setIcon("icon");
        final List<LLMInfoVo> sceneSquareList = List.of(llmInfoVo);
        verify(mockLlmService).getDataFromModelShelfList(sceneSquareList, List.of("value"), "uid", "name");
    }

    @Test
    void testGenerate9DigitRandomFromId() {
        assertEquals(0L, ModelService.generate9DigitRandomFromId(0L));
    }

    @Test
    void testEncodeId() {
        assertEquals(0L, ModelService.encodeId(3241414L));
    }

    @Test
    void testDecodeId() {
        assertEquals(0L, ModelService.decodeId(0L));
    }

    @Test
    void testGetDetail() {
        // Setup
        final MockHttpServletRequest request = new MockHttpServletRequest();

        // Configure ModelCommonService.getById(...).
        final ModelCommon modelCommon = new ModelCommon();
        modelCommon.setId(0L);
        modelCommon.setName("name");
        modelCommon.setDesc("desc");
        modelCommon.setIntro("intro");
        modelCommon.setUserName("userName");
        when(mockModelCommonService.getById(0L)).thenReturn(modelCommon);

        // Configure ModelMapper.selectOne(...).
        final Model model = new Model();
        model.setId(0L);
        model.setName("name");
        model.setDesc("desc");
        model.setSource(0);
        model.setUid("uid");
        when(mockMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(model);

        when(mockS3UtilClient.getS3Prefix()).thenReturn("address");

        // Configure ModelCategoryService.getTree(...).
        final CategoryTreeVO categoryTreeVO = new CategoryTreeVO();
        categoryTreeVO.setId(0L);
        categoryTreeVO.setKey("key");
        categoryTreeVO.setName("name");
        categoryTreeVO.setSortOrder(0);
        categoryTreeVO.setChildren(List.of(new CategoryTreeVO()));
        final List<CategoryTreeVO> categoryTreeVOS = List.of(categoryTreeVO);
        when(mockModelCategoryService.getTree(0L)).thenReturn(categoryTreeVOS);

        // Run the test
        final ApiResult result = modelServiceUnderTest.getDetail(0, 0L, request);

        // Verify the results
    }

    @Test
    void testGetDetail_ModelCommonServiceReturnsNull() {
        // Setup
        final MockHttpServletRequest request = new MockHttpServletRequest();
        when(mockModelCommonService.getById(0L)).thenReturn(null);

        // Run the test
        assertThrows(BusinessException.class, () -> modelServiceUnderTest.getDetail(0, 0L, request));
    }

    @Test
    void testGetDetail_ModelCategoryServiceReturnsNoItems() {
        // Setup
        final MockHttpServletRequest request = new MockHttpServletRequest();

        // Configure ModelMapper.selectOne(...).
        final Model model = new Model();
        model.setId(0L);
        model.setName("name");
        model.setDesc("desc");
        model.setSource(0);
        model.setUid("uid");
        when(mockMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(model);

        when(mockS3UtilClient.getS3Prefix()).thenReturn("address");
        when(mockModelCategoryService.getTree(0L)).thenReturn(Collections.emptyList());

        // Run the test
        final ApiResult result = modelServiceUnderTest.getDetail(0, 0L, request);

        // Verify the results
    }

    @Test
    void testGetPublicKey() {
        // Setup
        // Configure ConfigInfoMapper.selectOne(...).
        final ConfigInfo configInfo = new ConfigInfo();
        configInfo.setId(0L);
        configInfo.setCategory("category");
        configInfo.setCode("code");
        configInfo.setName("name");
        configInfo.setValue("value");
        when(mockConfigInfoMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(configInfo);

        // Run the test
        final String result = modelServiceUnderTest.getPublicKey();

        // Verify the results
        assertEquals("result", result);
    }

    @Test
    void testGetPublicKey_ConfigInfoMapperReturnsNull() {
        // Setup
        when(mockConfigInfoMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(null);

        // Run the test
        final String result = modelServiceUnderTest.getPublicKey();

        // Verify the results
        assertNull(result);
    }

    @Test
    void testCheckAndDelete_ThrowsBusinessException() {
        // Setup
        final MockHttpServletRequest request = new MockHttpServletRequest();

        // Configure WorkflowMapper.selectList(...).
        final Workflow workflow = new Workflow();
        workflow.setId(0L);
        workflow.setAppId("appId");
        workflow.setFlowId("flowId");
        workflow.setName("name");
        workflow.setData("data");
        final List<Workflow> workflows = List.of(workflow);
        when(mockWorkflowMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(workflows);

        // Configure ConfigInfoMapper.getByCategoryAndCode(...).
        final ConfigInfo configInfo = new ConfigInfo();
        configInfo.setId(0L);
        configInfo.setCategory("category");
        configInfo.setCode("code");
        configInfo.setName("name");
        configInfo.setValue("value");
        when(mockConfigInfoMapper.getByCategoryAndCode("LLM_WORKFLOW_FILTER", "self-model")).thenReturn(configInfo);

        when(mockSparkBotMapper.checkDomainIsUsage("uid", "domain")).thenReturn(0);

        // Run the test
        assertThrows(BusinessException.class, () -> modelServiceUnderTest.checkAndDelete(0L, request));
    }

    @Test
    void testCheckAndDelete_WorkflowMapperReturnsNoItems() {
        // Setup
        final MockHttpServletRequest request = new MockHttpServletRequest();
        when(mockWorkflowMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(Collections.emptyList());

        // Configure ConfigInfoMapper.getByCategoryAndCode(...).
        final ConfigInfo configInfo = new ConfigInfo();
        configInfo.setId(0L);
        configInfo.setCategory("category");
        configInfo.setCode("code");
        configInfo.setName("name");
        configInfo.setValue("value");
        when(mockConfigInfoMapper.getByCategoryAndCode("LLM_WORKFLOW_FILTER", "self-model")).thenReturn(configInfo);

        when(mockSparkBotMapper.checkDomainIsUsage("uid", "domain")).thenReturn(0);

        // Run the test
        assertThrows(BusinessException.class, () -> modelServiceUnderTest.checkAndDelete(0L, request));
    }

    @Test
    void testCheckAndDelete_ConfigInfoMapperReturnsNull() {
        // Setup
        final MockHttpServletRequest request = new MockHttpServletRequest();

        // Configure WorkflowMapper.selectList(...).
        final Workflow workflow = new Workflow();
        workflow.setId(0L);
        workflow.setAppId("appId");
        workflow.setFlowId("flowId");
        workflow.setName("name");
        workflow.setData("data");
        final List<Workflow> workflows = List.of(workflow);
        when(mockWorkflowMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(workflows);

        when(mockConfigInfoMapper.getByCategoryAndCode("LLM_WORKFLOW_FILTER", "self-model")).thenReturn(null);
        when(mockSparkBotMapper.checkDomainIsUsage("uid", "domain")).thenReturn(0);

        // Run the test
        assertThrows(BusinessException.class, () -> modelServiceUnderTest.checkAndDelete(0L, request));
    }

    @Test
    void testCheckAndDelete_SparkBotMapperReturnsNull() {
        // Setup
        final MockHttpServletRequest request = new MockHttpServletRequest();

        // Configure WorkflowMapper.selectList(...).
        final Workflow workflow = new Workflow();
        workflow.setId(0L);
        workflow.setAppId("appId");
        workflow.setFlowId("flowId");
        workflow.setName("name");
        workflow.setData("data");
        final List<Workflow> workflows = List.of(workflow);
        when(mockWorkflowMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(workflows);

        // Configure ConfigInfoMapper.getByCategoryAndCode(...).
        final ConfigInfo configInfo = new ConfigInfo();
        configInfo.setId(0L);
        configInfo.setCategory("category");
        configInfo.setCode("code");
        configInfo.setName("name");
        configInfo.setValue("value");
        when(mockConfigInfoMapper.getByCategoryAndCode("LLM_WORKFLOW_FILTER", "self-model")).thenReturn(configInfo);

        when(mockSparkBotMapper.checkDomainIsUsage("uid", "domain")).thenReturn(null);
        when(mockModelHandler.deleteModel("serviceId")).thenReturn(false);

        // Run the test
        final ApiResult result = modelServiceUnderTest.checkAndDelete(0L, request);

        // Verify the results
    }

    @Test
    void testCheckModelBase() {
        // Setup
        // Configure ConfigInfoMapper.selectOne(...).
        final ConfigInfo configInfo = new ConfigInfo();
        configInfo.setId(0L);
        configInfo.setCategory("category");
        configInfo.setCode("code");
        configInfo.setName("name");
        configInfo.setValue("value");
        when(mockConfigInfoMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(configInfo);

        // Configure ModelMapper.selectList(...).
        final Model model = new Model();
        model.setId(0L);
        model.setName("name");
        model.setDesc("desc");
        model.setSource(0);
        model.setUid("uid");
        final List<Model> models = List.of(model);
        when(mockMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(models);

        when(mockS3UtilClient.getS3Prefix()).thenReturn("address");

        // Configure ModelCategoryService.getTree(...).
        final CategoryTreeVO categoryTreeVO = new CategoryTreeVO();
        categoryTreeVO.setId(0L);
        categoryTreeVO.setKey("key");
        categoryTreeVO.setName("name");
        categoryTreeVO.setSortOrder(0);
        categoryTreeVO.setChildren(List.of(new CategoryTreeVO()));
        final List<CategoryTreeVO> categoryTreeVOS = List.of(categoryTreeVO);
        when(mockModelCategoryService.getTree(0L)).thenReturn(categoryTreeVOS);

        // Run the test
        final Boolean result = modelServiceUnderTest.checkModelBase(0L, "serviceId", "url", "uid", 0L);

        // Verify the results
        assertFalse(result);

        // Confirm LLMService.getDataFromModelShelfList(...).
        final LLMInfoVo llmInfoVo = new LLMInfoVo();
        llmInfoVo.setLlmSource(0);
        llmInfoVo.setLlmId(0L);
        llmInfoVo.setStatus(0);
        llmInfoVo.setInfo("info");
        llmInfoVo.setIcon("icon");
        final List<LLMInfoVo> sceneSquareList = List.of(llmInfoVo);
        verify(mockLlmService).getDataFromModelShelfList(sceneSquareList, List.of("value"), "uid", "name");
    }

    @Test
    void testCheckModelBase_ConfigInfoMapperReturnsNull() {
        // Setup
        when(mockConfigInfoMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(null);

        // Configure ModelMapper.selectList(...).
        final Model model = new Model();
        model.setId(0L);
        model.setName("name");
        model.setDesc("desc");
        model.setSource(0);
        model.setUid("uid");
        final List<Model> models = List.of(model);
        when(mockMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(models);

        when(mockS3UtilClient.getS3Prefix()).thenReturn("address");

        // Configure ModelCategoryService.getTree(...).
        final CategoryTreeVO categoryTreeVO = new CategoryTreeVO();
        categoryTreeVO.setId(0L);
        categoryTreeVO.setKey("key");
        categoryTreeVO.setName("name");
        categoryTreeVO.setSortOrder(0);
        categoryTreeVO.setChildren(List.of(new CategoryTreeVO()));
        final List<CategoryTreeVO> categoryTreeVOS = List.of(categoryTreeVO);
        when(mockModelCategoryService.getTree(0L)).thenReturn(categoryTreeVOS);

        // Run the test
        final Boolean result = modelServiceUnderTest.checkModelBase(0L, "serviceId", "url", "uid", 0L);

        // Verify the results
        assertFalse(result);

        // Confirm LLMService.getDataFromModelShelfList(...).
        final LLMInfoVo llmInfoVo = new LLMInfoVo();
        llmInfoVo.setLlmSource(0);
        llmInfoVo.setLlmId(0L);
        llmInfoVo.setStatus(0);
        llmInfoVo.setInfo("info");
        llmInfoVo.setIcon("icon");
        final List<LLMInfoVo> sceneSquareList = List.of(llmInfoVo);
        verify(mockLlmService).getDataFromModelShelfList(sceneSquareList, List.of("value"), "uid", "name");
    }

    @Test
    void testCheckModelBase_ModelMapperReturnsNoItems() {
        // Setup
        // Configure ConfigInfoMapper.selectOne(...).
        final ConfigInfo configInfo = new ConfigInfo();
        configInfo.setId(0L);
        configInfo.setCategory("category");
        configInfo.setCode("code");
        configInfo.setName("name");
        configInfo.setValue("value");
        when(mockConfigInfoMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(configInfo);

        when(mockMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(Collections.emptyList());

        // Run the test
        final Boolean result = modelServiceUnderTest.checkModelBase(0L, "serviceId", "url", "uid", 0L);

        // Verify the results
        assertFalse(result);

        // Confirm LLMService.getDataFromModelShelfList(...).
        final LLMInfoVo llmInfoVo = new LLMInfoVo();
        llmInfoVo.setLlmSource(0);
        llmInfoVo.setLlmId(0L);
        llmInfoVo.setStatus(0);
        llmInfoVo.setInfo("info");
        llmInfoVo.setIcon("icon");
        final List<LLMInfoVo> sceneSquareList = List.of(llmInfoVo);
        verify(mockLlmService).getDataFromModelShelfList(sceneSquareList, List.of("value"), "uid", "name");
    }

    @Test
    void testCheckModelBase_ModelCategoryServiceReturnsNoItems() {
        // Setup
        // Configure ConfigInfoMapper.selectOne(...).
        final ConfigInfo configInfo = new ConfigInfo();
        configInfo.setId(0L);
        configInfo.setCategory("category");
        configInfo.setCode("code");
        configInfo.setName("name");
        configInfo.setValue("value");
        when(mockConfigInfoMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(configInfo);

        // Configure ModelMapper.selectList(...).
        final Model model = new Model();
        model.setId(0L);
        model.setName("name");
        model.setDesc("desc");
        model.setSource(0);
        model.setUid("uid");
        final List<Model> models = List.of(model);
        when(mockMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(models);

        when(mockS3UtilClient.getS3Prefix()).thenReturn("address");
        when(mockModelCategoryService.getTree(0L)).thenReturn(Collections.emptyList());

        // Run the test
        final Boolean result = modelServiceUnderTest.checkModelBase(0L, "serviceId", "url", "uid", 0L);

        // Verify the results
        assertFalse(result);

        // Confirm LLMService.getDataFromModelShelfList(...).
        final LLMInfoVo llmInfoVo = new LLMInfoVo();
        llmInfoVo.setLlmSource(0);
        llmInfoVo.setLlmId(0L);
        llmInfoVo.setStatus(0);
        llmInfoVo.setInfo("info");
        llmInfoVo.setIcon("icon");
        final List<LLMInfoVo> sceneSquareList = List.of(llmInfoVo);
        verify(mockLlmService).getDataFromModelShelfList(sceneSquareList, List.of("value"), "uid", "name");
    }

    @Test
    void testGetAllCategoryTree() {
        // Setup
        final CategoryTreeVO categoryTreeVO = new CategoryTreeVO();
        categoryTreeVO.setId(0L);
        categoryTreeVO.setKey("key");
        categoryTreeVO.setName("name");
        categoryTreeVO.setSortOrder(0);
        categoryTreeVO.setChildren(List.of(new CategoryTreeVO()));
        final List<CategoryTreeVO> expectedResult = List.of(categoryTreeVO);

        // Configure ModelCategoryService.getAllCategoryTree(...).
        final CategoryTreeVO categoryTreeVO1 = new CategoryTreeVO();
        categoryTreeVO1.setId(0L);
        categoryTreeVO1.setKey("key");
        categoryTreeVO1.setName("name");
        categoryTreeVO1.setSortOrder(0);
        categoryTreeVO1.setChildren(List.of(new CategoryTreeVO()));
        final List<CategoryTreeVO> categoryTreeVOS = List.of(categoryTreeVO1);
        when(mockModelCategoryService.getAllCategoryTree()).thenReturn(categoryTreeVOS);

        // Run the test
        final List<CategoryTreeVO> result = modelServiceUnderTest.getAllCategoryTree();

        // Verify the results
        assertEquals(expectedResult, result);
    }

    @Test
    void testGetAllCategoryTree_ModelCategoryServiceReturnsNoItems() {
        // Setup
        when(mockModelCategoryService.getAllCategoryTree()).thenReturn(Collections.emptyList());

        // Run the test
        final List<CategoryTreeVO> result = modelServiceUnderTest.getAllCategoryTree();

        // Verify the results
        assertEquals(Collections.emptyList(), result);
    }

    @Test
    void testGetList() {
        // Setup
        final ModelDto dto = new ModelDto();
        dto.setType(0);
        dto.setFilter(0);
        dto.setName("name");
        dto.setPage(0);
        dto.setPageSize(0);

        final MockHttpServletRequest request = new MockHttpServletRequest();

        // Configure ConfigInfoMapper.selectOne(...).
        final ConfigInfo configInfo = new ConfigInfo();
        configInfo.setId(0L);
        configInfo.setCategory("category");
        configInfo.setCode("code");
        configInfo.setName("name");
        configInfo.setValue("value");
        when(mockConfigInfoMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(configInfo);

        // Configure ModelMapper.selectList(...).
        final Model model = new Model();
        model.setId(0L);
        model.setName("name");
        model.setDesc("desc");
        model.setSource(0);
        model.setUid("uid");
        final List<Model> models = List.of(model);
        when(mockMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(models);

        when(mockS3UtilClient.getS3Prefix()).thenReturn("address");

        // Configure ModelCategoryService.getTree(...).
        final CategoryTreeVO categoryTreeVO = new CategoryTreeVO();
        categoryTreeVO.setId(0L);
        categoryTreeVO.setKey("key");
        categoryTreeVO.setName("name");
        categoryTreeVO.setSortOrder(0);
        categoryTreeVO.setChildren(List.of(new CategoryTreeVO()));
        final List<CategoryTreeVO> categoryTreeVOS = List.of(categoryTreeVO);
        when(mockModelCategoryService.getTree(0L)).thenReturn(categoryTreeVOS);

        // Run the test
        final ApiResult<Page<LLMInfoVo>> result = modelServiceUnderTest.getList(dto, request);

        // Verify the results
        // Confirm LLMService.getDataFromModelShelfList(...).
        final LLMInfoVo llmInfoVo = new LLMInfoVo();
        llmInfoVo.setLlmSource(0);
        llmInfoVo.setLlmId(0L);
        llmInfoVo.setStatus(0);
        llmInfoVo.setInfo("info");
        llmInfoVo.setIcon("icon");
        final List<LLMInfoVo> sceneSquareList = List.of(llmInfoVo);
        verify(mockLlmService).getDataFromModelShelfList(sceneSquareList, List.of("value"), "uid", "name");
    }

    @Test
    void testGetList_ConfigInfoMapperReturnsNull() {
        // Setup
        final ModelDto dto = new ModelDto();
        dto.setType(0);
        dto.setFilter(0);
        dto.setName("name");
        dto.setPage(0);
        dto.setPageSize(0);

        final MockHttpServletRequest request = new MockHttpServletRequest();
        when(mockConfigInfoMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(null);

        // Configure ModelMapper.selectList(...).
        final Model model = new Model();
        model.setId(0L);
        model.setName("name");
        model.setDesc("desc");
        model.setSource(0);
        model.setUid("uid");
        final List<Model> models = List.of(model);
        when(mockMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(models);

        when(mockS3UtilClient.getS3Prefix()).thenReturn("address");

        // Configure ModelCategoryService.getTree(...).
        final CategoryTreeVO categoryTreeVO = new CategoryTreeVO();
        categoryTreeVO.setId(0L);
        categoryTreeVO.setKey("key");
        categoryTreeVO.setName("name");
        categoryTreeVO.setSortOrder(0);
        categoryTreeVO.setChildren(List.of(new CategoryTreeVO()));
        final List<CategoryTreeVO> categoryTreeVOS = List.of(categoryTreeVO);
        when(mockModelCategoryService.getTree(0L)).thenReturn(categoryTreeVOS);

        // Run the test
        final ApiResult<Page<LLMInfoVo>> result = modelServiceUnderTest.getList(dto, request);

        // Verify the results
        // Confirm LLMService.getDataFromModelShelfList(...).
        final LLMInfoVo llmInfoVo = new LLMInfoVo();
        llmInfoVo.setLlmSource(0);
        llmInfoVo.setLlmId(0L);
        llmInfoVo.setStatus(0);
        llmInfoVo.setInfo("info");
        llmInfoVo.setIcon("icon");
        final List<LLMInfoVo> sceneSquareList = List.of(llmInfoVo);
        verify(mockLlmService).getDataFromModelShelfList(sceneSquareList, List.of("value"), "uid", "name");
    }

    @Test
    void testGetList_ModelMapperReturnsNoItems() {
        // Setup
        final ModelDto dto = new ModelDto();
        dto.setType(0);
        dto.setFilter(0);
        dto.setName("name");
        dto.setPage(0);
        dto.setPageSize(0);

        final MockHttpServletRequest request = new MockHttpServletRequest();

        // Configure ConfigInfoMapper.selectOne(...).
        final ConfigInfo configInfo = new ConfigInfo();
        configInfo.setId(0L);
        configInfo.setCategory("category");
        configInfo.setCode("code");
        configInfo.setName("name");
        configInfo.setValue("value");
        when(mockConfigInfoMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(configInfo);

        when(mockMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(Collections.emptyList());

        // Run the test
        final ApiResult<Page<LLMInfoVo>> result = modelServiceUnderTest.getList(dto, request);

        // Verify the results
        // Confirm LLMService.getDataFromModelShelfList(...).
        final LLMInfoVo llmInfoVo = new LLMInfoVo();
        llmInfoVo.setLlmSource(0);
        llmInfoVo.setLlmId(0L);
        llmInfoVo.setStatus(0);
        llmInfoVo.setInfo("info");
        llmInfoVo.setIcon("icon");
        final List<LLMInfoVo> sceneSquareList = List.of(llmInfoVo);
        verify(mockLlmService).getDataFromModelShelfList(sceneSquareList, List.of("value"), "uid", "name");
    }

    @Test
    void testGetList_ModelCategoryServiceReturnsNoItems() {
        // Setup
        final ModelDto dto = new ModelDto();
        dto.setType(0);
        dto.setFilter(0);
        dto.setName("name");
        dto.setPage(0);
        dto.setPageSize(0);

        final MockHttpServletRequest request = new MockHttpServletRequest();

        // Configure ConfigInfoMapper.selectOne(...).
        final ConfigInfo configInfo = new ConfigInfo();
        configInfo.setId(0L);
        configInfo.setCategory("category");
        configInfo.setCode("code");
        configInfo.setName("name");
        configInfo.setValue("value");
        when(mockConfigInfoMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(configInfo);

        // Configure ModelMapper.selectList(...).
        final Model model = new Model();
        model.setId(0L);
        model.setName("name");
        model.setDesc("desc");
        model.setSource(0);
        model.setUid("uid");
        final List<Model> models = List.of(model);
        when(mockMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(models);

        when(mockS3UtilClient.getS3Prefix()).thenReturn("address");
        when(mockModelCategoryService.getTree(0L)).thenReturn(Collections.emptyList());

        // Run the test
        final ApiResult<Page<LLMInfoVo>> result = modelServiceUnderTest.getList(dto, request);

        // Verify the results
        // Confirm LLMService.getDataFromModelShelfList(...).
        final LLMInfoVo llmInfoVo = new LLMInfoVo();
        llmInfoVo.setLlmSource(0);
        llmInfoVo.setLlmId(0L);
        llmInfoVo.setStatus(0);
        llmInfoVo.setInfo("info");
        llmInfoVo.setIcon("icon");
        final List<LLMInfoVo> sceneSquareList = List.of(llmInfoVo);
        verify(mockLlmService).getDataFromModelShelfList(sceneSquareList, List.of("value"), "uid", "name");
    }

    @Test
    void testSwitchModel() {
        // Setup
        final MockHttpServletRequest request = new MockHttpServletRequest();

        // Run the test
        final ApiResult result = modelServiceUnderTest.switchModel(0L, 0, "option", request);

        // Verify the results
        verify(mockLlmService).switchFinetuneModel(0L, false);
    }

    @Test
    void testOffShelfModel() {
        // Setup
        // Configure WorkflowMapper.selectList(...).
        final Workflow workflow = new Workflow();
        workflow.setId(0L);
        workflow.setAppId("appId");
        workflow.setFlowId("flowId");
        workflow.setName("name");
        workflow.setData("data");
        final List<Workflow> workflows = List.of(workflow);
        when(mockWorkflowMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(workflows);

        // Configure ConfigInfoMapper.getByCategoryAndCode(...).
        final ConfigInfo configInfo = new ConfigInfo();
        configInfo.setId(0L);
        configInfo.setCategory("category");
        configInfo.setCode("code");
        configInfo.setName("name");
        configInfo.setValue("value");
        when(mockConfigInfoMapper.getByCategoryAndCode("NODE_PREFIX_MODEL", "switch")).thenReturn(configInfo);

        // Run the test
        final Object result = modelServiceUnderTest.offShelfModel(0L, "flowId", "serviceId");

        // Verify the results
        // Confirm WorkflowMapper.updateById(...).
        final Workflow entity = new Workflow();
        entity.setId(0L);
        entity.setAppId("appId");
        entity.setFlowId("flowId");
        entity.setName("name");
        entity.setData("data");
        verify(mockWorkflowMapper).updateById(entity);
    }

    @Test
    void testOffShelfModel_WorkflowMapperSelectListReturnsNoItems() {
        // Setup
        when(mockWorkflowMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(Collections.emptyList());

        // Configure ConfigInfoMapper.getByCategoryAndCode(...).
        final ConfigInfo configInfo = new ConfigInfo();
        configInfo.setId(0L);
        configInfo.setCategory("category");
        configInfo.setCode("code");
        configInfo.setName("name");
        configInfo.setValue("value");
        when(mockConfigInfoMapper.getByCategoryAndCode("NODE_PREFIX_MODEL", "switch")).thenReturn(configInfo);

        // Run the test
        final Object result = modelServiceUnderTest.offShelfModel(0L, "flowId", "serviceId");

        // Verify the results
    }

    @Test
    void testLocalModel() {
        // Setup
        final LocalModelDto dto = new LocalModelDto();
        dto.setModelName("modelName");
        dto.setDomain("domain");
        dto.setDescription("description");
        dto.setIcon("icon");
        dto.setColor("color");

        // Configure ModelCategoryService.getById(...).
        final ModelCategory modelCategory = new ModelCategory();
        modelCategory.setId(0L);
        modelCategory.setPid(0L);
        modelCategory.setKey("key");
        modelCategory.setName("name");
        modelCategory.setIsDelete((byte) 0b0);
        when(mockModelCategoryService.getById(0L)).thenReturn(modelCategory);

        // Configure LocalModelHandler.deployModel(...).
        final ModelDeployVo deployVo = new ModelDeployVo();
        deployVo.setModelName("modelName");
        final ModelDeployVo.ResourceRequirements resourceRequirements = new ModelDeployVo.ResourceRequirements();
        resourceRequirements.setAcceleratorCount(0);
        deployVo.setResourceRequirements(resourceRequirements);
        deployVo.setReplicaCount(0);
        deployVo.setContextLength(0);
        when(mockModelHandler.deployModel(deployVo)).thenReturn("result");

        // Configure LocalModelHandler.deployModelUpdate(...).
        final ModelDeployVo deployVo1 = new ModelDeployVo();
        deployVo1.setModelName("modelName");
        final ModelDeployVo.ResourceRequirements resourceRequirements1 = new ModelDeployVo.ResourceRequirements();
        resourceRequirements1.setAcceleratorCount(0);
        deployVo1.setResourceRequirements(resourceRequirements1);
        deployVo1.setReplicaCount(0);
        deployVo1.setContextLength(0);
        when(mockModelHandler.deployModelUpdate(deployVo1, "oldServiceId")).thenReturn("result");

        // Run the test
        final Object result = modelServiceUnderTest.localModel(dto);

        // Verify the results
        // Confirm ModelCategoryService.saveAll(...).
        final ModelCategoryReq req = new ModelCategoryReq();
        req.setModelId(0L);
        req.setCategorySystemIds(List.of(0L));
        final ModelCategoryReq.CustomItem categoryCustom = new ModelCategoryReq.CustomItem();
        categoryCustom.setPid(0L);
        categoryCustom.setCustomName("customName");
        req.setCategoryCustom(categoryCustom);
        verify(mockModelCategoryService).saveAll(req);
    }

    @Test
    void testLocalModel_ModelCategoryServiceGetByIdReturnsNull() {
        // Setup
        final LocalModelDto dto = new LocalModelDto();
        dto.setModelName("modelName");
        dto.setDomain("domain");
        dto.setDescription("description");
        dto.setIcon("icon");
        dto.setColor("color");

        when(mockModelCategoryService.getById(0L)).thenReturn(null);

        // Configure LocalModelHandler.deployModel(...).
        final ModelDeployVo deployVo = new ModelDeployVo();
        deployVo.setModelName("modelName");
        final ModelDeployVo.ResourceRequirements resourceRequirements = new ModelDeployVo.ResourceRequirements();
        resourceRequirements.setAcceleratorCount(0);
        deployVo.setResourceRequirements(resourceRequirements);
        deployVo.setReplicaCount(0);
        deployVo.setContextLength(0);
        when(mockModelHandler.deployModel(deployVo)).thenReturn("result");

        // Configure LocalModelHandler.deployModelUpdate(...).
        final ModelDeployVo deployVo1 = new ModelDeployVo();
        deployVo1.setModelName("modelName");
        final ModelDeployVo.ResourceRequirements resourceRequirements1 = new ModelDeployVo.ResourceRequirements();
        resourceRequirements1.setAcceleratorCount(0);
        deployVo1.setResourceRequirements(resourceRequirements1);
        deployVo1.setReplicaCount(0);
        deployVo1.setContextLength(0);
        when(mockModelHandler.deployModelUpdate(deployVo1, "oldServiceId")).thenReturn("result");

        // Run the test
        final Object result = modelServiceUnderTest.localModel(dto);

        // Verify the results
        // Confirm ModelCategoryService.saveAll(...).
        final ModelCategoryReq req = new ModelCategoryReq();
        req.setModelId(0L);
        req.setCategorySystemIds(List.of(0L));
        final ModelCategoryReq.CustomItem categoryCustom = new ModelCategoryReq.CustomItem();
        categoryCustom.setPid(0L);
        categoryCustom.setCustomName("customName");
        req.setCategoryCustom(categoryCustom);
        verify(mockModelCategoryService).saveAll(req);
    }

    @Test
    void testLocalModelList() {
        // Setup
        // Configure LocalModelHandler.getLocalModelList(...).
        final ModelFileVo modelFileVo = new ModelFileVo();
        modelFileVo.setModelName("modelName");
        modelFileVo.setModelPath("modelPath");
        final List<ModelFileVo> modelFileVos = List.of(modelFileVo);
        when(mockModelHandler.getLocalModelList()).thenReturn(modelFileVos);

        // Run the test
        final Object result = modelServiceUnderTest.localModelList();

        // Verify the results
    }

    @Test
    void testLocalModelList_LocalModelHandlerReturnsNoItems() {
        // Setup
        when(mockModelHandler.getLocalModelList()).thenReturn(Collections.emptyList());

        // Run the test
        final Object result = modelServiceUnderTest.localModelList();

        // Verify the results
    }

    @Test
    void testFlushStatus() {
        // Setup
        final Model model = new Model();
        model.setId(0L);
        model.setName("name");
        model.setDesc("desc");
        model.setSource(0);
        model.setUid("uid");

        when(mockModelHandler.checkDeployStatus("serviceId")).thenReturn(new JSONObject(0, 0.0f, false));
        modelServiceUnderTest.flushStatus(model);

    }

    @Test
    void testFlushStatusBatch() {
        // Setup
        final Model model = new Model();
        model.setId(0L);
        model.setName("name");
        model.setDesc("desc");
        model.setSource(0);
        model.setUid("uid");
        final List<Model> models = List.of(model);
        when(mockModelHandler.checkDeployStatus("serviceId")).thenReturn(new JSONObject(0, 0.0f, false));

        // Run the test
        final int result = modelServiceUnderTest.flushStatusBatch("uid", models);

        // Verify the results
        assertEquals(0, result);
    }
}
