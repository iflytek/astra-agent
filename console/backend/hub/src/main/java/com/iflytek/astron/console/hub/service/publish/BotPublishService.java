package com.iflytek.astron.console.hub.service.publish;

import com.iflytek.astron.console.hub.dto.PageResponse;
import com.iflytek.astron.console.commons.dto.bot.BotListRequestDto;
import com.iflytek.astron.console.hub.dto.publish.BotPublishInfoDto;
import com.iflytek.astron.console.hub.dto.publish.BotDetailResponseDto;
import com.iflytek.astron.console.hub.dto.publish.PublishStatusUpdateDto;
import com.iflytek.astron.console.hub.dto.publish.BotVersionVO;
import com.iflytek.astron.console.hub.dto.publish.BotSummaryStatsVO;
import com.iflytek.astron.console.hub.dto.publish.BotTimeSeriesResponseDto;
import com.iflytek.astron.console.hub.dto.publish.WechatAuthUrlResponseDto;
import com.iflytek.astron.console.commons.enums.PublishChannelEnum;

/**
 * Bot Publishing Management Service Interface
 *
 * Unified bot publishing management service, including: - Bot list query and detail retrieval -
 * Publishing status management (publish/take offline) - Version management - Statistics data query
 *
 * @author Omuigix
 */
public interface BotPublishService {

    // ==================== Basic Publish Management ====================

    /**
     * Paginated query for bot list
     *
     * @param requestDto Query condition
     * @param currentUid Current user ID
     * @param spaceId Space ID
     * @return Pagination result
     */
    PageResponse<BotPublishInfoDto> getBotList(
            BotListRequestDto requestDto,
            String currentUid,
            Long spaceId);

    /**
     * Get bot details
     *
     * @param botId Bot ID
     * @param currentUid Current user ID
     * @param spaceId Space ID (optional)
     * @return Bot detail
     */
    BotDetailResponseDto getBotDetail(Integer botId, String currentUid, Long spaceId);

    /**
     * Update bot publish status - unified handling of publish and offline logic, using ShelfStatusEnum
     * (0=offline, 1=published)
     *
     * @param botId Bot ID
     * @param updateDto Status update request
     * @param currentUid Current user ID
     * @param spaceId Space ID (optional)
     */
    void updatePublishStatus(Integer botId, PublishStatusUpdateDto updateDto, String currentUid, Long spaceId);

    // ==================== Version Management ====================

    /**
     * Get bot version list - supports version history query for workflow-type bots
     *
     * @param botId Bot ID
     * @param page Page number
     * @param size Page size
     * @param uid User ID
     * @param spaceId Space ID
     * @return Version list
     */
    PageResponse<BotVersionVO> getBotVersions(Integer botId, Integer page, Integer size, String uid, Long spaceId);

    // ==================== Statistics Data ====================

    /**
     * Get bot summary statistics
     *
     * @param botId Bot ID
     * @param currentUid Current user ID
     * @param currentSpaceId Current space ID
     * @return Summary statistics data
     */
    BotSummaryStatsVO getBotSummaryStats(Integer botId, String currentUid, Long currentSpaceId);

    /**
     * Get bot time series statistics
     *
     * @param botId Bot ID
     * @param overviewDays Overview statistics days
     * @param currentUid Current user ID
     * @param currentSpaceId Current space ID
     * @return Time series statistics data
     */
    BotTimeSeriesResponseDto getBotTimeSeriesStats(Integer botId, Integer overviewDays,
            String currentUid, Long currentSpaceId);

    /**
     * Record conversation statistics data
     *
     * @param uid User ID
     * @param spaceId Space ID
     * @param botId Bot ID
     * @param chatId Chat ID
     * @param sid Session identifier
     * @param tokenConsumed Token consumption count
     * @param messageRounds Message rounds
     */
    void recordConversationStats(String uid, Long spaceId, Integer botId, Long chatId,
            String sid, Integer tokenConsumed, Integer messageRounds);

    // ==================== Publish Channel Management ====================

    /**
     * Update bot publish channel
     *
     * @param botId Bot ID
     * @param uid User ID
     * @param spaceId Space ID (can be null)
     * @param channel Publish channel enum
     * @param isAdd Whether to add channel (true=add, false=remove)
     */
    void updatePublishChannel(Integer botId, String uid, Long spaceId, PublishChannelEnum channel, boolean isAdd);

    // ==================== WeChat Publish Management ====================

    /**
     * Get WeChat official account authorization URL Corresponding to original interface: getAuthUrl
     *
     * @param botId Bot ID
     * @param appid WeChat official account AppID
     * @param redirectUrl Callback URL
     * @param uid Current user ID
     * @param spaceId Space ID
     * @return WeChat authorization URL
     */
    WechatAuthUrlResponseDto getWechatAuthUrl(Integer botId, String appid, String redirectUrl,
            String uid, Long spaceId);
}
