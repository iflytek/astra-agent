package com.iflytek.astron.console.commons.mapper.model;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.iflytek.astron.console.commons.entity.model.McpData;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * @author yun-zhi-ztl
 */
@Mapper
public interface McpDataMapper extends BaseMapper<McpData> {

    /**
     * 根据智能体ID获取最新的MCP数据
     *
     * @param botId 智能体ID
     * @return MCP数据
     */
    McpData selectLatestByBotId(@Param("botId") Integer botId);

    /**
     * 根据用户ID获取MCP数据列表
     *
     * @param uid 用户ID
     * @return MCP数据列表
     */
    List<McpData> selectByUid(@Param("uid") String uid);

    /**
     * 检查智能体是否已发布MCP
     *
     * @param botId 智能体ID
     * @param versionName 版本名称
     * @return 记录数量
     */
    int checkMcpExists(@Param("botId") Integer botId, @Param("versionName") String versionName);
}
