package com.iflytek.astron.console.toolkit.mapper.workflow;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.iflytek.astron.console.commons.entity.workflow.Workflow;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;


@Mapper
public interface WorkflowMapper extends BaseMapper<Workflow> {

    List<Workflow> selectSuqareFlowList(@Param("page") Page<Workflow> page,
            @Param("uid") String uid,
            @Param("configId") Integer configId,
            @Param("adminUid") String adminUid,
            @Param("name") String name);

    Integer checkDomainIsUsage(@Param("uid") String uid, @Param("domain") String domain);
}
