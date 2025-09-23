package com.iflytek.astra.console.toolkit.mapper.trace;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.iflytek.astra.console.toolkit.entity.dto.ToolUseDto;
import com.iflytek.astra.console.toolkit.entity.dto.WorkflowModelErrorReq;
import com.iflytek.astra.console.toolkit.entity.table.trace.ChatInfo;
import com.iflytek.astra.console.toolkit.entity.vo.WorkflowErrorVo;
import com.iflytek.astra.console.toolkit.entity.vo.WorkflowUserFeedbackErrorVo;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.Date;
import java.util.List;

@Mapper
public interface ChatInfoMapper extends BaseMapper<ChatInfo> {
    Long selectUserCount(@Param("botId") String botId, @Param("flowId") Long flowId, @Param("startDate") Date startDate, @Param("endDate") Date endDate);

    Long selectTokenSum(@Param("botId") String botId, @Param("flowId") Long flowId, @Param("startDate") Date startDate, @Param("endDate") Date endDate);

    List<WorkflowErrorVo> getErrorBySidList(@Param("sidList") List<String> sidList);

    List<WorkflowUserFeedbackErrorVo> getUserFeedBackErrorInfo(
            @Param("params") WorkflowModelErrorReq workflowModelErrorReq);

    List<ToolUseDto> selectWorkflowUseCount(@Param("toolIds") List<String> toolIds);

    List<ToolUseDto> selectBotUseCount(@Param("toolIds") List<String> toolIds);
}
