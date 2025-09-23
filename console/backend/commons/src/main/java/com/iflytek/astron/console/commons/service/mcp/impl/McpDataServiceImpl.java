package com.iflytek.astron.console.commons.service.mcp.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.iflytek.astron.console.commons.entity.model.McpData;
import com.iflytek.astron.console.commons.mapper.model.McpDataMapper;
import com.iflytek.astron.console.commons.service.mcp.McpDataService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Objects;

/**
 * @author wowo_zZ
 * @since 2025/9/11 09:58
 **/

@Service
public class McpDataServiceImpl implements McpDataService {

    @Autowired
    private McpDataMapper mcpDataMapper;

    @Override
    public List<McpData> getMcpByUid(String uid) {
        return mcpDataMapper.selectList(Wrappers.lambdaQuery(McpData.class)
                .eq(McpData::getUid, uid)
                .orderByDesc(McpData::getCreateTime));
    }

    @Override
    public McpData insert(McpData mcpData) {
        mcpDataMapper.insert(mcpData);
        return mcpData;
    }

    @Override
    public McpData getMcp(Long botId) {
        LambdaQueryWrapper<McpData> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(McpData::getBotId, botId)
                .orderByDesc(McpData::getCreateTime)
                .last("limit 1");
        McpData mcpData = mcpDataMapper.selectOne(queryWrapper);


        if (Objects.nonNull(mcpData)) {
            mcpData.setReleased(1);
        } else {
            mcpData = new McpData();
            mcpData.setReleased(0);
        }
        return mcpData;
    }

}
