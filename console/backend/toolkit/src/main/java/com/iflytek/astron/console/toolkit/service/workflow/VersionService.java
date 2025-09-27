package com.iflytek.astron.console.toolkit.service.workflow;

import com.alibaba.fastjson2.JSONObject;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.iflytek.astron.console.commons.constant.ResponseEnum;
import com.iflytek.astron.console.commons.entity.workflow.Workflow;
import com.iflytek.astron.console.commons.exception.BusinessException;
import com.iflytek.astron.console.commons.response.ApiResult;
import com.iflytek.astron.console.commons.util.space.SpaceInfoUtil;
import com.iflytek.astron.console.toolkit.entity.biz.workflow.BizWorkflowData;
import com.iflytek.astron.console.toolkit.entity.core.workflow.FlowProtocol;
import com.iflytek.astron.console.toolkit.entity.dto.WorkflowReq;
import com.iflytek.astron.console.toolkit.entity.table.workflow.WorkflowVersion;
import com.iflytek.astron.console.toolkit.mapper.workflow.WorkflowMapper;
import com.iflytek.astron.console.toolkit.mapper.workflow.WorkflowVersionMapper;
import com.iflytek.astron.console.toolkit.tool.DataPermissionCheckTool;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Version service for managing workflow versions. Handles workflow version creation, listing,
 * restoration, and lifecycle management. Provides version comparison and publishing capabilities.
 *
 * @author VersionService Team
 * @since 1.0.0
 */
@Service
@Slf4j
public class VersionService {

    @Autowired
    WorkflowService workflowService;

    @Autowired
    DataPermissionCheckTool dataPermissionCheckTool;

    @Autowired
    WorkflowMapper workflowMapper;

    @Autowired
    WorkflowVersionMapper workflowVersionMapper;


    @Value("${spring.profiles.active}")
    String env;

    private static final Random random = new Random();

    public Object listPage(Page<WorkflowVersion> page, String flowId) {
        Page<WorkflowVersion> newPage = new Page<>(page.getCurrent(), page.getSize());
        Workflow workflow = workflowMapper.selectOne(Wrappers.lambdaQuery(Workflow.class).eq(Workflow::getFlowId, flowId));
        if (workflow == null) {
            throw new BusinessException(ResponseEnum.WORKFLOW_NOT_EXIST);
        }
        dataPermissionCheckTool.checkWorkflowBelong(workflow, SpaceInfoUtil.getSpaceId());
        Page<WorkflowVersion> result = workflowVersionMapper.selectPageByCondition(newPage, flowId);
        return result;
    }

    public Object list_botId_Page(Page<WorkflowVersion> page, String botId) {
        Page<WorkflowVersion> newPage = new Page<>(page.getCurrent(), page.getSize());

        LambdaQueryWrapper<WorkflowVersion> wrapper = new QueryWrapper<WorkflowVersion>()
                .lambda()
                .eq(WorkflowVersion::getBotId, botId)
                .eq(WorkflowVersion::getDeleted, 1)
                .eq(WorkflowVersion::getPublishResult, "Success")
                .groupBy(WorkflowVersion::getName)
                .orderByDesc(WorkflowVersion::getCreatedTime);

        return workflowVersionMapper.selectPage(newPage, wrapper);
    }

    /**
     * Create workflow version with input parameters createDto: New parameters: String flowId flowId
     * String botId botId String name version name Long publishChannel workflow publish channel info
     * enum values 1: WeChat Official Account 2: Spark Desk 3: API 4: MCP String publishResult workflow
     * publish data 3 enum values: Success Failed Under Review String description workflow publish
     * description
     */
    // Exception database operation rollback
    @Transactional
    public ApiResult<JSONObject> create(WorkflowVersion createDto) {
        log.info("Starting to add version, input data: {}", createDto);
        Workflow workflow = workflowMapper.selectOne(Wrappers.lambdaQuery(Workflow.class).eq(Workflow::getFlowId, createDto.getFlowId()));
        if (workflow == null) {
            throw new BusinessException(ResponseEnum.WORKFLOW_NOT_EXIST);
        }
        dataPermissionCheckTool.checkWorkflowBelong(workflow, SpaceInfoUtil.getSpaceId());

        try {
            // Create workflow version
            WorkflowVersion workflowVersion = new WorkflowVersion();

            // Set version number
            String version_num = generateVersionNumber();

            // Set currently using this version
            updateIsVersionForFlowId(createDto.getFlowId());

            // Get core test protocol
            WorkflowReq workflowReq = new WorkflowReq();
            workflowReq.setId(workflow.getId());
            workflowReq.setName(workflow.getName());
            workflowReq.setDescription(workflow.getDescription());
            workflowReq.setData(JSONObject.parseObject(workflow.getData(), BizWorkflowData.class));
            FlowProtocol flowProtocol = workflowService.buildWorkflowData(workflowReq, createDto.getFlowId());

            // Data setting
            workflowVersion.setBotId(createDto.getBotId());
            workflowVersion.setVersionNum(version_num);
            workflowVersion.setName(createDto.getName());
            workflowVersion.setData(workflow.getData());
            workflowVersion.setSysData(JSONObject.toJSONString(flowProtocol));
            workflowVersion.setPublishChannel(createDto.getPublishChannel());
            workflowVersion.setPublishResult(createDto.getPublishResult());
            workflowVersion.setFlowId(createDto.getFlowId());
            workflowVersion.setDescription(createDto.getDescription());
            workflowVersionMapper.insert(workflowVersion);

            return ApiResult.success(new JSONObject()
                    .fluentPut("workflowVersionId", workflowVersion.getId())
                    .fluentPut("workflowVersionName", createDto.getName()));
        } catch (Exception e) {
            throw new BusinessException(ResponseEnum.WORKFLOW_VERSION_ADD_FAILED);
        }
        //
    }

    /**
     * Update isVersion flag for all versions of a specific flowId. Sets all versions' isVersion to 2
     * (inactive) for the given flowId.
     *
     * @param flowId Flow ID to update versions for
     */
    public void updateIsVersionForFlowId(String flowId) {
        // Build update conditions
        LambdaUpdateWrapper<WorkflowVersion> updateWrapper = new LambdaUpdateWrapper<>();
        updateWrapper.eq(WorkflowVersion::getFlowId, flowId)
                .eq(WorkflowVersion::getIsVersion, 1)
                .set(WorkflowVersion::getIsVersion, 2);
        // Execute update
        workflowVersionMapper.update(null, updateWrapper);
    }

    /**
     * Extract the numeric part from version string (e.g., "Version V1.0" -> 1.0)
     *
     * @param version Version string to extract number from
     * @return Numeric value of the version, or 0.0 if no valid number found
     */
    private static double extractVersionNumber(String version) {
        Pattern pattern = Pattern.compile("v(\\d+\\.?\\d*)");
        Matcher matcher = pattern.matcher(version);
        if (matcher.find()) {
            return Double.parseDouble(matcher.group(1));
        }
        return 0.0;
    }

    /**
     * Get version name for creating new version. Determines the appropriate version name based on
     * workflow changes.
     *
     * @param createDto Version creation parameters
     * @return API result with suggested version name
     */
    public ApiResult<JSONObject> getVersionName(WorkflowVersion createDto) {
        log.info("Starting to get workflow version name, input data: {}", createDto);
        Workflow workflow = workflowMapper.selectOne(Wrappers.lambdaQuery(Workflow.class).eq(Workflow::getFlowId, createDto.getFlowId()));
        if (workflow == null) {
            throw new BusinessException(ResponseEnum.WORKFLOW_NOT_EXIST);
        }
        dataPermissionCheckTool.checkWorkflowBelong(workflow, SpaceInfoUtil.getSpaceId());

        try {
            // Get the maximum version integer
            WorkflowVersion workflowVersion = workflowVersionMapper.selectOne(Wrappers.lambdaQuery(WorkflowVersion.class)
                    .eq(WorkflowVersion::getFlowId, createDto.getFlowId())
                    .orderByDesc(WorkflowVersion::getCreatedTime)
                    .isNotNull(WorkflowVersion::getSysData)
                    .last("limit 1"));
            if (workflowVersion == null) {
                return ApiResult.success(new JSONObject()
                        .fluentPut("workflowVersionName", "v1.0"));
            }
            String data = workflowVersion.getData();
            String maxName = workflowVersion.getName();

            String name;
            String workflow_data = workflow.getData();
            if (workflow_data != null && workflow_data.equals(data)) {
                name = incrementVersion(maxName, false);
            } else {
                name = incrementVersion(maxName, true);
            }
            return ApiResult.success(new JSONObject()
                    .fluentPut("workflowVersionName", name));
        } catch (Exception e) {
            throw new BusinessException(ResponseEnum.WORKFLOW_VERSION_GET_NAME_FAILED);
        }
    }


    /**
     * Get system data for a specific version.
     *
     * @param createDto Version query parameters
     * @return API result with system data
     */
    public ApiResult<JSONObject> getVersionSysData(WorkflowVersion createDto) {
        WorkflowVersion workflowVersion = workflowVersionMapper.selectOne(Wrappers.lambdaQuery(WorkflowVersion.class)
                .eq(WorkflowVersion::getBotId, createDto.getBotId())
                .eq(WorkflowVersion::getName, createDto.getName())
                .last("limit 1"));
        if (workflowVersion == null) {
            throw new BusinessException(ResponseEnum.WORKFLOW_VERSION_NOT_FOUND);
        }
        String sysData = workflowVersion.getSysData();
        return ApiResult.success(new JSONObject()
                .fluentPut("sysData", sysData));
    }

    /**
     * Check if version has system data.
     *
     * @param createDto Version check parameters
     * @return API result with availability flag
     */
    public ApiResult<JSONObject> haveVersionSysData(WorkflowVersion createDto) {
        List<WorkflowVersion> workflowVersions = workflowVersionMapper.selectList(Wrappers.lambdaQuery(WorkflowVersion.class)
                .eq(WorkflowVersion::getFlowId, createDto.getFlowId())
                .eq(WorkflowVersion::getName, createDto.getName()));
        if (workflowVersions.isEmpty()) {
            throw new BusinessException(ResponseEnum.WORKFLOW_VERSION_NOT_FOUND);
        }
        boolean haveSysData = workflowVersions.stream().noneMatch(wv -> "Success".equals(wv.getPublishResult()));
        return ApiResult.success(new JSONObject()
                .fluentPut("haveSysData", haveSysData));
    }

    /**
     * Increment version number based on type.
     *
     * @param maxVersion Current maximum version
     * @param type Whether to increment (true) or keep same (false)
     * @return New version string
     */
    public static String incrementVersion(String maxVersion, Boolean type) {
        if (maxVersion == null) {
            return "v1.0";
        }

        double maxVersionNumber = extractVersionNumber(maxVersion);

        // Extract numeric part and add 1
        String incrementedNumber;
        if (type) {
            incrementedNumber = String.valueOf(maxVersionNumber + 1);
        } else {
            incrementedNumber = String.valueOf(maxVersionNumber);
        }
        return "v" + incrementedNumber;
    }

    /**
     * Generate unique version number using timestamp and random number. Creates a 19-digit version
     * number by combining current timestamp with a 6-digit random number.
     *
     * @return Generated version number string with maximum length of 19 digits
     */
    public static String generateVersionNumber() {
        // Get current timestamp in milliseconds
        long timestamp = System.currentTimeMillis();
        // Generate a 6-digit random number
        int randomNumber = random.nextInt(900000) + 100000;
        // Combine timestamp and random number, ensure total length is 19 digits
        String versionNumber = String.valueOf(timestamp) + String.valueOf(randomNumber);
        // If length exceeds 19 digits, truncate
        if (versionNumber.length() > 19) {
            versionNumber = versionNumber.substring(0, 19);
        }
        return versionNumber;
    }

    /**
     * Restore a specific version as the current version. Updates workflow with the selected version's
     * data and marks it as active.
     *
     * @param createDto Restore version parameters
     * @return API result of restoration operation
     */
    @Transactional
    public ApiResult<JSONObject> restore(WorkflowVersion createDto) {
        log.info("Starting to restore version, input data: {}", createDto);
        Workflow workflow = workflowMapper.selectOne(Wrappers.lambdaQuery(Workflow.class).eq(Workflow::getFlowId, createDto.getFlowId()));
        if (workflow == null) {
            throw new BusinessException(ResponseEnum.WORKFLOW_NOT_EXIST);
        }
        dataPermissionCheckTool.checkWorkflowBelong(workflow, SpaceInfoUtil.getSpaceId());

        // Restore version functionality: 1: First update the version protocol to the workflow protocol 2:
        // Set all other versions' isVersion to 2 for flowId, then set the current passed version id to 1.
        try {
            // Get version protocol data
            WorkflowVersion workflowVersion = workflowVersionMapper.selectOne(Wrappers.lambdaQuery(WorkflowVersion.class).eq(WorkflowVersion::getId, createDto.getId()));
            String data = workflowVersion.getData();
            // Update workflow table protocol data
            updateFlowIdWorkflow(createDto.getFlowId(), data);

            LambdaUpdateWrapper<WorkflowVersion> updateWrapper1 = new LambdaUpdateWrapper<>();
            // Update flowId corresponding records, set isVersion to 2
            updateWrapper1.eq(WorkflowVersion::getFlowId, createDto.getFlowId())
                    .set(WorkflowVersion::getIsVersion, 2);
            // Execute update
            workflowVersionMapper.update(null, updateWrapper1);


            LambdaUpdateWrapper<WorkflowVersion> updateWrapper2 = new LambdaUpdateWrapper<>();
            // Update id corresponding records, set isVersion to 1
            updateWrapper2
                    .eq(WorkflowVersion::getId, createDto.getId())
                    .set(WorkflowVersion::getIsVersion, 1);
            // Execute update
            workflowVersionMapper.update(null, updateWrapper2);

            return ApiResult.success(new JSONObject());
        } catch (Exception e) {
            throw new BusinessException(ResponseEnum.WORKFLOW_VERSION_REDUCTION_FAILED);
        }
    }

    /**
     * Update workflow data for a specific flowId.
     *
     * @param flowId Flow ID to update
     * @param data New workflow data
     */
    public void updateFlowIdWorkflow(String flowId, String data) {
        // Build update conditions
        LambdaUpdateWrapper<Workflow> updateWrapper = new LambdaUpdateWrapper<>();
        updateWrapper.eq(Workflow::getFlowId, flowId)
                .set(Workflow::getData, data)
                .set(Workflow::getCanPublish, false);
        // Execute update
        workflowMapper.update(null, updateWrapper);
    }

    /**
     * Logical delete of a workflow version. Marks version as deleted rather than physical deletion.
     *
     * @param id Version ID to delete
     * @return Delete operation result
     */
    public Object logicDelete(Long id) {
        // Security validation
        WorkflowVersion workflowVersion = workflowVersionMapper.selectOne(Wrappers.lambdaQuery(WorkflowVersion.class).eq(WorkflowVersion::getId, id));
        String flowId = workflowVersion.getFlowId();
        Workflow workflow = workflowMapper.selectOne(Wrappers.lambdaQuery(Workflow.class).eq(Workflow::getFlowId, flowId));
        if (workflow == null) {
            throw new BusinessException(ResponseEnum.WORKFLOW_NOT_EXIST);
        }
        dataPermissionCheckTool.checkWorkflowBelong(workflow, SpaceInfoUtil.getSpaceId());

        LambdaUpdateWrapper<WorkflowVersion> updateWrapper = new LambdaUpdateWrapper<>();
        // Update id corresponding records, set getDeleted to 2 for deletion
        updateWrapper
                .eq(WorkflowVersion::getId, id)
                .set(WorkflowVersion::getDeleted, 2);
        // Execute update
        workflowVersionMapper.update(null, updateWrapper);

        return ApiResult.success(new JSONObject());
    }

    /**
     * Query publish results for a specific workflow version.
     *
     * @param flowId Flow ID to query
     * @param name Version name to query
     * @return List of publish results by channel
     */
    public Object publishResult(String flowId, String name) {
        log.info("Starting to query workflow version publish result, input workflow flowId: {}, version name: {}", flowId, name);
        // Security validation
        Workflow workflow = workflowMapper.selectOne(Wrappers.lambdaQuery(Workflow.class).eq(Workflow::getFlowId, flowId));
        if (workflow == null) {
            throw new BusinessException(ResponseEnum.WORKFLOW_NOT_EXIST);
        }
        Long spaceId = SpaceInfoUtil.getSpaceId();
        // dataPermissionCheckTool.checkWorkflowBelong(workflow, spaceId);

        List<WorkflowVersion> workflowVersions = workflowVersionMapper.selectList(Wrappers.lambdaQuery(WorkflowVersion.class).eq(WorkflowVersion::getFlowId, flowId).eq(WorkflowVersion::getName, name));
        List<Map<String, Object>> resultList = new ArrayList<>();
        Set<Long> addedChannels = new HashSet<>();
        for (WorkflowVersion version : workflowVersions) {
            Long publishChannel = version.getPublishChannel();
            if (!addedChannels.contains(publishChannel)) {
                Map<String, Object> map = new HashMap<>();
                map.put("publishChannel", publishChannel);
                map.put("publishResult", version.getPublishResult());
                resultList.add(map);
                addedChannels.add(publishChannel);
            }
        }
        return ApiResult.success(resultList);
    }

    /**
     * Update channel publish result for a version.
     *
     * @param createDto Update parameters including result status
     * @return Update operation result
     */
    @Transactional
    public ApiResult<JSONObject> update_channel_result(WorkflowVersion createDto) {
        log.info("Starting to update version result, input data: {}", createDto);
        WorkflowVersion workflowVersion = workflowVersionMapper.selectOne(Wrappers.lambdaQuery(WorkflowVersion.class).eq(WorkflowVersion::getId, createDto.getId()));
        if (workflowVersion == null) {
            throw new BusinessException(ResponseEnum.WORKFLOW_VERSION_NOT_FOUND);
        }
        Workflow workflow = workflowMapper.selectOne(Wrappers.lambdaQuery(Workflow.class).eq(Workflow::getFlowId, workflowVersion.getFlowId()));
        if (workflow == null) {
            throw new BusinessException(ResponseEnum.WORKFLOW_NOT_EXIST);
        }
        // dataPermissionCheckTool.checkWorkflowBelong(workflow);

        try {
            LambdaUpdateWrapper<WorkflowVersion> updateWrapper = new LambdaUpdateWrapper<>();
            // Update flowId corresponding records, set isVersion to 2
            updateWrapper.eq(WorkflowVersion::getId, createDto.getId())
                    .set(WorkflowVersion::getPublishResult, createDto.getPublishResult())
                    .set(WorkflowVersion::getUpdatedTime, new Date());
            // Execute update
            workflowVersionMapper.update(null, updateWrapper);
            log.info("Workflow version publish result successful, version ID: {}, publish result: {}", createDto.getId(), createDto.getPublishResult());

            return ApiResult.success(new JSONObject());
        } catch (Exception e) {
            log.info("Workflow version publish result failed, failure reason: {}, version ID: {}, publish result: {}", e, createDto.getId(), createDto.getPublishResult());
            throw new BusinessException(ResponseEnum.WORKFLOW_VERSION_PUBLISH_FAILED);
        }
    }

    /**
     * Get maximum version for a specific bot.
     *
     * @param botId Bot ID to query maximum version for
     * @return API result with maximum version info
     */
    public ApiResult<JSONObject> getMaxVersion(String botId) {
        log.info("Querying workflow maximum version number, botId: {}", botId);

        try {
            // Query latest version (ordered by creation time descending)
            WorkflowVersion latestVersion = workflowVersionMapper.selectOne(
                    Wrappers.lambdaQuery(WorkflowVersion.class)
                            .eq(WorkflowVersion::getBotId, botId)
                            .eq(WorkflowVersion::getPublishResult, "Success")
                            .orderByDesc(WorkflowVersion::getCreatedTime)
                            .last("LIMIT 1"));

            // Return result: if version exists return version name, if no version return "Draft Version"
            String versionDisplay = (latestVersion != null) ? latestVersion.getName() : "Draft Version";

            return ApiResult.success(new JSONObject().fluentPut("workflowMaxVersion", versionDisplay));
        } catch (Exception e) {
            log.error("Query workflow maximum version number exception, botId: {}", botId, e);
            throw new BusinessException(ResponseEnum.WORKFLOW_VERSION_GET_MAX_FAILED);
        }
    }
}
