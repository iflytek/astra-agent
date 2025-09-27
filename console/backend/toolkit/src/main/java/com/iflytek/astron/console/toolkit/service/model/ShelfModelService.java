package com.iflytek.astron.console.toolkit.service.model;

import cn.hutool.core.collection.CollUtil;
import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.iflytek.astron.console.commons.constant.ResponseEnum;
import com.iflytek.astron.console.commons.entity.workflow.Workflow;
import com.iflytek.astron.console.commons.exception.BusinessException;
import com.iflytek.astron.console.commons.response.ApiResult;
import com.iflytek.astron.console.toolkit.entity.biz.workflow.BizWorkflowData;
import com.iflytek.astron.console.toolkit.entity.biz.workflow.BizWorkflowNode;
import com.iflytek.astron.console.toolkit.entity.table.ConfigInfo;
import com.iflytek.astron.console.toolkit.mapper.ConfigInfoMapper;
import com.iflytek.astron.console.toolkit.service.workflow.WorkflowService;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

/**
 * Service for managing model shelf operations Handles model removal from shelf and workflow updates
 *
 * @Author clliu19
 * @Date: 2025/9/11 16:51
 */
@Service
@Slf4j
public class ShelfModelService {
    @Autowired
    private ConfigInfoMapper configInfoMapper;
    @Resource
    private WorkflowService workflowService;

    /**
     * Remove model from shelf and update related workflows
     *
     * @param llmId The LLM model ID to remove from shelf
     * @param flowId Specific workflow ID to update (optional)
     * @param serviceId The service ID of the model being removed
     * @return Processing result
     * @throws BusinessException if parameters are invalid or operation fails
     */
    @Transactional(rollbackFor = Exception.class)
    public Object offShelfModel(Long llmId, String flowId, String serviceId) {
        // 0) Parameter validation
        if (llmId == null) {
            throw new BusinessException(ResponseEnum.RESPONSE_FAILED, "Invalid parameters: llmId/serviceId cannot be null");
        }

        // 1) Calculate operable workflow set (query only necessary columns to reduce IO)
        LambdaQueryWrapper<Workflow> lqw = new LambdaQueryWrapper<Workflow>()
                .select(Workflow::getId, Workflow::getFlowId, Workflow::getData, Workflow::getUpdateTime, Workflow::getDeleted);
        if (StringUtils.isNotBlank(flowId)) {
            lqw.eq(Workflow::getFlowId, flowId);
        } else {
            // Only replace in workflows containing oldServiceId in data to avoid accidental damage
            lqw.like(Workflow::getData, serviceId);
        }
        lqw.eq(Workflow::getDeleted, false);
        List<Workflow> workflows = workflowService.list(lqw);
        if (CollUtil.isEmpty(workflows)) {
            throw new BusinessException(ResponseEnum.RESPONSE_FAILED, "Flow list data is empty");
        }

        ConfigInfo configInfo = configInfoMapper.getByCategoryAndCode("NODE_PREFIX_MODEL", "switch");
        String value = configInfo.getValue();

        // 2) Node prefix whitelist (read from config first, fallback to built-in)
        Set<String> nodePrefixAllow = new HashSet<>(Arrays.asList(value.split(",")));

        // 3) Iterate and replace precisely, only modify when actually "hitting oldServiceId"
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
                // Only replace when current node actually references oldServiceId
                boolean hitOld = Objects.equals(llmId, nodeParam.getLong("llmId"));

                if (!hitOld) {
                    continue;
                }

                // Replacement logic
                nodeParam.put("modelEnabled", false);
                changed = true;
                if (changed) {
                    wf.setData(JSON.toJSONString(data));
                    workflowService.updateById(wf);
                    nodeTouched++;
                    wfChangedCount.merge(wf.getId(), 1, Integer::sum);
                }
            }

            if (changed) {
                toUpdate.add(wf);
            }
        }

        if (toUpdate.isEmpty()) {
            // No nodes hit, no update needed
            log.info("offModel: No nodes hit oldServiceId={}, flowId={}, no update performed", serviceId, flowId);
            return ApiResult.success(Collections.singletonMap("updated", 0));
        }

        // 4) Batch update (only update workflows that have changes)
        // workflowService.updateBatchById(toUpdate);
        log.info("offModel: Batch replacement completed, flowsUpdated={}, nodesTouched={}, details={}",
                toUpdate.size(), nodeTouched, wfChangedCount);

        Map<String, Object> ret = new HashMap<>();
        ret.put("flowsUpdated", toUpdate.size());
        ret.put("nodesTouched", nodeTouched);
        // key=workflowId, value=number of nodes hit
        ret.put("flowChangedDetails", wfChangedCount);
        return ApiResult.success(ret);
    }
}
