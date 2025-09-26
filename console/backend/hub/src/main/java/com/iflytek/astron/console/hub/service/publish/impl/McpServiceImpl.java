package com.iflytek.astron.console.hub.service.publish.impl;

import com.iflytek.astron.console.commons.constant.ResponseEnum;
import com.iflytek.astron.console.commons.exception.BusinessException;
import com.iflytek.astron.console.hub.dto.publish.mcp.McpContentResponseDto;
import com.iflytek.astron.console.hub.dto.publish.mcp.McpPublishRequestDto;
import com.iflytek.astron.console.commons.entity.model.McpData;
import com.iflytek.astron.console.commons.entity.bot.UserLangChainInfo;
import com.iflytek.astron.console.commons.mapper.bot.ChatBotBaseMapper;
import com.iflytek.astron.console.commons.mapper.model.McpDataMapper;
import com.iflytek.astron.console.commons.mapper.UserLangChainInfoMapper;
import com.iflytek.astron.console.hub.service.publish.McpService;
import com.iflytek.astron.console.hub.converter.McpDataConverter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.iflytek.astron.console.hub.service.publish.BotPublishService;
import com.iflytek.astron.console.commons.enums.PublishChannelEnum;

import java.time.LocalDateTime;

/**
 * MCP Service Implementation
 *
 * @author Omuigix
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class McpServiceImpl implements McpService {

    private final McpDataMapper mcpDataMapper;
    private final ChatBotBaseMapper chatBotBaseMapper;
    private final UserLangChainInfoMapper userLangChainInfoMapper;
    private final McpDataConverter mcpDataConverter;
    private final BotPublishService botPublishService;

    @Override
    public McpContentResponseDto getMcpContent(Integer botId, String currentUid, Long spaceId) {
        log.info("Get MCP content: botId={}, uid={}, spaceId={}", botId, currentUid, spaceId);

        // 1. Permission check
        int hasPermission = chatBotBaseMapper.checkBotPermission(botId, currentUid, spaceId);
        if (hasPermission == 0) {
            throw new BusinessException(ResponseEnum.BOT_NOT_EXISTS);
        }

        // 2. Query MCP data
        McpData mcpData = mcpDataMapper.selectLatestByBotId(botId);

        // 3. Build response
        McpContentResponseDto response;
        if (mcpData != null) {
            // Use MapStruct converter
            response = mcpDataConverter.toResponseDto(mcpData);
        } else {
            // No MCP data found, return default status
            response = new McpContentResponseDto();
            response.setBotId(botId);
            response.setReleased("0"); // Not published
        }

        log.info("MCP content query completed: botId={}, released={}", botId, response.getReleased());
        return response;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void publishMcp(McpPublishRequestDto request, String currentUid, Long spaceId) {
        log.info("Publish MCP: botId={}, serverName={}, uid={}, spaceId={}",
                request.getBotId(), request.getServerName(), currentUid, spaceId);

        Integer botId = request.getBotId();

        // 1. Permission check
        int hasPermission = chatBotBaseMapper.checkBotPermission(botId, currentUid, spaceId);
        if (hasPermission == 0) {
            throw new BusinessException(ResponseEnum.BOT_NOT_EXISTS);
        }

        // 2. Check if workflow protocol exists
        UserLangChainInfo chainInfo = userLangChainInfoMapper.selectOne(
                new com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<UserLangChainInfo>()
                        .eq("bot_id", botId)
                        .orderByDesc("create_time")
                        .last("LIMIT 1"));
        if (chainInfo == null) {
            log.info("Bot workflow protocol not found: uid={}, botId={}", currentUid, botId);
            throw new BusinessException(ResponseEnum.BOT_CHAIN_SUBMIT_ERROR);
        }

        // 3. Content moderation (simplified here, should call moderation service in production)
        // TODO: Call moderation service to check text and images
        // String allText = request.getServerName() + request.getDescription() + request.getContent();

        // 4. Get version name (using default version for now)
        String versionName = "v1.0"; // TODO: Get from version management service

        // 5. Check if MCP with same version already exists
        // int existCount = mcpDataMapper.checkMcpExists(botId, versionName);
        // if (existCount > 0) {
        // throw new BusinessException("MCP with this version already exists, please do not republish");
        // }

        // 6. Build MCP data
        McpData mcpData = McpData.builder()
                .botId(botId)
                .uid(currentUid)
                .spaceId(spaceId)
                .serverName(request.getServerName())
                .description(request.getDescription())
                .content(request.getContent())
                .icon(request.getIcon())
                .serverUrl(request.getServerUrl())
                .args(request.getArgs())
                .versionName(versionName)
                .released(1)
                .isDelete(0)
                .createTime(LocalDateTime.now())
                .updateTime(LocalDateTime.now())
                .build();

        // 7. Save MCP data
        int result = mcpDataMapper.insert(mcpData);
        if (result == 0) {
            throw new BusinessException(ResponseEnum.SYSTEM_ERROR);
        }

        // 8. Update publish channel
        botPublishService.updatePublishChannel(botId, currentUid, spaceId, PublishChannelEnum.MCP, true);

        log.info("MCP published successfully: botId={}, mcpId={}, versionName={}", botId, mcpData.getId(), versionName);
    }
}
