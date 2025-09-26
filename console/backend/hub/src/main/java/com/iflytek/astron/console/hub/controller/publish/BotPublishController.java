package com.iflytek.astron.console.hub.controller.publish;

import com.iflytek.astron.console.commons.annotation.RateLimit;
import com.iflytek.astron.console.commons.response.ApiResult;
import com.iflytek.astron.console.commons.util.RequestContextUtil;
import com.iflytek.astron.console.commons.util.space.SpaceInfoUtil;
import com.iflytek.astron.console.hub.dto.PageResponse;
import com.iflytek.astron.console.commons.dto.bot.BotListRequestDto;
import com.iflytek.astron.console.hub.dto.publish.BotPublishInfoDto;
import com.iflytek.astron.console.hub.dto.publish.BotDetailResponseDto;
import com.iflytek.astron.console.hub.dto.publish.PublishStatusUpdateDto;
import com.iflytek.astron.console.hub.dto.publish.BotSummaryStatsVO;
import com.iflytek.astron.console.hub.dto.publish.BotTimeSeriesResponseDto;
import com.iflytek.astron.console.hub.dto.publish.BotVersionVO;
import com.iflytek.astron.console.hub.dto.publish.WechatAuthUrlRequestDto;
import com.iflytek.astron.console.hub.dto.publish.WechatAuthUrlResponseDto;
import com.iflytek.astron.console.hub.dto.publish.mcp.McpContentResponseDto;
import com.iflytek.astron.console.hub.dto.publish.mcp.McpPublishRequestDto;
import com.iflytek.astron.console.commons.dto.workflow.WorkflowInputsResponseDto;
import com.iflytek.astron.console.hub.service.publish.BotPublishService;
import com.iflytek.astron.console.hub.service.publish.McpService;
import com.iflytek.astron.console.hub.service.publish.WorkflowInputService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import io.swagger.v3.oas.annotations.Parameter;
import org.springframework.validation.annotation.Validated;


/**
 * Bot Publishing Management Controller
 *
 * Provides comprehensive bot publishing management capabilities including: - Bot list querying with
 * filtering and pagination - Publishing status management (publish/unpublish) - Multi-channel
 * publishing (Market, API, WeChat, MCP) - Publishing analytics and statistics - Version management
 * for workflow bots
 *
 * @author Omuigix
 */
@Slf4j
@Tag(name = "Bot Publishing Management", description = "Comprehensive bot publishing management and analytics APIs")
@RestController
@RequestMapping("/api/v1/publish")
@RequiredArgsConstructor
@Validated
public class BotPublishController {

    private final BotPublishService botPublishService;
    private final McpService mcpService;
    private final WorkflowInputService workflowInputService;

    /**
     * Retrieve paginated bot list with advanced filtering
     */
    @Operation(
            summary = "Get bot list",
            description = "Retrieve paginated bot list with support for filtering by status, type, and search terms")
    @RateLimit(limit = 30, window = 60, dimension = "USER")
    @GetMapping("/bots")
    public ApiResult<PageResponse<BotPublishInfoDto>> getBotList(
            @ModelAttribute @Valid BotListRequestDto requestDto) {

        String currentUid = RequestContextUtil.getUID();
        Long spaceId = SpaceInfoUtil.getSpaceId();

        PageResponse<BotPublishInfoDto> result = botPublishService.getBotList(requestDto, currentUid, spaceId);

        log.info("Bot list retrieved successfully: uid={}, total={}", currentUid, result.getTotal());

        return ApiResult.success(result);
    }

    /**
     * Get detailed information for a specific bot
     */
    @Operation(
            summary = "Get bot details",
            description = "Retrieve comprehensive bot information including publishing status, channels, and metadata")
    @RateLimit(limit = 100, window = 60, dimension = "USER")
    @GetMapping("/bots/{botId}")
    public ApiResult<BotDetailResponseDto> getBotDetail(
            @Parameter(description = "Unique bot identifier", required = true)
            @PathVariable Integer botId) {

        String currentUid = RequestContextUtil.getUID();
        Long spaceId = SpaceInfoUtil.getSpaceId();

        log.info("Retrieving bot details: botId={}, uid={}, spaceId={}", botId, currentUid, spaceId);

        BotDetailResponseDto result = botPublishService.getBotDetail(botId, currentUid, spaceId);

        log.info("Bot details retrieved successfully: botId={}, channels={}", botId, result.getPublishChannels());

        return ApiResult.success(result);
    }

    /**
     * Update bot publishing status (publish to market or unpublish)
     */
    @Operation(
            summary = "Update bot publishing status",
            description = "Publish bot to marketplace or remove from marketplace with status management")
    @RateLimit(limit = 10, window = 60, dimension = "USER")
    @PostMapping("/bots/{botId}/status")
    public ApiResult<Void> updatePublishStatus(
            @Parameter(description = "Unique bot identifier", required = true)
            @PathVariable Integer botId,
            @Valid @RequestBody PublishStatusUpdateDto updateDto) {

        String currentUid = RequestContextUtil.getUID();
        Long spaceId = SpaceInfoUtil.getSpaceId();

        log.info("Updating bot publish status: botId={}, action={}, uid={}, spaceId={}",
                botId, updateDto.getAction(), currentUid, spaceId);

        botPublishService.updatePublishStatus(botId, updateDto, currentUid, spaceId);

        log.info("Bot publish status updated successfully: botId={}, action={}", botId, updateDto.getAction());
        return ApiResult.success();
    }

    /**
     * Get comprehensive usage statistics for a bot
     */
    @Operation(
            summary = "Get bot summary statistics",
            description = "Retrieve overall bot usage metrics including total conversations, users, and tokens")
    @RateLimit(limit = 20, window = 60, dimension = "USER")
    @GetMapping("/bots/{botId}/summary")
    public ApiResult<BotSummaryStatsVO> getBotSummaryStats(
            @Parameter(description = "Unique bot identifier", required = true)
            @PathVariable Integer botId) {

        String currentUid = RequestContextUtil.getUID();
        Long currentSpaceId = SpaceInfoUtil.getSpaceId();

        log.info("Retrieving bot summary statistics: botId={}, uid={}, spaceId={}",
                botId, currentUid, currentSpaceId);

        BotSummaryStatsVO summaryStats = botPublishService.getBotSummaryStats(
                botId, currentUid, currentSpaceId);

        log.info("Bot summary statistics retrieved successfully: botId={}", botId);
        return ApiResult.success(summaryStats);
    }

    /**
     * Get time-series usage statistics for a bot
     */
    @Operation(
            summary = "Get bot time series statistics",
            description = "Retrieve daily usage metrics over a specified time period for trend analysis")
    @RateLimit(limit = 20, window = 60, dimension = "USER")
    @GetMapping("/bots/{botId}/timeseries")
    public ApiResult<BotTimeSeriesResponseDto> getBotTimeSeriesStats(
            @Parameter(description = "Unique bot identifier", required = true)
            @PathVariable Integer botId,

            @Parameter(description = "Number of days to analyze (1-365)", example = "7")
            @RequestParam(value = "days", defaultValue = "7")
            @Min(value = 1, message = "Days must be at least 1")
            @Max(value = 365, message = "Days cannot exceed 365") Integer days) {

        String currentUid = RequestContextUtil.getUID();
        Long currentSpaceId = SpaceInfoUtil.getSpaceId();

        log.info("Retrieving bot time series statistics: botId={}, days={}, uid={}, spaceId={}",
                botId, days, currentUid, currentSpaceId);

        BotTimeSeriesResponseDto timeSeriesData = botPublishService.getBotTimeSeriesStats(
                botId, days, currentUid, currentSpaceId);

        log.info("Bot time series statistics retrieved successfully: botId={}", botId);
        return ApiResult.success(timeSeriesData);
    }

    /**
     * Get version history for workflow-based bots
     */
    @Operation(
            summary = "Get bot version history",
            description = "Retrieve paginated list of workflow bot versions with metadata and deployment history")
    @RateLimit(limit = 50, window = 60, dimension = "USER")
    @GetMapping("/bots/{botId}/versions")
    public ApiResult<PageResponse<BotVersionVO>> getBotVersions(
            @Parameter(description = "Unique bot identifier", required = true)
            @PathVariable Integer botId,

            @Parameter(description = "Page number (1-based)", example = "1")
            @RequestParam(value = "page", defaultValue = "1")
            @Min(value = 1, message = "Page number must be at least 1") Integer page,

            @Parameter(description = "Number of items per page (1-100)", example = "10")
            @RequestParam(value = "size", defaultValue = "10")
            @Min(value = 1, message = "Page size must be at least 1")
            @Max(value = 100, message = "Page size cannot exceed 100") Integer size) {

        String currentUid = RequestContextUtil.getUID();
        Long currentSpaceId = SpaceInfoUtil.getSpaceId();

        log.info("Retrieving bot version history: botId={}, page={}, size={}, uid={}, spaceId={}",
                botId, page, size, currentUid, currentSpaceId);

        PageResponse<BotVersionVO> result = botPublishService.getBotVersions(
                botId, page, size, currentUid, currentSpaceId);

        log.info("Bot version history retrieved successfully: botId={}, total={}", botId, result.getTotal());
        return ApiResult.success(result);
    }

    /**
     * Generate WeChat Official Account authorization URL
     */
    @Operation(
            summary = "Get WeChat authorization URL",
            description = "Generate authorization URL for binding bot to WeChat Official Account for publishing")
    @RateLimit(limit = 10, window = 60, dimension = "USER")
    @PostMapping("/wechat/auth-url")
    public ApiResult<WechatAuthUrlResponseDto> getWechatAuthUrl(
            @Valid @RequestBody WechatAuthUrlRequestDto request) {

        String uid = RequestContextUtil.getUID();
        Long spaceId = SpaceInfoUtil.getSpaceId();

        log.info("Generating WeChat authorization URL: botId={}, appid={}, redirectUrl={}, uid={}, spaceId={}",
                request.getBotId(), request.getAppid(), request.getRedirectUrl(), uid, spaceId);

        WechatAuthUrlResponseDto result = botPublishService.getWechatAuthUrl(
                request.getBotId(), request.getAppid(), request.getRedirectUrl(), uid, spaceId);

        log.info("WeChat authorization URL generated successfully: botId={}", request.getBotId());
        return ApiResult.success(result);
    }

    // ==================== MCP Publishing Management ====================

    /**
     * Get MCP service configuration for a bot
     */
    @Operation(
            summary = "Get MCP service details",
            description = "Retrieve MCP service configuration including server details, parameters, and publishing status")
    @RateLimit(limit = 50, window = 60, dimension = "USER")
    @GetMapping("/mcp/{botId}")
    public ApiResult<McpContentResponseDto> getMcpContent(
            @Parameter(description = "Unique bot identifier", required = true)
            @PathVariable Integer botId) {

        String currentUid = RequestContextUtil.getUID();
        Long spaceId = SpaceInfoUtil.getSpaceId();

        log.info("Retrieving MCP service details: botId={}, uid={}, spaceId={}", botId, currentUid, spaceId);

        McpContentResponseDto result = mcpService.getMcpContent(botId, currentUid, spaceId);

        log.info("MCP service details retrieved successfully: botId={}, released={}", botId, result.getReleased());
        return ApiResult.success(result);
    }

    /**
     * Publish bot as MCP service
     */
    @Operation(
            summary = "Publish bot to MCP",
            description = "Publish workflow bot as MCP (Model Context Protocol) service with custom server configuration")
    @RateLimit(limit = 10, window = 60, dimension = "USER")
    @PostMapping("/mcp/publish")
    public ApiResult<Void> publishMcp(@Valid @RequestBody McpPublishRequestDto request) {

        String currentUid = RequestContextUtil.getUID();
        Long spaceId = SpaceInfoUtil.getSpaceId();

        log.info("Publishing bot to MCP: botId={}, serverName={}, uid={}, spaceId={}",
                request.getBotId(), request.getServerName(), currentUid, spaceId);

        mcpService.publishMcp(request, currentUid, spaceId);

        log.info("Bot published to MCP successfully: botId={}, serverName={}",
                request.getBotId(), request.getServerName());
        return ApiResult.success();
    }

    /**
     * Get workflow input parameters for MCP publishing
     */
    @Operation(
            summary = "Get bot input parameters",
            description = "Retrieve dynamic input parameter definitions for workflow bot, used for MCP service configuration")
    @RateLimit(limit = 50, window = 60, dimension = "USER")
    @GetMapping("/mcp/{botId}/inputs")
    public ApiResult<WorkflowInputsResponseDto> getInputsType(
            @Parameter(description = "Unique bot identifier", required = true)
            @PathVariable Integer botId) {

        String currentUid = RequestContextUtil.getUID();
        Long spaceId = SpaceInfoUtil.getSpaceId();

        log.info("Retrieving bot input parameters: botId={}, uid={}, spaceId={}", botId, currentUid, spaceId);

        WorkflowInputsResponseDto result = workflowInputService.getInputsType(botId, currentUid, spaceId);

        log.info("Bot input parameters retrieved successfully: botId={}, paramCount={}",
                botId, result.getParameters().size());
        return ApiResult.success(result);
    }
}
