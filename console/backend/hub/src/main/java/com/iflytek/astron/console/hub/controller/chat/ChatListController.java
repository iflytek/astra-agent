package com.iflytek.astron.console.hub.controller.chat;

import com.iflytek.astron.console.commons.constant.ResponseEnum;
import com.iflytek.astron.console.commons.entity.bot.ChatBotMarket;
import com.iflytek.astron.console.commons.exception.BusinessException;
import com.iflytek.astron.console.commons.response.ApiResult;
import com.iflytek.astron.console.commons.util.RequestContextUtil;
import com.iflytek.astron.console.commons.util.space.SpaceInfoUtil;
import com.iflytek.astron.console.commons.service.bot.ChatBotDataService;
import com.iflytek.astron.console.commons.entity.bot.BotInfoDto;
import com.iflytek.astron.console.commons.entity.chat.ChatListCreateRequest;
import com.iflytek.astron.console.commons.entity.chat.ChatListCreateResponse;
import com.iflytek.astron.console.commons.entity.chat.ChatListDelRequest;
import com.iflytek.astron.console.commons.entity.chat.ChatListResponseDto;
import com.iflytek.astron.console.commons.entity.bot.ChatBotBase;
import com.iflytek.astron.console.commons.enums.ShelfStatusEnum;
import com.iflytek.astron.console.hub.service.chat.ChatListService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * @author yingpeng
 */
@RestController
@Tag(name = "Chat List")
@RequestMapping("/chat-list")
public class ChatListController {

    @Autowired
    private ChatListService chatListService;

    @Autowired
    private ChatBotDataService chatBotDataService;

    /**
     * All chat list
     */
    @PostMapping("/all-chat-list")
    @Operation(summary = "All Chat List")
    public ApiResult<List<ChatListResponseDto>> getAllChatList() {
        String uid = RequestContextUtil.getUID();
        List<ChatListResponseDto> allChatList = chatListService.allChatList(uid, null);
        return ApiResult.success(allChatList);
    }

    @PostMapping("/v1/create-chat-list")
    @Operation(summary = "Create Chat List")
    public ApiResult<ChatListCreateResponse> createChatList(
            @RequestBody ChatListCreateRequest payload) {
        String uid = getCurrentUserId();
        setDefaultChatListName(payload);
        Integer botId = validateBotId(payload.getBotId());
        validateBotPermissions(botId, uid);
        return ApiResult.success(chatListService.createChatList(uid, payload.getChatListName(), botId));
    }

    @PostMapping("/v1/del-chat-list")
    @Operation(summary = "Delete Chat List")
    public ApiResult<Boolean> deleteChatList(
            @RequestBody ChatListDelRequest payload) {
        String uid = RequestContextUtil.getUID();
        if (payload.getChatListId() == null) {
            throw new BusinessException(ResponseEnum.PARAMS_ERROR);
        }
        Long chatListId = payload.getChatListId();

        return ApiResult.success(chatListService.logicDeleteChatList(chatListId, uid));
    }

    /**
     * Get bot information.
     *
     * @param request HTTP request object
     * @param botId Bot ID
     * @param workflowVersion Optional workflow version parameter
     * @return ApiResult object containing bot information
     */
    @GetMapping("/v1/get-bot-info")
    @Operation(summary = "Get Bot Information")
    public ApiResult<BotInfoDto> getBotInfo(HttpServletRequest request, Integer botId, @RequestParam(required = false) String workflowVersion) {
        String uid = RequestContextUtil.getUID();
        return ApiResult.success(chatListService.getBotInfo(request, uid, botId, workflowVersion));
    }

    /**
     * Get current user ID
     *
     * @return Current user's ID
     */
    private String getCurrentUserId() {
        return RequestContextUtil.getUID();
    }

    /**
     * Set default chat list name. If chat list name is empty, set default name based on display type
     *
     * @param payload Chat list creation request object
     */
    private void setDefaultChatListName(ChatListCreateRequest payload) {
        if (StringUtils.isBlank(payload.getChatListName())) {
            if (payload.getShowType() != null && payload.getShowType() == 2) {
                payload.setChatListName("New Chat");
            } else {
                payload.setChatListName("New Chat Window");
            }
        }
    }

    /**
     * Validate if bot ID is valid
     *
     * @param botId Bot ID to be validated
     * @return Returns original value if botId is valid, otherwise throws exception
     */
    private Integer validateBotId(Integer botId) {
        if (botId == null || botId == 0) {
            throw new BusinessException(ResponseEnum.BOT_NOT_EXISTS);
        }
        return botId;
    }

    /**
     * Validate bot permissions
     *
     * @param botId Bot ID
     * @param uid User ID
     */
    private void validateBotPermissions(Integer botId, String uid) {
        ChatBotMarket chatBotMarket = chatBotDataService.findMarketBotByBotId(botId);

        if (chatBotMarket != null) {
            validateMarketBotPermissions(chatBotMarket, uid);
        } else {
            validatePrivateBotPermissions(botId, uid);
        }
    }

    /**
     * Validate market bot permissions
     *
     * @param chatBotMarket Chat bot market object
     * @param uid User unique identifier
     * @throws BusinessException Throws business exception if no approved permission
     */
    private void validateMarketBotPermissions(ChatBotMarket chatBotMarket, String uid) {
        if (ShelfStatusEnum.isOffShelf(chatBotMarket.getBotStatus()) &&
                !chatBotMarket.getUid().equals(uid)) {
            throw new BusinessException(ResponseEnum.USER_NO_APPROVEL);
        }
    }

    /**
     * Validate private bot permissions
     *
     * @param botId Bot ID
     * @param uid User ID
     */
    private void validatePrivateBotPermissions(Integer botId, String uid) {
        ChatBotBase chatBotBase = chatBotDataService.findById(botId)
                .orElseThrow(() -> new BusinessException(ResponseEnum.BOT_NOT_EXISTS));

        if (!chatBotBase.getUid().equals(uid)) {
            validateSpacePermissions(chatBotBase);
        }
    }

    /**
     * Validate user permissions in specified space
     *
     * @param chatBotBase Bot basic information object
     */
    private void validateSpacePermissions(ChatBotBase chatBotBase) {
        Long spaceId = SpaceInfoUtil.getSpaceId();

        if (spaceId != null) {
            if (!spaceId.equals(chatBotBase.getSpaceId()) || !SpaceInfoUtil.checkUserBelongSpace()) {
                throw new BusinessException(ResponseEnum.USER_NO_APPROVEL);
            }
        } else {
            throw new BusinessException(ResponseEnum.USER_NO_APPROVEL);
        }
    }

}
