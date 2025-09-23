package com.iflytek.astron.console.hub.service;

import cn.xfyun.api.AgentClient;
import cn.xfyun.model.agent.AgentChatParam;
import cn.xfyun.model.agent.AgentResumeParam;
import cn.xfyun.model.sparkmodel.RoleContent;
import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import com.iflytek.astron.console.commons.service.data.ChatDataService;
import com.iflytek.astron.console.commons.entity.workflow.WorkflowChatRequest;
import com.iflytek.astron.console.commons.entity.workflow.WorkflowEventData;
import com.iflytek.astron.console.commons.entity.workflow.WorkflowResumeReq;
import com.iflytek.astron.console.commons.entity.chat.ChatReqRecords;
import com.iflytek.astron.console.commons.util.SseEmitterUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.Response;
import okhttp3.ResponseBody;
import okio.BufferedSource;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Workflow conversation service
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class WorkflowChatService {

    @Value("${spark.api-key}")
    private String apiKey;

    @Value("${spark.api-secret}")
    private String apiSecret;

    private final ChatDataService chatDataService;

    /**
     * Create workflow conversation stream
     *
     * @param request Workflow conversation request
     * @return SseEmitter
     */
    public SseEmitter workflowChatStream(WorkflowChatRequest request) {
        SseEmitter emitter = SseEmitterUtil.createSseEmitter();
        String streamId = request.getChatId() + "_" + request.getUserId() + "_" + System.currentTimeMillis();

        workflowChatStream(request, emitter, streamId, null, false);
        return emitter;
    }

    /**
     * Workflow conversation stream processing
     *
     * @param request Workflow conversation request
     * @param emitter SSE emitter
     * @param streamId Stream ID
     * @param chatReqRecords Chat request records
     * @param edit Whether in edit mode
     */
    public void workflowChatStream(WorkflowChatRequest request, SseEmitter emitter, String streamId,
            ChatReqRecords chatReqRecords, boolean edit) {
        // if (chatReqRecords == null || chatReqRecords.getUid() == null || chatReqRecords.getChatId() ==
        // null) {
        // SseEmitterUtil.completeWithError(emitter, "Chat records are empty");
        // return;
        // }

        try {
            // Create AgentClient
            AgentClient agentClient = new AgentClient.Builder(apiKey, apiSecret).build();

            // Build workflow conversation parameters
            AgentChatParam chatParam = buildAgentChatParam(request);
            log.info("Starting workflow conversation, request: {}", request);

            // Send workflow conversation request
            agentClient.completion(chatParam, new WorkflowCallback(emitter, streamId, chatReqRecords, edit));

        } catch (Exception e) {
            log.error("Failed to create workflow conversation stream, streamId: {}", streamId, e);
            SseEmitterUtil.completeWithError(emitter, "Failed to create workflow conversation stream: " + e.getMessage());
        }
    }

    /**
     * Resume workflow conversation
     *
     * @param request Resume request
     * @return SseEmitter
     */
    public SseEmitter resumeWorkflow(WorkflowResumeReq request) {
        SseEmitter emitter = SseEmitterUtil.createSseEmitter();
        String streamId = request.getChatId() + "_resume_" + System.currentTimeMillis();

        try {
            // Create AgentClient
            AgentClient agentClient = new AgentClient.Builder(apiKey, apiSecret).build();

            // Build resume parameters
            AgentResumeParam resumeParam = buildAgentResumeParam(request);
            log.info("Resuming workflow conversation, request: {}", request);

            // Send resume request
            agentClient.resume(resumeParam, new WorkflowCallback(emitter, streamId, null, false));

        } catch (Exception e) {
            log.error("Failed to resume workflow conversation, streamId: {}", streamId, e);
            SseEmitterUtil.completeWithError(emitter, "Failed to resume workflow conversation: " + e.getMessage());
        }

        return emitter;
    }

    /**
     * Build AgentChatParam parameters
     */
    private AgentChatParam buildAgentChatParam(WorkflowChatRequest request) {
        // Convert message format
        List<RoleContent> history = request.getMessages().stream().map(msg -> {
            RoleContent roleContent = new RoleContent();
            roleContent.setRole(msg.getRole());
            roleContent.setContent(msg.getContent());
            return roleContent;
        }).collect(Collectors.toList());

        return AgentChatParam.builder()
                .flowId(request.getFlowId())
                .uid(request.getUserId())
                .chatId(request.getChatId())
                .stream(request.getStream())
                .history(history)
                .parameters(request.getParameters())
                .ext(request.getExt())
                .build();
    }

    /**
     * Build AgentResumeParam parameters
     */
    private AgentResumeParam buildAgentResumeParam(WorkflowResumeReq request) {
        return AgentResumeParam.builder()
                .eventId(request.getEventId())
                .eventType(request.getEventType())
                .content(request.getContent())
                .build();
    }

    /**
     * Workflow callback handler
     */
    private class WorkflowCallback implements Callback {
        private final SseEmitter emitter;
        private final String streamId;
        private final ChatReqRecords chatReqRecords;
        private final boolean edit;

        public WorkflowCallback(SseEmitter emitter, String streamId, ChatReqRecords chatReqRecords, boolean edit) {
            this.emitter = emitter;
            this.streamId = streamId;
            this.chatReqRecords = chatReqRecords;
            this.edit = edit;
        }

        @Override
        public void onFailure(Call call, IOException e) {
            log.error("Workflow conversation connection failed, streamId: {}, error: {}", streamId, e.getMessage());
            SseEmitterUtil.completeWithError(emitter, "Connection failed: " + e.getMessage());
        }

        @Override
        public void onResponse(Call call, Response response) throws IOException {
            if (!response.isSuccessful()) {
                log.error("Workflow conversation request failed, streamId: {}, status code: {}, reason: {}", streamId, response.code(), response.message());
                SseEmitterUtil.completeWithError(emitter, "Request failed: " + response.message());
                return;
            }

            ResponseBody body = response.body();
            if (body != null) {
                processWorkflowSSEStream(body, emitter, streamId, chatReqRecords, edit);
            } else {
                SseEmitterUtil.completeWithError(emitter, "Response body is empty");
            }
        }
    }

    /**
     * Process workflow SSE stream
     */
    private void processWorkflowSSEStream(ResponseBody body, SseEmitter emitter, String streamId,
            ChatReqRecords chatReqRecords, boolean edit) {
        BufferedSource source = body.source();
        StringBuilder finalResult = new StringBuilder();
        StringBuilder thinkingResult = new StringBuilder();
        StringBuilder sid = new StringBuilder();
        StringBuilder traceResult = new StringBuilder();

        try (body) {
            try {
                while (true) {
                    // Check stop signal
                    if (SseEmitterUtil.isStreamStopped(streamId)) {
                        log.info("Stop signal detected, saving collected data, streamId: {}", streamId);
                        handleWorkflowStreamInterrupted(emitter, streamId, finalResult, thinkingResult,
                                chatReqRecords, sid, traceResult, edit);
                        break;
                    }

                    String line = source.readUtf8Line();
                    if (line == null) {
                        break;
                    }

                    if (line.startsWith("data:")) {
                        if (line.contains("[DONE]")) {
                            handleWorkflowStreamComplete(emitter, streamId, finalResult, thinkingResult,
                                    chatReqRecords, sid, traceResult, edit);
                            break;
                        }

                        String data = line.substring(5).trim();
                        parseWorkflowSSEContent(data, emitter, streamId, finalResult, thinkingResult, sid, traceResult);

                        // Check stop signal again after processing data
                        if (SseEmitterUtil.isStreamStopped(streamId)) {
                            log.info("Stop signal detected after processing data, saving collected data, streamId: {}", streamId);
                            handleWorkflowStreamInterrupted(emitter, streamId, finalResult, thinkingResult,
                                    chatReqRecords, sid, traceResult, edit);
                            break;
                        }
                    }
                }
            } catch (IOException e) {
                log.error("Exception reading workflow SSE stream data, saving collected data, streamId: {}", streamId, e);
                handleWorkflowStreamInterrupted(emitter, streamId, finalResult, thinkingResult,
                        chatReqRecords, sid, traceResult, edit);
                SseEmitterUtil.completeWithError(emitter, "Data reading exception: " + e.getMessage());
            }
        } catch (Exception e) {
            log.warn("Exception closing workflow response body, streamId: {}", streamId, e);
            handleWorkflowStreamInterrupted(emitter, streamId, finalResult, thinkingResult,
                    chatReqRecords, sid, traceResult, edit);
        }
    }

    /**
     * Parse workflow SSE content
     */
    private void parseWorkflowSSEContent(String data, SseEmitter emitter, String streamId,
            StringBuilder finalResult, StringBuilder thinkingResult,
            StringBuilder sid, StringBuilder traceResult) {
        log.debug("Workflow SSE data streamId: {} ==> {}", streamId, data);

        try {
            JSONObject dataObj = JSON.parseObject(data);

            // Check if contains event_data key, if so close SSE stream
            if (dataObj.containsKey("event_data")) {
                log.info("Detected event_data key, closing workflow SSE stream, streamId: {}", streamId);

                // Send data to client first
                tryServeWorkflowSSEData(emitter, dataObj, streamId);

                // Process and save data
                processSidValue(dataObj, sid, streamId);
                processWorkflowChoicesData(dataObj, finalResult, thinkingResult, traceResult, streamId);

                // Close SSE stream
                closeWorkflowStream(emitter, streamId, finalResult, thinkingResult, sid, traceResult);
                return;
            }

            // Process workflow-specific event types
            processWorkflowEvents(dataObj, emitter, streamId);

            // Try to send data, continue processing data even if client disconnects
            boolean clientConnected = tryServeWorkflowSSEData(emitter, dataObj, streamId);

            // Process and save data regardless of client connection status
            processSidValue(dataObj, sid, streamId);
            processWorkflowChoicesData(dataObj, finalResult, thinkingResult, traceResult, streamId);

            if (!clientConnected) {
                log.info("Client disconnected, but continuing to process workflow data, streamId: {}", streamId);
            }
        } catch (Exception e) {
            handleWorkflowParseError(e, data, streamId, emitter);
        }
    }

    /**
     * Process workflow-specific events
     */
    private void processWorkflowEvents(JSONObject dataObj, SseEmitter emitter, String streamId) {
        // Check if contains workflow interrupt event
        if (dataObj.containsKey("event")) {
            JSONObject event = dataObj.getJSONObject("event");
            if ("interrupt".equals(event.getString("type"))) {
                // Process workflow interrupt event
                processWorkflowInterrupt(event, emitter, streamId);
            }
        }
    }

    /**
     * Process workflow interrupt event
     */
    private void processWorkflowInterrupt(JSONObject event, SseEmitter emitter, String streamId) {
        try {
            WorkflowEventData eventData = WorkflowEventData.builder()
                    .eventId(event.getString("event_id"))
                    .eventType(event.getString("type"))
                    .needReply(event.getBooleanValue("need_reply"))
                    .value(parseEventValue(event.getJSONObject("value")))
                    .build();

            // Send workflow interrupt event to frontend
            JSONObject interruptResponse = new JSONObject();
            interruptResponse.put("type", "workflow_interrupt");
            interruptResponse.put("eventData", eventData);

            SseEmitterUtil.sendData(emitter, interruptResponse);
            log.info("Sent workflow interrupt event, streamId: {}, eventId: {}", streamId, eventData.getEventId());
        } catch (Exception e) {
            log.error("Failed to process workflow interrupt event, streamId: {}", streamId, e);
        }
    }

    /**
     * Parse event value
     */
    private WorkflowEventData.EventValue parseEventValue(JSONObject valueObj) {
        if (valueObj == null) {
            return null;
        }

        return WorkflowEventData.EventValue.builder()
                .type(valueObj.getString("type"))
                .message(valueObj.getString("message"))
                .content(valueObj.getString("content"))
                .build();
    }

    /**
     * Try to send workflow SSE data
     */
    private boolean tryServeWorkflowSSEData(SseEmitter emitter, JSONObject dataObj, String streamId) {
        if (emitter == null) {
            log.warn("SseEmitter is null, cannot send workflow data, streamId: {}", streamId);
            return false;
        }

        try {
            String jsonData = dataObj.toJSONString();
            emitter.send(SseEmitter.event().name("data").data(jsonData));
            return true;
        } catch (org.springframework.web.context.request.async.AsyncRequestNotUsableException e) {
            log.warn("Client connection disconnected, streamId: {}, continuing background workflow data processing", streamId);
            return false;
        } catch (IOException e) {
            log.error("Failed to send workflow SSE data, streamId: {}, error: {}", streamId, e.getMessage());
            return false;
        } catch (IllegalStateException e) {
            log.debug("SseEmitter completed, streamId: {}", streamId);
            return false;
        } catch (Exception e) {
            log.error("Unexpected error occurred while sending workflow SSE data, streamId: {}", streamId, e);
            return false;
        }
    }

    /**
     * Process SID value
     */
    private void processSidValue(JSONObject dataObj, StringBuilder sid, String streamId) {
        if (sid.isEmpty() && dataObj.containsKey("sid")) {
            String sidValue = dataObj.getString("sid");
            if (sidValue != null && !sidValue.trim().isEmpty()) {
                sid.append(sidValue);
                log.debug("Set workflow sid: {}, streamId: {}", sidValue, streamId);
            }
        }
    }

    /**
     * Process workflow choices data
     */
    private void processWorkflowChoicesData(JSONObject dataObj, StringBuilder finalResult,
            StringBuilder thinkingResult, StringBuilder traceResult, String streamId) {
        if (!dataObj.containsKey("choices")) {
            return;
        }

        // Process here according to workflow's specific response format
        // Basic logic is similar to SparkChatService, but needs to adapt to workflow's special format
        log.debug("Processing workflow choices data, streamId: {}", streamId);
    }

    /**
     * Handle workflow parsing error
     */
    private void handleWorkflowParseError(Exception e, String data, String streamId, SseEmitter emitter) {
        log.error("Exception parsing workflow SSE data, streamId: {}", streamId, e);
        log.error("Exception data: {}", data);

        JSONObject errorResponse = new JSONObject();
        errorResponse.put("error", true);
        errorResponse.put("message", "Exception parsing workflow data: " + e.getMessage());
        errorResponse.put("timestamp", System.currentTimeMillis());

        SseEmitterUtil.sendData(emitter, errorResponse);
    }

    /**
     * Handle workflow stream completion
     */
    private void handleWorkflowStreamComplete(SseEmitter emitter, String streamId, StringBuilder finalResult,
            StringBuilder thinkingResult, ChatReqRecords chatReqRecords,
            StringBuilder sid, StringBuilder traceResult, boolean edit) {
        log.info("Workflow conversation completed, streamId: {}", streamId);

        // If chatReqRecords exists, save data to database
        if (chatReqRecords != null) {
            // Here we can reuse SparkChatService's data saving logic
            log.info("Saving workflow conversation data, streamId: {}", streamId);
        }

        JSONObject completeData = new JSONObject();
        completeData.put("type", "workflow_complete");
        completeData.put("finalResult", finalResult.toString());
        completeData.put("timestamp", System.currentTimeMillis());

        SseEmitterUtil.sendComplete(emitter, completeData);
        SseEmitterUtil.sendEndAndComplete(emitter);
    }

    /**
     * Handle workflow stream interruption
     */
    private void handleWorkflowStreamInterrupted(SseEmitter emitter, String streamId, StringBuilder finalResult,
            StringBuilder thinkingResult, ChatReqRecords chatReqRecords,
            StringBuilder sid, StringBuilder traceResult, boolean edit) {
        log.info("Workflow conversation interrupted, streamId: {}", streamId);

        // If chatReqRecords exists, save data to database
        if (chatReqRecords != null) {
            log.info("Saving workflow interruption data, streamId: {}", streamId);
        }

        JSONObject interruptedData = new JSONObject();
        interruptedData.put("type", "workflow_interrupted");
        interruptedData.put("finalResult", finalResult.toString());
        interruptedData.put("interrupted", true);
        interruptedData.put("reason", "Workflow interrupted or client disconnected");
        interruptedData.put("timestamp", System.currentTimeMillis());

        SseEmitterUtil.sendComplete(emitter, interruptedData);
        SseEmitterUtil.sendEndAndComplete(emitter);
    }

    /**
     * Close workflow SSE stream - called when event_data key is detected
     */
    private void closeWorkflowStream(SseEmitter emitter, String streamId, StringBuilder finalResult,
            StringBuilder thinkingResult, StringBuilder sid, StringBuilder traceResult) {
        log.info("Actively closing workflow SSE stream due to event_data key detection, streamId: {}", streamId);

        try {
            // Build close response data
            JSONObject closeData = new JSONObject();
            closeData.put("type", "workflow_event_data_close");
            closeData.put("finalResult", finalResult.toString());
            closeData.put("thinkingResult", thinkingResult.toString());
            closeData.put("traceResult", traceResult.toString());
            closeData.put("sid", sid.toString());
            closeData.put("reason", "Detected event_data key, workflow ended");
            closeData.put("timestamp", System.currentTimeMillis());

            // Send close completion event
            SseEmitterUtil.sendComplete(emitter, closeData);
            SseEmitterUtil.sendEndAndComplete(emitter);

            log.info("Workflow SSE stream successfully closed, streamId: {}, data length - finalResult: {}, thinkingResult: {}, traceResult: {}",
                    streamId, finalResult.length(), thinkingResult.length(), traceResult.length());
        } catch (Exception e) {
            log.error("Exception occurred while closing workflow SSE stream, streamId: {}", streamId, e);
            try {
                SseEmitterUtil.completeWithError(emitter, "Error occurred while closing workflow stream: " + e.getMessage());
            } catch (Exception ex) {
                log.error("Failed to send error close signal, streamId: {}", streamId, ex);
            }
        }
    }
}
