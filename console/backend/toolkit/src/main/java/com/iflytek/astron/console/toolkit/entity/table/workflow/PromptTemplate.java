package com.iflytek.astron.console.toolkit.entity.table.workflow;

import com.alibaba.fastjson2.JSONObject;
import com.baomidou.mybatisplus.annotation.TableField;
import com.iflytek.astron.console.toolkit.service.workflow.WorkflowService;
import lombok.Data;
import java.util.Date;
import java.util.List;

@Data
public class PromptTemplate {

    Integer id;

    String uid;

    // Template name
    String name;

    // Template description
    String description;

    Boolean deleted;

    // Role setting\thinking process\ user question
    String prompt;

    Date createdTime;

    Date updatedTime;

    // Node category
    Integer nodeCategory;

    // Adapted model
    String adaptationModel;

    // Maximum inference loops
    Integer maxLoopCount;

    // Character settings
    @TableField(exist = false)
    String characterSettings;

    // Thinking process
    @TableField(exist = false)
    String thinkStep;

    // User question
    @TableField(exist = false)
    String userQuery;

    @TableField(exist = false)
    JSONObject jsonAdaptationModel;

    @TableField(exist = false)
    List<WorkflowService.Input> inputs;

}
