package com.iflytek.astra.console.hub.service.chat.impl;

import cn.hutool.core.collection.CollectionUtil;
import com.iflytek.astra.console.commons.constant.ResponseEnum;
import com.iflytek.astra.console.commons.entity.bot.ChatBotMarket;
import com.iflytek.astra.console.commons.entity.chat.ChatList;
import com.iflytek.astra.console.commons.entity.chat.ChatListCreateResponse;
import com.iflytek.astra.console.commons.exception.BusinessException;
import com.iflytek.astra.console.commons.service.bot.BotService;
import com.iflytek.astra.console.commons.service.bot.ChatBotDataService;
import com.iflytek.astra.console.commons.service.data.ChatDataService;
import com.iflytek.astra.console.commons.entity.bot.ChatBotReqDto;
import com.iflytek.astra.console.commons.dto.llm.SparkChatRequest;
import com.iflytek.astra.console.commons.entity.bot.ChatBotBase;
import com.iflytek.astra.console.commons.entity.chat.ChatReqRecords;
import com.iflytek.astra.console.commons.enums.bot.BotTypeEnum;
import com.iflytek.astra.console.commons.enums.ShelfStatusEnum;
import com.iflytek.astra.console.commons.service.data.ChatListDataService;
import com.iflytek.astra.console.commons.util.SseEmitterUtil;
import com.iflytek.astra.console.hub.service.SparkChatService;
import com.iflytek.astra.console.hub.service.chat.BotChatService;
import com.iflytek.astra.console.commons.service.data.ChatHistoryService;
import com.iflytek.astra.console.commons.service.workflow.WorkflowBotChatService;
import com.iflytek.astra.console.hub.service.chat.ChatListService;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

@Slf4j
@Service
public class BotChatServiceImpl implements BotChatService {

    @Autowired
    private ChatBotDataService chatBotDataService;

    @Autowired
    private ChatDataService chatDataService;

    @Autowired
    private SparkChatService sparkChatService;

    @Autowired
    private ChatHistoryService chatHistoryService;

    @Autowired
    private WorkflowBotChatService workflowBotChatService;

    @Value("${spark.chat.max.input.tokens:8000}")
    private int maxInputTokens;

    public static final String LOOSE_PREFIX_PROMPT = """

            Please use the following document fragments as known information:[]\
            Please answer questions accurately based on the original text of the above paragraphs and your knowledge
            When answering user questions, please answer in the language the user asked
            If the above content cannot answer the user's information, combine the information you know to answer the user's question
            Answer the user's questions concisely and professionally, do not allow fabricated components to be added to the answer.
            """;
    @Autowired
    private ChatListDataService chatListDataService;

    @Autowired
    private ChatListService chatListService;

    @Autowired
    private BotService botService;

    @Override
    public void chatMessageBot(ChatBotReqDto chatBotReqDto, SseEmitter sseEmitter, String sseId, String workflowOperation, String workflowVersion) {
        try {
            log.info("Processing chat request, sseId: {}, chatId: {}, uid: {}", sseId, chatBotReqDto.getChatId(), chatBotReqDto.getUid());

            BotConfiguration botConfig = getBotConfiguration(chatBotReqDto.getBotId());
            if (botConfig.version.equals(BotTypeEnum.WORKFLOW_BOT.getType())) {
                workflowBotChatService.chatWorkflowBot(chatBotReqDto, sseEmitter, sseId, workflowOperation, workflowVersion);
            } else {
                List<SparkChatRequest.MessageDto> messages = buildMessageList(chatBotReqDto, botConfig.supportContext, botConfig.prompt);
                ChatReqRecords chatReqRecords = createChatRequest(chatBotReqDto);
                SparkChatRequest sparkChatRequest = buildSparkChatRequest(chatBotReqDto, botConfig, messages);
                sparkChatService.chatStream(sparkChatRequest, sseEmitter, sseId, chatReqRecords, false, false);
            }
        } catch (Exception e) {
            log.error("Bot chat error for sseId: {}, chatId: {}, uid: {}", sseId, chatBotReqDto.getChatId(), chatBotReqDto.getUid(), e);
            SseEmitterUtil.completeWithError(sseEmitter, "Failed to process chat request: " + e.getMessage());
        }
    }

    @Override
    public void reAnswerMessageBot(Long requestId, Integer botId, SseEmitter sseEmitter, String sseId) {
        try {
            log.info("Processing re-answer request, sseId: {}, requestId: {}", sseId, requestId);

            ChatReqRecords chatReqRecords = chatDataService.findRequestById(requestId);
            BotConfiguration botConfig = getBotConfiguration(botId);
            ChatBotReqDto chatBotReqDto = new ChatBotReqDto();
            chatBotReqDto.setBotId(botId);
            chatBotReqDto.setChatId(chatReqRecords.getChatId());
            chatBotReqDto.setUid(chatReqRecords.getUid());
            chatBotReqDto.setAsk(chatReqRecords.getMessage());
            chatBotReqDto.setEdit(true);
            List<SparkChatRequest.MessageDto> messages = buildMessageList(chatBotReqDto, botConfig.supportContext, botConfig.prompt);
            SparkChatRequest sparkChatRequest = buildSparkChatRequest(chatBotReqDto, botConfig, messages);

            sparkChatService.chatStream(sparkChatRequest, sseEmitter, sseId, chatReqRecords, chatBotReqDto.getEdit(), false);
        } catch (Exception e) {
            log.error("Bot reAnswer error for sseId: {}, requestId: {}", sseId, requestId, e);
            SseEmitterUtil.completeWithError(sseEmitter, "Failed to process re-answer request: " + e.getMessage());
        }
    }

    @Override
    public void debugChatMessageBot(String text, String prompt, List<String> messages, String uid, String openedTool, String model, SseEmitter sseEmitter, String sseId) {
        // 1. Create system message (must be kept)
        SparkChatRequest.MessageDto systemMessage = new SparkChatRequest.MessageDto();
        systemMessage.setRole("system");
        systemMessage.setContent(prompt);
        // 2. Create current user message (must be kept)
        SparkChatRequest.MessageDto queryMessage = new SparkChatRequest.MessageDto();
        queryMessage.setRole("user");
        queryMessage.setContent(text);
        // 3. Build history messages
        List<SparkChatRequest.MessageDto> historyMessages = new ArrayList<>();
        int index = 0;
        for (String message : messages) {
            SparkChatRequest.MessageDto messageDto = new SparkChatRequest.MessageDto();
            messageDto.setRole(index % 2 == 0 ? "user" : "assistant");
            messageDto.setContent(message);
            historyMessages.add(messageDto);
            index++;
        }
        // Integrate message list
        List<SparkChatRequest.MessageDto> messageList = new ArrayList<>();
        messageList.add(systemMessage);
        messageList.addAll(historyMessages);
        messageList.add(queryMessage);
        // 4. Build request object
        SparkChatRequest sparkChatRequest = new SparkChatRequest();
        sparkChatRequest.setModel(model);
        sparkChatRequest.setMessages(messageList);
        sparkChatRequest.setChatId(null);
        sparkChatRequest.setUserId(uid);
        sparkChatRequest.setEnableWebSearch(enableWebSearch(openedTool));
        sparkChatService.chatStream(sparkChatRequest, sseEmitter, sseId, null, false, true);
    }

    @Override
    @Transactional
    public ChatListCreateResponse clear(Long chatId, String uid, Integer botId, ChatBotBase botBase) {
        // Check if this is the user's own assistant chat window
        ChatList chatList = chatListDataService.findByUidAndChatId(uid, chatId);
        if (chatList == null || !Objects.equals(chatList.getBotId(), botId)) {
            return new ChatListCreateResponse();
        }
        // Check if this window has chat history, if not, use this blank window directly
        if (chatDataService.countMessagesByChatId(chatId) == 0) {
            ChatListCreateResponse response = new ChatListCreateResponse();
            response.setId(chatId);
            response.setTitle(chatList.getTitle());
            response.setBotId(botId);
            response.setCreateTime(chatList.getCreateTime());
            return response;
        }
        // Delete window
        chatListService.logicDeleteChatList(chatId, uid);
        // Add new window based on botId
        ChatListCreateResponse chatListCreateResponse = chatListService.createRestartChat(uid, "", botId);
        // Add assistant to user's chat_bot_list
        if (!Objects.equals(botBase.getUid(), uid)) {
            botService.addV2Bot(uid, botId);
        }

        return chatListCreateResponse;
    }

    /**
     * Get bot configuration
     *
     * @param botId Bot ID
     * @return Returns BotConfiguration object
     */
    private BotConfiguration getBotConfiguration(Integer botId) {
        ChatBotMarket chatBotMarket = chatBotDataService.findMarketBotByBotId(botId);

        if (chatBotMarket != null && ShelfStatusEnum.isOnShelf(chatBotMarket.getBotStatus())) {
            return new BotConfiguration(
                    chatBotMarket.getPrompt(),
                    chatBotMarket.getSupportContext() == 1,
                    chatBotMarket.getModel(),
                    chatBotMarket.getOpenedTool(),
                    chatBotMarket.getVersion());
        } else {
            ChatBotBase chatBotBase = chatBotDataService.findById(botId)
                    .orElseThrow(() -> new BusinessException(ResponseEnum.BOT_NOT_EXISTS));
            return new BotConfiguration(
                    chatBotBase.getPrompt(),
                    chatBotBase.getSupportContext() == 1,
                    chatBotBase.getModel(),
                    chatBotBase.getOpenedTool(),
                    chatBotBase.getVersion());
        }
    }

    /**
     * Create chat request record
     *
     * @param chatBotReqDto Chat bot request data transfer object
     * @return Generated chat request record
     */
    private ChatReqRecords createChatRequest(ChatBotReqDto chatBotReqDto) {
        ChatReqRecords chatReqRecords = new ChatReqRecords();
        chatReqRecords.setChatId(chatBotReqDto.getChatId());
        chatReqRecords.setUid(chatBotReqDto.getUid());
        chatReqRecords.setMessage(chatBotReqDto.getAsk());
        chatReqRecords.setClientType(0);
        chatReqRecords.setCreateTime(LocalDateTime.now());
        chatReqRecords.setUpdateTime(LocalDateTime.now());
        chatReqRecords.setNewContext(1);
        chatDataService.createRequest(chatReqRecords);
        return chatReqRecords;
    }

    /**
     * Build message list, truncate historical conversation data based on maximum input tokens
     *
     * @param chatBotReqDto Chat bot request data transfer object
     * @param supportContext Whether to support context
     * @param prompt Prompt text
     * @return List of message data transfer objects
     */
    private List<SparkChatRequest.MessageDto> buildMessageList(ChatBotReqDto chatBotReqDto, boolean supportContext, String prompt) {
        List<SparkChatRequest.MessageDto> messageDtoList = new ArrayList<>();

        // 1. Create system message (must be kept)
        SparkChatRequest.MessageDto systemMessage = new SparkChatRequest.MessageDto();
        systemMessage.setRole("system");
        systemMessage.setContent(prompt);

        // 2. Create current user message (must be kept)
        SparkChatRequest.MessageDto queryMessage = new SparkChatRequest.MessageDto();
        queryMessage.setRole("user");
        queryMessage.setContent(chatBotReqDto.getAsk());

        // 3. Calculate token count for system message and current user message
        int systemTokens = estimateTokenCount(prompt);
        int currentUserTokens = estimateTokenCount(chatBotReqDto.getAsk());
        int reservedTokens = systemTokens + currentUserTokens;

        log.debug("Token statistics - System message: {}, Current user message: {}, Reserved total: {}, Maximum limit: {}",
                systemTokens, currentUserTokens, reservedTokens, maxInputTokens);

        // 4. Add system message to the top of the list
        messageDtoList.add(systemMessage);

        // 5. If supporting context, add historical messages (truncated by token limit)
        if (supportContext && reservedTokens < maxInputTokens) {
            List<SparkChatRequest.MessageDto> historyMessages = chatHistoryService.getSystemBotHistory(chatBotReqDto.getUid(), chatBotReqDto.getChatId());
            // If it's a re-answer request, need to remove the last Q&A pair
            if (chatBotReqDto.getEdit()) {
                historyMessages.removeLast();
                historyMessages.removeLast();
            }

            if (!historyMessages.isEmpty()) {
                List<SparkChatRequest.MessageDto> truncatedHistory = truncateHistoryByTokens(historyMessages, maxInputTokens - reservedTokens);
                messageDtoList.addAll(truncatedHistory);

                log.debug("History message truncation completed - Original count: {}, After truncation: {}",
                        historyMessages.size(), truncatedHistory.size());
            }
        }

        // 6. Add current user message to the end of the list
        messageDtoList.add(queryMessage);

        // 7. Output final statistics
        int totalTokens = messageDtoList.stream()
                .mapToInt(msg -> estimateTokenCount(msg.getContent()))
                .sum();

        log.info("Message list build completed - Total messages: {}, Estimated total tokens: {}, Maximum limit: {}",
                messageDtoList.size(), totalTokens, maxInputTokens);

        return messageDtoList;
    }

    /**
     * Truncate history messages based on token limit, trim from front to back
     *
     * @param historyMessages History message list
     * @param maxHistoryTokens Maximum token count for history messages
     * @return Truncated history message list
     */
    private List<SparkChatRequest.MessageDto> truncateHistoryByTokens(List<SparkChatRequest.MessageDto> historyMessages, int maxHistoryTokens) {
        List<SparkChatRequest.MessageDto> result = new ArrayList<>();
        int currentTokens = 0;

        // Traverse from back to front (keep the newest conversations)
        for (int i = historyMessages.size() - 1; i >= 0; i--) {
            SparkChatRequest.MessageDto message = historyMessages.get(i);
            int messageTokens = estimateTokenCount(message.getContent());

            // Check if token limit is exceeded
            if (currentTokens + messageTokens > maxHistoryTokens) {
                log.debug("History message truncation - Stopped at index {}, current tokens: {}, message tokens: {}, limit: {}",
                        i, currentTokens, messageTokens, maxHistoryTokens);
                break;
            }

            currentTokens += messageTokens;
            result.addFirst(message); // Add to the beginning of the list, maintain time order
        }

        return result;
    }

    /**
     * Estimate token count for text (simple estimation: Chinese characters * 1.5, English words * 1.3)
     *
     * @param text Text content
     * @return Estimated token count
     */
    private int estimateTokenCount(String text) {
        if (StringUtils.isBlank(text)) {
            return 0;
        }

        // Simple estimation method:
        // - Chinese characters calculated as 1.5 tokens
        // - English characters calculated as 1.3 tokens (considering word segmentation)
        int chineseChars = 0;
        int englishChars = 0;

        for (char c : text.toCharArray()) {
            if (c >= 0x4e00 && c <= 0x9fff) {
                // Chinese character range
                chineseChars++;
            } else if (Character.isLetterOrDigit(c)) {
                // English characters and numbers
                englishChars++;
            }
        }

        int estimatedTokens = (int) (chineseChars * 1.5 + englishChars * 1.3);

        log.trace("Token estimation - Chinese characters: {}, English characters: {}, Estimated tokens: {}",
                chineseChars, englishChars, estimatedTokens);

        return Math.max(estimatedTokens, 1); // At least 1 token
    }

    /**
     * Utility method to build SparkChatRequest object
     *
     * @param chatBotReqDto Chat bot request data transfer object
     * @param botConfig Bot configuration
     * @param messages List of message data transfer objects
     * @return Built SparkChatRequest object
     */
    private SparkChatRequest buildSparkChatRequest(ChatBotReqDto chatBotReqDto, BotConfiguration botConfig, List<SparkChatRequest.MessageDto> messages) {
        SparkChatRequest sparkChatRequest = new SparkChatRequest();
        sparkChatRequest.setModel(botConfig.model);
        sparkChatRequest.setMessages(messages);
        sparkChatRequest.setChatId(chatBotReqDto.getChatId().toString());
        sparkChatRequest.setUserId(chatBotReqDto.getUid().toString());
        sparkChatRequest.setEnableWebSearch(enableWebSearch(botConfig.openedTool));
        return sparkChatRequest;
    }

    private record BotConfiguration(String prompt, boolean supportContext, String model, String openedTool, Integer version) {}

    /**
     * Determine whether to enable web search
     */
    private boolean enableWebSearch(String tool) {
        if (StringUtils.isBlank(tool)) {
            return false;
        }
        List<String> tools = CollectionUtil.newArrayList(Objects.requireNonNull(tool).split(","));
        return tools.contains("ifly_search");
    }
}
