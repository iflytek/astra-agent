package com.iflytek.astron.console.hub.service.workflow.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.iflytek.astron.console.hub.entity.WorkflowTemplateGroup;
import com.iflytek.astron.console.hub.mapper.WorkflowTemplateGroupMapper;
import com.iflytek.astron.console.hub.service.workflow.WorkflowTemplateGroupService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * @author cherry
 */
@Service
public class WorkflowTemplateGroupServiceImpl implements WorkflowTemplateGroupService {
    @Autowired
    private WorkflowTemplateGroupMapper workflowTemplateGroupMapper;

    @Override
    public List<WorkflowTemplateGroup> getTemplateGroup() {

        return workflowTemplateGroupMapper.selectList(Wrappers.lambdaQuery(WorkflowTemplateGroup.class)
                .eq(WorkflowTemplateGroup::getIsDelete, false));
    }
}
