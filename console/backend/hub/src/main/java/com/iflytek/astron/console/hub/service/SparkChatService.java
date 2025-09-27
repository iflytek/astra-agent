package com.iflytek.astron.console.hub.service;

import cn.xfyun.api.SparkChatClient;
import cn.xfyun.config.SparkModel;
import cn.xfyun.model.sparkmodel.RoleContent;
import cn.xfyun.model.sparkmodel.SparkChatParam;
import cn.xfyun.model.sparkmodel.WebSearch;
import cn.xfyun.model.sparkmodel.response.SparkChatResponse;
import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import com.iflytek.astron.console.commons.service.data.ChatDataService;
import com.iflytek.astron.console.commons.dto.llm.SparkChatRequest;
import com.iflytek.astron.console.commons.entity.chat.ChatReqRecords;
import com.iflytek.astron.console.commons.entity.chat.ChatTraceSource;
import com.iflytek.astron.console.commons.service.ChatRecordModelService;
import com.iflytek.astron.console.commons.util.SseEmitterUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import okhttp3.*;
import okio.BufferedSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class SparkChatService {

    @Value("${spark.api.password}")
    private String apiPassword;

    @Autowired
    private ChatDataService chatDataService;

    @Autowired
    private ChatRecordModelService chatRecordModelService;

    public SseEmitter chatStream(SparkChatRequest request) {
        SseEmitter emitter = SseEmitterUtil.createSseEmitter();
        String streamId = request.getChatId() + "_" + request.getUserId() + "_" + System.currentTimeMillis();
        chatStream(request, emitter, streamId, null, false, false);
        return emitter;
    }

    /**
     * Function to handle chat stream requests
     *
     * @param request HTTP request object
     * @param emitter Server-Sent Events (SSE) emitter
     * @param streamId Stream identifier
     * @param chatReqRecords Chat request records
     */
    public void chatStream(SparkChatRequest request, SseEmitter emitter, String streamId, ChatReqRecords chatReqRecords, boolean edit, boolean isDebug) {
        if (!isDebug && (chatReqRecords == null || chatReqRecords.getUid() == null || chatReqRecords.getChatId() == null)) {
            SseEmitterUtil.completeWithError(emitter, "Message is empty");
            return;
        }
        try {
            SparkModel sparkModel = getSparkModel(request.getModel());
            // todo: Connection pool configuration
            SparkChatClient client = new SparkChatClient.Builder().signatureHttp(apiPassword, sparkModel).build();

            SparkChatParam sendParam = buildSparkChatParam(request);
            log.info("request:{}", request);

            client.send(sendParam, new Callback() {
                /**
                 * Callback method when SSE connection fails
                 *
                 * @param call Current Call object
                 * @param e IOException exception thrown
                 */
                @Override
                public void onFailure(Call call, IOException e) {
                    log.error("SSE connection failed, streamId: {}, error: {}", streamId, e.getMessage());
                    SseEmitterUtil.completeWithError(emitter, "Connection failed: " + e.getMessage());
                }

                /**
                 * Response callback method
                 *
                 * @param call HTTP call object
                 * @param response HTTP response object
                 */
                @Override
                public void onResponse(Call call, Response response) {
                    if (!response.isSuccessful()) {
                        log.error("Request failed, streamId: {}, status code: {}, reason: {}", streamId, response.code(), response.message());
                        SseEmitterUtil.completeWithError(emitter, "Request failed: " + response.message());
                        return;
                    }

                    ResponseBody body = response.body();
                    if (body != null) {
                        processSSEStream(body, emitter, streamId, chatReqRecords, edit, isDebug);
                    } else {
                        SseEmitterUtil.completeWithError(emitter, "Response body is empty");
                    }
                }
            });

        } catch (Exception e) {
            log.error("Exception occurred while creating Spark chat stream, streamId: {}", streamId, e);
            SseEmitterUtil.completeWithError(emitter, "Failed to create chat stream: " + e.getMessage());
        }
    }

    /**
     * Get SparkModel based on the input model name string.
     *
     * @param model Model name string, can be null.
     * @return SparkModel Enum value matched based on model name.
     */
    private SparkModel getSparkModel(String model) {
        if (model == null) {
            return SparkModel.SPARK_X1;
        }

        return switch (model.toLowerCase()) {
            case "spark" -> SparkModel.SPARK_4_0_ULTRA;
            default -> SparkModel.SPARK_X1;
        };
    }

    /**
     * Build SparkChatParam object based on SparkChatRequest
     *
     * @param request Input SparkChatRequest object
     * @return Built SparkChatParam object
     */
    private SparkChatParam buildSparkChatParam(SparkChatRequest request) {
        List<RoleContent> messages = request.getMessages().stream().map(msg -> {
            RoleContent roleContent = new RoleContent();
            roleContent.setRole(msg.getRole());
            roleContent.setContent(msg.getContent());
            return roleContent;
        }).collect(Collectors.toList());

        SparkChatParam sparkChatParam = new SparkChatParam();
        sparkChatParam.setMessages(messages);
        sparkChatParam.setChatId(request.getChatId());
        sparkChatParam.setUserId(request.getUserId());

        if (request.getEnableWebSearch() != null && request.getEnableWebSearch()) {
            WebSearch webSearch = new WebSearch();
            webSearch.setEnable(true);
            webSearch.setSearchMode(request.getSearchMode());
            webSearch.setShowRefLabel(request.getShowRefLabel());
            sparkChatParam.setWebSearch(webSearch);
        }

        return sparkChatParam;
    }

    /**
     * Process Server-Sent Events (SSE) stream.
     *
     * @param body HTTP response body containing SSE stream data
     * @param emitter SseEmitter object for sending events to client
     * @param streamId Unique identifier of the stream being processed
     * @param chatReqRecords Chat request records object
     */
    private void processSSEStream(ResponseBody body, SseEmitter emitter, String streamId, ChatReqRecords chatReqRecords, boolean edit, boolean isDebug) {
        BufferedSource source = body.source();
        StringBuffer finalResult = new StringBuffer();
        StringBuffer thinkingResult = new StringBuffer();
        // Use StringBuffer as mutable container, ensure assignment only once
        StringBuffer sid = new StringBuffer();
        StringBuffer traceResult = new StringBuffer();

        try (body) {
            try {
                while (true) {
                    // Check if stop signal is received
                    if (SseEmitterUtil.isStreamStopped(streamId)) {
                        log.info("Stop signal detected, saving collected data, streamId: {}", streamId);
                        handleStreamInterrupted(emitter, streamId, finalResult, thinkingResult, chatReqRecords, sid, traceResult, edit, isDebug);
                        break;
                    }

                    String line = source.readUtf8Line();
                    if (line == null) {
                        break;
                    }

                    if (line.startsWith("data:")) {
                        if (line.contains("[DONE]")) {
                            handleStreamComplete(emitter, streamId, finalResult, thinkingResult, chatReqRecords, sid, traceResult, edit, isDebug);
                            break;
                        }

                        String data = line.substring(5).trim();
                        parseSSEContent(data, emitter, streamId, finalResult, thinkingResult, sid, traceResult);

                        // Check stop signal again after processing each data
                        if (SseEmitterUtil.isStreamStopped(streamId)) {
                            log.info("Stop signal detected after processing data, saving collected data, streamId: {}", streamId);
                            handleStreamInterrupted(emitter, streamId, finalResult, thinkingResult, chatReqRecords, sid, traceResult, edit, isDebug);
                            break;
                        }
                    }
                }
            } catch (IOException e) {
                log.error("Exception reading SSE stream data, saving collected data, streamId: {}", streamId, e);
                // Save collected data even when exception occurs
                handleStreamInterrupted(emitter, streamId, finalResult, thinkingResult, chatReqRecords, sid, traceResult, edit, isDebug);
                SseEmitterUtil.completeWithError(emitter, "Data reading exception: " + e.getMessage());
            }
        } catch (Exception e) {
            log.warn("Exception closing response body, streamId: {}", streamId, e);
            // Save collected data when exception occurs
            handleStreamInterrupted(emitter, streamId, finalResult, thinkingResult, chatReqRecords, sid, traceResult, edit, isDebug);
        }
    }

    /**
     * Parse SSE content and process data
     *
     * @param data SSE data string to be parsed
     * @param emitter SseEmitter object for sending data to client
     * @param streamId Stream identifier
     * @param finalResult Final result StringBuffer object
     * @param thinkingResult Thinking process result StringBuffer object
     * @param sid Session identifier StringBuffer object
     * @param traceResult Trace result StringBuffer object
     */
    private void parseSSEContent(String data, SseEmitter emitter, String streamId, StringBuffer finalResult, StringBuffer thinkingResult, StringBuffer sid, StringBuffer traceResult) {
        log.debug("SSE data streamId: {} ==> {}", streamId, data);

        try {
            JSONObject dataObj = JSON.parseObject(data);

            if (dataObj.getInteger("code") != 0) {
                Integer code = dataObj.getInteger("code");
                log.error("SSE data contains error code, streamId: {}, code: {}, message: {}", streamId, code, dataObj.getString("message"));
                String fallbackMessage = getFallbackMessage(code);
                finalResult.append(fallbackMessage);
            }

            // Add deskToolName field for Web search tool calls
            addDeskToolNameForWebSearch(dataObj);

            // Try to send data, continue processing data even if client disconnects
            boolean clientConnected = tryServeSSEData(emitter, dataObj, streamId);

            // Process and save data regardless of client connection status
            processSidValue(dataObj, sid, streamId);
            processChoicesData(dataObj, finalResult, thinkingResult, traceResult, streamId);

            if (!clientConnected) {
                log.info("Client disconnected, but continue processing data to ensure completeness, streamId: {}", streamId);
            }
        } catch (Exception e) {
            handleParseError(e, data, streamId, emitter);
        }
    }

    /**
     * Try to send SSE data, detect client connection status
     *
     * @param emitter SseEmitter object
     * @param dataObj Data object to be sent
     * @param streamId Stream identifier
     * @return true if client is still connected, false if client has disconnected
     */
    private boolean tryServeSSEData(SseEmitter emitter, JSONObject dataObj, String streamId) {
        if (emitter == null) {
            log.warn("SseEmitter is null, cannot send data, streamId: {}", streamId);
            return false;
        }

        try {
            String jsonData = dataObj.toJSONString();
            emitter.send(SseEmitter.event().name("data").data(jsonData));
            return true;
        } catch (org.springframework.web.context.request.async.AsyncRequestNotUsableException e) {
            log.warn("Client connection disconnected, streamId: {}, continue background data processing", streamId);
            return false;
        } catch (IOException e) {
            log.error("Failed to send SSE data, streamId: {}, error: {}", streamId, e.getMessage());
            return false;
        } catch (IllegalStateException e) {
            log.debug("SseEmitter completed, streamId: {}", streamId);
            return false;
        } catch (Exception e) {
            log.error("Unexpected error occurred while sending SSE data, streamId: {}", streamId, e);
            return false;
        }
    }

    /**
     * Add deskToolName field for Web search tool calls
     *
     * @param dataObj SSE data JSON object
     */
    private void addDeskToolNameForWebSearch(JSONObject dataObj) {
        if (!dataObj.containsKey("choices")) {
            return;
        }

        JSONArray choices = dataObj.getJSONArray("choices");
        for (int i = 0; i < choices.size(); i++) {
            JSONObject choice = choices.getJSONObject(i);
            if (!choice.containsKey("delta")) {
                continue;
            }

            JSONObject delta = choice.getJSONObject("delta");
            if (!delta.containsKey("tool_calls")) {
                continue;
            }

            JSONArray toolCalls = delta.getJSONArray("tool_calls");
            for (int j = 0; j < toolCalls.size(); j++) {
                JSONObject toolCall = toolCalls.getJSONObject(j);
                if (isWebSearchToolCall(toolCall)) {
                    // Add deskToolName field for Web search tool calls
                    toolCall.put("deskToolName", "Web Search");
                    log.debug("Added deskToolName field for Web search tool call: Web Search");
                }
            }
        }
    }

    /**
     * Function to process SID value
     *
     * @param dataObj JSON object containing SID
     * @param sid StringBuffer for storing SID
     * @param streamId Stream ID
     */
    private void processSidValue(JSONObject dataObj, StringBuffer sid, String streamId) {
        if (sid.isEmpty() && dataObj.containsKey("sid")) {
            String sidValue = dataObj.getString("sid");
            if (sidValue != null && !sidValue.trim().isEmpty()) {
                sid.append(sidValue);
                log.debug("Set sid: {}, streamId: {}", sidValue, streamId);
            }
        }
    }

    /**
     * Function to process choices
     *
     * @param dataObj JSON object containing choices
     * @param finalResult StringBuffer for storing final result
     * @param thinkingResult StringBuffer for storing thinking process
     * @param traceResult StringBuffer for storing trace information
     * @param streamId ID for identifying the stream
     */
    private void processChoicesData(JSONObject dataObj, StringBuffer finalResult, StringBuffer thinkingResult, StringBuffer traceResult, String streamId) {
        if (!dataObj.containsKey("choices")) {
            return;
        }

        JSONArray choices = dataObj.getJSONArray("choices");
        if (choices.isEmpty()) {
            return;
        }

        processFirstChoice(choices, finalResult, thinkingResult);
        processSecondChoiceForTracing(choices, traceResult, streamId);
    }

    /**
     * Process output and thinking results
     *
     * @param choices JSONArray object representing collection of choices
     * @param finalResult StringBuffer object for storing final result
     * @param thinkingResult StringBuffer object for storing thinking process result
     */
    private void processFirstChoice(JSONArray choices, StringBuffer finalResult, StringBuffer thinkingResult) {
        JSONObject firstChoice = choices.getJSONObject(0);
        if (!firstChoice.containsKey("delta")) {
            return;
        }

        JSONObject delta = firstChoice.getJSONObject("delta");
        if (delta.containsKey("content")) {
            finalResult.append(delta.getString("content"));
        }
        if (delta.containsKey("reasoning_content")) {
            thinkingResult.append(delta.getString("reasoning_content"));
        }
    }

    /**
     * Process trace results
     *
     * @param choices JSONArray object containing multiple choice items
     * @param traceResult StringBuffer object for storing trace results
     * @param streamId String representing stream ID
     */
    private void processSecondChoiceForTracing(JSONArray choices, StringBuffer traceResult, String streamId) {
        if (choices.size() <= 1) {
            return;
        }

        JSONObject secondChoice = choices.getJSONObject(1);
        if (secondChoice == null || !secondChoice.containsKey("delta")) {
            return;
        }

        JSONObject delta = secondChoice.getJSONObject("delta");
        if (!delta.containsKey("tool_calls")) {
            return;
        }

        // Save entire tool_calls field content as trace data
        saveCompleteToolCalls(delta.getJSONArray("tool_calls"), traceResult, streamId);
    }

    /**
     * Save complete tool_calls content as trace data
     *
     * @param toolCalls JSONArray containing tool calls
     * @param traceResult StringBuffer for storing processing results
     * @param streamId ID identifying the stream
     */
    private void saveCompleteToolCalls(JSONArray toolCalls, StringBuffer traceResult, String streamId) {
        if (toolCalls == null || toolCalls.isEmpty()) {
            return;
        }

        // Save entire tool_calls array as trace data
        if (!traceResult.isEmpty()) {
            traceResult.append(",");
        }
        traceResult.append(toolCalls.toJSONString());
        log.debug("Save complete tool_calls trace data, streamId: {}, toolCallsCount: {}", streamId, toolCalls.size());
    }

    /**
     * Check if it's a Web search tool call.
     *
     * @param toolCall JSON object representing tool call information.
     * @return Returns true if toolCall type is "web_search" and contains "web_search" key; otherwise
     *         returns false.
     */
    private boolean isWebSearchToolCall(JSONObject toolCall) {
        return "web_search".equals(toolCall.getString("type")) && toolCall.containsKey("web_search");
    }

    /**
     * Method to handle parsing errors
     *
     * @param e Exception thrown
     * @param data Data that caused the error
     * @param streamId Stream ID
     * @param emitter SseEmitter object for sending events
     */
    private void handleParseError(Exception e, String data, String streamId, SseEmitter emitter) {
        log.error("Exception parsing SSE data, streamId: {}", streamId, e);
        log.error("Exception data: {}", data);

        SparkChatResponse errorResponse = createErrorResponse(e);
        SseEmitterUtil.sendData(emitter, errorResponse);
    }

    /**
     * Get fallback message based on error code
     *
     * @param code Error code
     * @return Fallback message
     */
    private String getFallbackMessage(Integer code) {
        if (code == null) {
            return "Service exception, please try again later";
        }

        return switch (code) {
            case 10007 -> "User traffic limited: Service is processing user's current request, please wait for completion before sending new requests.";
            case 10013 -> "Input content audit failed, suspected violation, please readjust input content";
            case 10014 -> "Output content involves sensitive information, audit failed";
            case 10019 -> "This conversation content has a tendency to involve violation information";
            case 10907 -> "Token count exceeds limit";
            case 11200 -> "Authorization error: This appId does not have authorization for related functions or business volume exceeds limit";
            case 11201 -> "Authorization error: Daily flow control exceeded. Exceeded daily maximum access limit";
            case 11202 -> "Authorization error: Second-level flow control exceeded. Second-level concurrency exceeds authorized path limit";
            case 11203 -> "Authorization error: Concurrent flow control exceeded. Concurrent paths exceed authorized path limit";
            default -> "Service exception, please try again later";
        };
    }

    /**
     * Create error response object
     *
     * @param e Input exception object
     * @return SparkChatResponse object containing error information
     */
    private SparkChatResponse createErrorResponse(Exception e) {
        SparkChatResponse errorResponse = new SparkChatResponse(-1, "Parsing exception");
        SparkChatResponse.Header errorHeader = new SparkChatResponse.Header();
        errorHeader.setCode(-1);
        errorHeader.setMessage("Data parsing exception: " + e.getMessage());
        errorHeader.setSid("");
        errorHeader.setStatus(2);
        errorResponse.setHeader(errorHeader);
        return errorResponse;
    }


    /**
     * Handle stream completion function
     *
     * @param emitter SseEmitter object for sending events
     * @param streamId Unique identifier of the stream
     * @param finalResult StringBuffer object of final result
     * @param thinkingResult StringBuffer object of thinking process
     * @param chatReqRecords Chat request records object
     * @param sid StringBuffer object of session ID
     * @param traceResult StringBuffer object of trace result
     */
    private void handleStreamComplete(SseEmitter emitter, String streamId, StringBuffer finalResult, StringBuffer thinkingResult, ChatReqRecords chatReqRecords, StringBuffer sid, StringBuffer traceResult, boolean edit, boolean isDebug) {
        log.info("Stream completed for streamId: {}", streamId);

        // Save data to database first to ensure data is not lost
        if (!isDebug) {
            saveStreamResultsToDatabase(chatReqRecords, finalResult, thinkingResult, sid, traceResult, edit);
        }

        // Build completion data and try to send to client (if still connected)
        JSONObject completeData = buildCompleteData(finalResult, thinkingResult, traceResult, chatReqRecords);
        trySendCompleteAndEnd(emitter, completeData, streamId);
    }

    /**
     * Handle stream interruption function - save collected data
     *
     * @param emitter SseEmitter object for sending events
     * @param streamId Unique identifier of the stream
     * @param finalResult StringBuffer object of final result
     * @param thinkingResult StringBuffer object of thinking process
     * @param chatReqRecords Chat request records object
     * @param sid StringBuffer object of session ID
     * @param traceResult StringBuffer object of trace result
     */
    private void handleStreamInterrupted(SseEmitter emitter, String streamId, StringBuffer finalResult, StringBuffer thinkingResult, ChatReqRecords chatReqRecords, StringBuffer sid, StringBuffer traceResult, boolean edit, boolean isDebug) {
        log.info("Stream interrupted for streamId: {}, saving collected data", streamId);

        // Save collected data to database first to ensure data is not lost
        if (!isDebug) {
            saveStreamResultsToDatabase(chatReqRecords, finalResult, thinkingResult, sid, traceResult, edit);
        }

        // Build interrupted completion data and try to send to client (if still connected)
        JSONObject interruptedData = buildCompleteData(finalResult, thinkingResult, traceResult, chatReqRecords);
        interruptedData.put("interrupted", true);
        interruptedData.put("reason", "Stream interrupted or client disconnected");

        trySendCompleteAndEnd(emitter, interruptedData, streamId);

        log.info("Saved data at interruption, streamId: {}, finalResult length: {}, thinkingResult length: {}, traceResult length: {}",
                streamId, finalResult.length(), thinkingResult.length(), traceResult.length());
    }

    /**
     * Try to send completion signal and end SSE connection
     *
     * @param emitter SseEmitter object
     * @param completeData Completion data
     * @param streamId Stream identifier
     */
    private void trySendCompleteAndEnd(SseEmitter emitter, JSONObject completeData, String streamId) {
        if (emitter == null) {
            log.warn("SseEmitter is null, cannot send completion signal, streamId: {}", streamId);
            return;
        }

        try {
            // Try to send completion data
            emitter.send(SseEmitter.event().name("complete").data(completeData.toJSONString()));
            log.debug("Successfully sent completion data, streamId: {}", streamId);
        } catch (org.springframework.web.context.request.async.AsyncRequestNotUsableException e) {
            log.info("Client connection disconnected, cannot send completion data, but data has been saved, streamId: {}", streamId);
        } catch (Exception e) {
            log.warn("Failed to send completion data, but data has been saved, streamId: {}, error: {}", streamId, e.getMessage());
        }

        try {
            // Try to send end signal and complete connection
            String endData = "{\"end\":true,\"timestamp\":" + System.currentTimeMillis() + "}";
            emitter.send(SseEmitter.event().name("end").data(endData));
            emitter.complete();
            log.debug("SSE connection ended normally, streamId: {}", streamId);
        } catch (org.springframework.web.context.request.async.AsyncRequestNotUsableException e) {
            log.info("Client connection disconnected, cannot send end signal, streamId: {}", streamId);
        } catch (IllegalStateException e) {
            log.debug("SseEmitter completed, streamId: {}", streamId);
        } catch (Exception e) {
            log.warn("Exception occurred while ending SSE connection, streamId: {}, error: {}", streamId, e.getMessage());
        }
    }

    /**
     * Build complete data JSON object
     *
     * @param finalResult StringBuffer of final result
     * @param thinkingResult StringBuffer of thinking process
     * @param traceResult StringBuffer of trace result
     * @param chatReqRecords Chat request records object
     * @return JSONObject containing complete data
     */
    private JSONObject buildCompleteData(StringBuffer finalResult, StringBuffer thinkingResult, StringBuffer traceResult, ChatReqRecords chatReqRecords) {
        JSONObject completeData = new JSONObject();
        completeData.put("finalResult", finalResult.toString());
        completeData.put("thinkingResult", thinkingResult.toString());
        completeData.put("traceResult", traceResult.toString());
        completeData.put("timestamp", System.currentTimeMillis());

        if (chatReqRecords != null) {
            completeData.put("chatId", chatReqRecords.getChatId());
            completeData.put("requestId", chatReqRecords.getId());
        }

        return completeData;
    }

    /**
     * Save stream results to database
     *
     * @param chatReqRecords Chat request records object
     * @param finalResult StringBuffer of final result
     * @param thinkingResult StringBuffer of thinking process
     * @param sid StringBuffer of session ID
     * @param traceResult StringBuffer of trace result
     */
    private void saveStreamResultsToDatabase(ChatReqRecords chatReqRecords, StringBuffer finalResult, StringBuffer thinkingResult, StringBuffer sid, StringBuffer traceResult, boolean edit) {
        if (chatReqRecords == null) {
            return;
        }

        chatRecordModelService.saveChatResponse(chatReqRecords, finalResult, sid, edit, 2);
        chatRecordModelService.saveThinkingResult(chatReqRecords, thinkingResult, edit);
        saveTraceResult(chatReqRecords, traceResult, edit);
    }

    /**
     * Function to save trace results
     *
     * @param chatReqRecords Chat request records object
     * @param traceResult StringBuffer object storing trace results
     * @param edit Whether in edit mode
     */
    private void saveTraceResult(ChatReqRecords chatReqRecords, StringBuffer traceResult, boolean edit) {
        if (traceResult.isEmpty()) {
            return;
        }

        java.time.LocalDateTime now = java.time.LocalDateTime.now();

        if (edit) {
            // Edit mode: query existing record and update
            ChatTraceSource existingRecord = chatDataService.findTraceSourceByUidAndChatIdAndReqId(
                    chatReqRecords.getUid(),
                    chatReqRecords.getChatId(),
                    chatReqRecords.getId());

            if (existingRecord != null) {
                existingRecord.setContent(traceResult.toString());
                existingRecord.setUpdateTime(now);

                chatDataService.updateTraceSourceByUidAndChatIdAndReqId(existingRecord);
                log.info("Update trace record, reqId: {}, chatId: {}, uid: {}",
                        chatReqRecords.getId(), chatReqRecords.getChatId(), chatReqRecords.getUid());
            }
        } else {
            // New mode: create new record
            createNewTraceSource(chatReqRecords, traceResult, now);
        }
    }

    /**
     * Create new trace record
     */
    private void createNewTraceSource(ChatReqRecords chatReqRecords, StringBuffer traceResult, java.time.LocalDateTime now) {
        ChatTraceSource chatTraceSource = new ChatTraceSource();
        chatTraceSource.setUid(chatReqRecords.getUid());
        chatTraceSource.setChatId(chatReqRecords.getChatId());
        chatTraceSource.setReqId(chatReqRecords.getId());
        chatTraceSource.setContent(traceResult.toString());
        chatTraceSource.setType("search");
        chatTraceSource.setCreateTime(now);
        chatTraceSource.setUpdateTime(now);

        chatDataService.createTraceSource(chatTraceSource);
        log.info("Create new trace record, reqId: {}, chatId: {}, uid: {}",
                chatReqRecords.getId(), chatReqRecords.getChatId(), chatReqRecords.getUid());
    }
}
