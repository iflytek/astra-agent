package com.iflytek.astra.console.hub.controller.chat;

import cn.hutool.core.util.ObjectUtil;
import com.iflytek.astra.console.commons.constant.ResponseEnum;
import com.iflytek.astra.console.commons.entity.bot.ChatBotMarket;
import com.iflytek.astra.console.commons.exception.BusinessException;
import com.iflytek.astra.console.commons.response.ApiResult;
import com.iflytek.astra.console.commons.util.RequestContextUtil;
import com.iflytek.astra.console.commons.service.bot.ChatBotDataService;
import com.iflytek.astra.console.commons.service.data.ChatListDataService;
import com.iflytek.astra.console.commons.entity.chat.ChatListCreateResponse;
import com.iflytek.astra.console.commons.entity.bot.ChatBotBase;
import com.iflytek.astra.console.commons.entity.chat.ChatList;
import com.iflytek.astra.console.commons.enums.ShelfStatusEnum;
import com.iflytek.astra.console.hub.service.chat.ChatReqRespService;
import com.iflytek.astra.console.hub.service.chat.ChatRestartService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * @author yingpeng
 */
@RestController
@Tag(name = "New Chat")
@RequestMapping("/chat-restart")
@Slf4j
public class ChatRestartController {

    @Autowired
    private ChatListDataService chatListDataService;

    @Autowired
    private ChatBotDataService chatBotDataService;

    @Autowired
    private ChatReqRespService chatReqRespService;

    @Autowired
    private ChatRestartService chatRestartService;

    @PostMapping(value = "/restart")
    @Operation(summary = "Start New Chat")
    public ApiResult<ChatListCreateResponse> restart(@RequestParam("chatId") Long chatId) {
        String uid = RequestContextUtil.getUID();

        ChatList chatList = chatListDataService.findByUidAndChatId(uid, chatId);
        if (chatList == null) {
            log.info("Chat window is unavailable or illegal access, uid{}, chatId{}", uid, chatId);
            return ApiResult.error(ResponseEnum.CHAT_REQ_NOT_BELONG_ERROR);
        }

        // Multi-turn assistant supports old logic for new chat
        if (ObjectUtil.isNotEmpty(chatList.getBotId()) && chatList.getBotId() > 0) {
            ChatBotMarket chatBotMarket = chatBotDataService.findMarketBotByBotId(chatList.getBotId());
            Integer supportContext;
            if (chatBotMarket != null && ShelfStatusEnum.isOnShelf(chatBotMarket.getBotStatus())) {
                supportContext = chatBotMarket.getSupportContext();
            } else {
                ChatBotBase chatBotBase = chatBotDataService.findById(chatList.getBotId())
                        .orElseThrow(() -> new BusinessException(ResponseEnum.BOT_NOT_EXISTS));
                supportContext = chatBotBase.getSupportContext();
            }
            if (supportContext.equals(1)) {
                chatReqRespService.updateBotChatContext(chatId, uid, chatList.getBotId());
            }
        }

        return ApiResult.success(chatRestartService.createNewTreeIndexByRootChatId(chatId, uid, null));
    }

}
