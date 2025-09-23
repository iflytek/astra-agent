package com.iflytek.astron.console.toolkit.mapper.trace;

import com.github.pagehelper.Page;
import com.github.yulichang.base.MPJBaseMapper;
import com.iflytek.astron.console.toolkit.entity.dto.WorkflowModelErrorReq;
import com.iflytek.astron.console.toolkit.entity.dto.eval.NodeDataDto;
import com.iflytek.astron.console.toolkit.entity.table.trace.NodeInfo;
import com.iflytek.astron.console.toolkit.entity.vo.WorkflowErrorModelVo;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface NodeInfoMapper extends MPJBaseMapper<NodeInfo> {
    Page<NodeDataDto> selectMarkedNodePage(
            @Param("botId") String botId,
            @Param("flowId") String flowId,
            @Param("list") List<String> nodeIdList);

    List<NodeDataDto> selectMarkedInIdList(
            @Param("list") List<String> idList);

    List<NodeDataDto> selectMarkedNodeList(
            @Param("sidList") List<String> sidList,
            @Param("nodeIdList") List<String> nodeIdList);

    List<NodeDataDto> selectMarkedNodeList2(
            @Param("list") List<String> sidList,
            @Param("nodeId") String nodeId);

    List<WorkflowErrorModelVo> getNodeErrorInfo(@Param("params") WorkflowModelErrorReq workflowModelErrorReq);

    List<String> getSidList(@Param("params") WorkflowModelErrorReq params,
            @Param("nodeName") String nodeName);

    long getNodeCallNum(@Param("params") WorkflowModelErrorReq params,
            @Param("nodeName") String nodeName);
}
