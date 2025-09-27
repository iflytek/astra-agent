package com.iflytek.astron.console.hub.controller.chat;

import cn.hutool.core.util.RandomUtil;
import com.iflytek.astron.console.commons.constant.ResponseEnum;
import com.iflytek.astron.console.commons.entity.bot.ChatBotBase;
import com.iflytek.astron.console.commons.entity.chat.ChatListCreateResponse;
import com.iflytek.astron.console.commons.exception.BusinessException;
import com.iflytek.astron.console.commons.response.ApiResult;
import com.iflytek.astron.console.commons.util.RequestContextUtil;
import com.iflytek.astron.console.commons.service.bot.ChatBotDataService;
import com.iflytek.astron.console.commons.service.data.ChatDataService;
import com.iflytek.astron.console.commons.service.data.ChatListDataService;
import com.iflytek.astron.console.commons.util.SseEmitterUtil;
import com.iflytek.astron.console.hub.dto.chat.BotDebugRequest;
import com.iflytek.astron.console.commons.entity.bot.ChatBotReqDto;
import com.iflytek.astron.console.hub.dto.chat.StopStreamResponse;
import com.iflytek.astron.console.commons.entity.chat.ChatList;
import com.iflytek.astron.console.commons.entity.chat.ChatReqRecords;
import com.iflytek.astron.console.commons.entity.chat.ChatTreeIndex;
import com.iflytek.astron.console.hub.service.chat.BotChatService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.PostConstruct;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.redisson.api.RTopic;
import org.redisson.api.RedissonClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.*;

/**
 * @author yingpeng
 */
@RestController
@Slf4j
@Tag(name = "Chat Messages")
@RequestMapping("/chat-message")
public class ChatMessageController {

    @Autowired
    private ChatBotDataService chatBotDataService;

    @Autowired
    private ChatListDataService chatListDataService;

    @Autowired
    private BotChatService botChatService;

    @Autowired
    private ChatDataService chatDataService;

    @Autowired
    private RedissonClient redissonClient;

    public static final String STOP_GENERATE_SUBSCRIBE_PUBLISH_CHANNEL = "stop_generate_sub_pub";

    /**
     * Conduct chat session based on chatId
     */
    @PostMapping(path = "/chat", produces = "text/event-stream;charset=UTF-8")
    @Operation(summary = "Conduct chat session based on chatId")
    public SseEmitter chat(@RequestParam Long chatId,
            @RequestParam String text,
            @RequestParam(required = false) String fileUrl,
            @RequestParam(required = false) String workflowOperation,
            @RequestParam(required = false) String workflowVersion) {
        String sseId = RandomUtil.randomString(8);
        SseEmitter sseEmitter = SseEmitterUtil.createSseEmitter();

        log.info("Establishing SSE connection, sseId: {}, chatId: {}", sseId, chatId);

        // Get the latest chat_id
        List<ChatTreeIndex> chatTreeIndexList = chatListDataService.findChatTreeIndexByChatIdOrderById(chatId);
        if (chatTreeIndexList.isEmpty()) {
            log.warn("chatId is empty, sseId: {}", sseId);
            SseEmitterUtil.sendError(sseEmitter, "Chat ID cannot be empty");
            SseEmitterUtil.sendEndAndComplete(sseEmitter);
            return sseEmitter;
        }
        Long lastChatId = chatTreeIndexList.getFirst().getChildChatId();

        // Validate request parameters
        ValidationResult validation = validateChatRequest(lastChatId, text, sseId, sseEmitter);
        if (!validation.isValid()) {
            return sseEmitter;
        }

        // Validate chat window and assistant status
        ChatContext chatContext = validateChatContext(lastChatId, null, sseId, sseEmitter);
        if (chatContext == null) {
            return sseEmitter;
        }

        return processChatRequest(chatContext, text, fileUrl, sseEmitter, sseId, workflowOperation, workflowVersion);
    }

    /**
     * Validate the validity of chat request
     *
     * @param chatId Chat room ID, may be null
     * @param text Chat text content, may be null or blank
     * @param sseId Server-sent events ID, identifies current connection
     * @param sseEmitter Server-sent events emitter, used to send messages to client
     * @return Returns valid result if validation passes, otherwise returns invalid result
     */
    private ValidationResult validateChatRequest(Long chatId, String text, String sseId, SseEmitter sseEmitter) {
        if (chatId == null) {
            log.warn("chatId is empty, sseId: {}", sseId);
            SseEmitterUtil.sendError(sseEmitter, "Chat ID cannot be empty");
            SseEmitterUtil.sendEndAndComplete(sseEmitter);
            return ValidationResult.invalid();
        }

        if (StringUtils.isBlank(text)) {
            log.warn("Chat content is empty, sseId: {}, chatId: {}", sseId, chatId);
            SseEmitterUtil.sendError(sseEmitter, "Please enter chat content");
            SseEmitterUtil.sendEndAndComplete(sseEmitter);
            return ValidationResult.invalid();
        }

        return ValidationResult.valid();
    }

    /**
     * Validate chat context
     *
     * @param chatId Chat ID
     * @param sseId Server-sent events ID
     * @param sseEmitter Server-sent events emitter
     * @return Valid chat context or null
     */
    private ChatContext validateChatContext(Long chatId, Long requestId, String sseId, SseEmitter sseEmitter) {
        String uid = RequestContextUtil.getUID();

        ChatList chatList = chatListDataService.findByUidAndChatId(uid, chatId);
        if (chatList == null) {
            log.warn("Chat window is unavailable or illegal access, sseId: {}, uid: {}, chatId: {}", sseId, uid, chatId);
            SseEmitterUtil.sendError(sseEmitter, "Current conversation window is unavailable");
            SseEmitterUtil.sendEndAndComplete(sseEmitter);
            return null;
        }

        Integer botId = chatList.getBotId();
        if (chatBotDataService.botIsDeleted(botId.longValue())) {
            log.warn("Current conversation window assistant has been deleted, sseId: {}, uid: {}, chatId: {}, botId: {}", sseId, uid, chatId, botId);
            SseEmitterUtil.sendError(sseEmitter, "Current conversation window assistant has been deleted");
            SseEmitterUtil.sendEndAndComplete(sseEmitter);
            return null;
        }

        // Re-answering requires validating the legitimacy of question ID
        if (requestId != null) {
            ChatReqRecords chatReqRecord = chatDataService.findRequestById(requestId);
            if (chatReqRecord == null) {
                log.warn("Record for re-answer request does not exist, sseId: {}, requestId: {}", sseId, requestId);
                SseEmitterUtil.sendError(sseEmitter, "Record for re-answer request does not exist");
                SseEmitterUtil.sendEndAndComplete(sseEmitter);
                return null;
            } else if (!chatReqRecord.getChatId().equals(chatId) || !chatReqRecord.getUid().equals(uid)) {
                log.warn("Record for re-answer request does not match, sseId: {}, uid: {}, chatId: {}, requestId: {}", sseId, uid, chatId, requestId);
                SseEmitterUtil.sendError(sseEmitter, "Record for re-answer request does not match");
                SseEmitterUtil.sendEndAndComplete(sseEmitter);
                return null;
            }
        }

        return new ChatContext(uid, chatId, botId);
    }

    /**
     * Method to process chat request
     *
     * @param chatContext Chat context object
     * @param text User input text
     * @param fileUrl File URL
     * @param sseEmitter SSE emitter object
     * @param sseId SSE unique identifier
     * @return Returns SseEmitter object
     */
    private SseEmitter processChatRequest(ChatContext chatContext, String text, String fileUrl, SseEmitter sseEmitter, String sseId, String workflowOperation, String workflowVersion) {
        log.info("Starting to process chat request, sseId: {}, uid: {}, chatId: {}, botId: {}, text: {}",
                sseId, chatContext.uid(), chatContext.chatId(), chatContext.botId(),
                text.length() > 50 ? text.substring(0, 50) + "..." : text);

        ChatBotReqDto chatBotReqDto = buildChatBotRequest(chatContext, text, fileUrl);

        try {
            sendStartSignal(sseEmitter, sseId, chatContext);
            botChatService.chatMessageBot(chatBotReqDto, sseEmitter, sseId, workflowOperation, workflowVersion);
            return sseEmitter;
        } catch (Exception e) {
            log.error("Bot chat error, sseId: {}, uid: {}, chatId: {}, botId: {}", sseId, chatContext.uid(), chatContext.chatId(), chatContext.botId(), e);
            SseEmitterUtil.completeWithError(sseEmitter, "Chat service exception: " + e.getMessage());
            return sseEmitter;
        }
    }

    /**
     * Build chat robot request object
     *
     * @param chatContext Chat context object
     * @param text User input text information
     * @param fileUrl File URL address
     * @return Built chat robot request object
     */
    private ChatBotReqDto buildChatBotRequest(ChatContext chatContext, String text, String fileUrl) {
        ChatBotReqDto chatBotReqDto = new ChatBotReqDto();
        chatBotReqDto.setAsk(text);
        chatBotReqDto.setUid(chatContext.uid());
        chatBotReqDto.setChatId(chatContext.chatId());
        chatBotReqDto.setBotId(chatContext.botId());
        chatBotReqDto.setUrl(fileUrl);
        chatBotReqDto.setEdit(false);
        return chatBotReqDto;
    }

    /**
     * Send start signal
     *
     * @param sseEmitter SseEmitter object, used to send events
     * @param sseId Unique ID identifying SSE session
     * @param chatContext Chat context object, containing chat-related information
     */
    private void sendStartSignal(SseEmitter sseEmitter, String sseId, ChatContext chatContext) {
        SseEmitterUtil.sendData(sseEmitter, Map.of(
                "type", "start",
                "sseId", sseId,
                "chatId", chatContext.chatId(),
                "botId", chatContext.botId(),
                "timestamp", System.currentTimeMillis()));
    }

    private record ValidationResult(boolean isValid) {
        static ValidationResult valid() { return new ValidationResult(true); }
        static ValidationResult invalid() { return new ValidationResult(false); }
    }

    private record ChatContext(String uid, Long chatId, Integer botId) {}

    /**
     * Stop SSE stream
     */
    @PostMapping("/stop")
    @Operation(summary = "Stop generation")
    public StopStreamResponse stopStream(@RequestParam String streamId) {
        log.info("Stopping SSE stream, sseId: {}", streamId);
        RTopic topic = redissonClient.getTopic(STOP_GENERATE_SUBSCRIBE_PUBLISH_CHANNEL);
        topic.publish(streamId);
        return StopStreamResponse.success(streamId);
    }

    /**
     * Subscribe method, used to subscribe to specified publish channel messages
     */
    @PostConstruct
    public void subscribe() {
        RTopic topic = redissonClient.getTopic(STOP_GENERATE_SUBSCRIBE_PUBLISH_CHANNEL);
        topic.addListener(String.class, (channel, msg) -> {
            SseEmitterUtil.stopStream(msg);
        });
    }

    /**
     * Regenerate conversation result
     */
    @PostMapping(path = "/reAnswer", produces = "text/event-stream;charset=UTF-8")
    @Operation(summary = "Regenerate conversation result")
    public SseEmitter reAnswer(@RequestParam Long chatId, @RequestParam Long requestId) {
        String sseId = RandomUtil.randomString(8);
        SseEmitter sseEmitter = SseEmitterUtil.createSseEmitter();

        log.info("Establishing SSE connection, sseId: {}, chatId: {}", sseId, chatId);

        // Get the latest chat_id
        List<ChatTreeIndex> chatTreeIndexList = chatListDataService.findChatTreeIndexByChatIdOrderById(chatId);
        if (chatTreeIndexList.isEmpty()) {
            log.warn("chatId is empty, sseId: {}", sseId);
            SseEmitterUtil.sendError(sseEmitter, "Chat ID cannot be empty");
            SseEmitterUtil.sendEndAndComplete(sseEmitter);
            return sseEmitter;
        }
        Long lastChatId = chatTreeIndexList.getFirst().getChildChatId();

        // Validate request parameters
        ValidationResult validation = validateReAnswerRequest(lastChatId, requestId, sseId, sseEmitter);
        if (!validation.isValid()) {
            return sseEmitter;
        }

        // Validate chat window and assistant status
        ChatContext chatContext = validateChatContext(lastChatId, requestId, sseId, sseEmitter);
        if (chatContext == null) {
            return sseEmitter;
        }

        return processReAnswerRequest(chatContext, requestId, sseEmitter, sseId);
    }

    /**
     * Validate the validity of re-answer request
     *
     * @param chatId Chat room ID
     * @param requestId Request ID
     * @param sseId Server-sent events ID
     * @param sseEmitter SSE emitter
     * @return ValidationResult Validation result
     */
    private ValidationResult validateReAnswerRequest(Long chatId, Long requestId, String sseId, SseEmitter sseEmitter) {
        if (chatId == null) {
            log.warn("chatId is empty, sseId: {}", sseId);
            SseEmitterUtil.sendError(sseEmitter, "Chat ID cannot be empty");
            SseEmitterUtil.sendEndAndComplete(sseEmitter);
            return ValidationResult.invalid();
        }

        if (requestId == null) {
            log.warn("requestId is empty, sseId: {}, chatId: {}", sseId, chatId);
            SseEmitterUtil.sendError(sseEmitter, "Request ID cannot be empty");
            SseEmitterUtil.sendEndAndComplete(sseEmitter);
            return ValidationResult.invalid();
        }

        return ValidationResult.valid();
    }

    /**
     * Method to process re-answer request
     *
     * @param chatContext Chat context object, containing chat-related information
     * @param requestId Unique identifier of the request
     * @param sseEmitter Server-sent events emitter, used to push events to client
     * @param sseId Server-sent events ID
     * @return Processed SseEmitter object
     */
    private SseEmitter processReAnswerRequest(ChatContext chatContext, Long requestId, SseEmitter sseEmitter, String sseId) {
        log.info("Starting to process re-answer request, sseId: {}, requestId: {}",
                sseId, requestId);

        try {
            sendStartSignal(sseEmitter, sseId, chatContext);
            botChatService.reAnswerMessageBot(requestId, chatContext.botId, sseEmitter, sseId);
            return sseEmitter;
        } catch (Exception e) {
            log.error("Bot chat error, sseId: {}, uid: {}, chatId: {}, botId: {}", sseId, chatContext.uid(), chatContext.chatId(), chatContext.botId(), e);
            SseEmitterUtil.completeWithError(sseEmitter, "Chat service exception: " + e.getMessage());
            return sseEmitter;
        }
    }

    /**
     * Bot single-step debugging chat interface
     */
    @PostMapping(path = "/bot-debug", produces = "text/event-stream;charset=UTF-8")
    @Operation(summary = "Bot single-step debugging chat interface")
    public SseEmitter botDebug(HttpServletRequest request, HttpServletResponse response, @ModelAttribute BotDebugRequest debugRequest) {
        String uid = RequestContextUtil.getUID();
        String sseId = RandomUtil.randomString(6);
        SseEmitter sseEmitter = SseEmitterUtil.createSseEmitter();

        log.info("Debug interface establishing SSE connection, sseId: {}", sseId);
        // Check if multi-turn conversation is selected
        if (debugRequest.getNeed() == 0) {
            debugRequest.setArr(new ArrayList<>());
        }

        try {
            sendStartSignal(sseEmitter, sseId, new ChatContext(uid, 0L, 0));
            botChatService.debugChatMessageBot(debugRequest.getText(), debugRequest.getPrompt(), debugRequest.getArr(),
                    uid, debugRequest.getOpenedTool(), debugRequest.getModel(), debugRequest.getModelId(), sseEmitter, sseId);
            return sseEmitter;
        } catch (Exception e) {
            log.error("Bot debug error, sseId: {}", sseId, e);
            SseEmitterUtil.completeWithError(sseEmitter, "Chat service exception: " + e.getMessage());
            return sseEmitter;
        }
    }

    /**
     * Clear chat history
     */
    @GetMapping(path = "/clear")
    @Operation(summary = "Clear chat history")
    public ApiResult<ChatListCreateResponse> clear(Integer botId, Long chatId) {
        String uid = RequestContextUtil.getUID();
        if (uid == null) {
            throw new BusinessException(ResponseEnum.LOGIN_INFO_ERROR);
        }
        if (chatId == null) {
            throw new BusinessException(ResponseEnum.PARAMS_ERROR);
        }
        ChatBotBase botBase = chatBotDataService.findById(botId).orElse(null);
        if (botBase == null) {
            throw new BusinessException(ResponseEnum.PARAMS_ERROR);
        }
        return ApiResult.success(botChatService.clear(chatId, uid, botId, botBase));
    }
}
