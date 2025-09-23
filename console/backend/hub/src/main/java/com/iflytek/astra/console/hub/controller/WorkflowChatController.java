package com.iflytek.astra.console.hub.controller;

import com.iflytek.astra.console.commons.entity.workflow.WorkflowChatRequest;
import com.iflytek.astra.console.commons.entity.workflow.WorkflowResumeReq;
import com.iflytek.astra.console.commons.util.SseEmitterUtil;
import com.iflytek.astra.console.hub.service.WorkflowChatService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import jakarta.validation.Valid;

/**
 * Workflow chat controller
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/workflow")
@RequiredArgsConstructor
@Tag(name = "Workflow Chat", description = "Workflow chat API based on iFlytek AgentClient")
@Validated
public class WorkflowChatController {

    private final WorkflowChatService workflowChatService;

    /**
     * Start workflow chat stream
     *
     * @param request Workflow chat request
     * @return SSE stream
     */
    @PostMapping(value = "/chat/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    @Operation(summary = "Start workflow chat stream", description = "Start streaming chat based on specified workflow ID")
    public SseEmitter workflowChatStream(@Valid @RequestBody WorkflowChatRequest request) {
        log.info("Starting workflow chat stream, flowId: {}, userId: {}, chatId: {}",
                request.getFlowId(), request.getUserId(), request.getChatId());

        return workflowChatService.workflowChatStream(request);
    }

    /**
     * Resume workflow chat
     *
     * @param request Workflow resume request
     * @return SSE stream
     */
    @PostMapping(value = "/chat/resume", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    @Operation(summary = "Resume workflow chat", description = "Resume interrupted workflow chat")
    public SseEmitter resumeWorkflowChat(@Valid @RequestBody WorkflowResumeReq request) {
        log.info("Resuming workflow chat, eventId: {}, operation: {}, userId: {}",
                request.getEventId(), request.getOperation(), request.getUserId());

        return workflowChatService.resumeWorkflow(request);
    }

    /**
     * Stop workflow chat stream
     *
     * @param streamId Stream ID
     */
    @PostMapping("/chat/stop/{streamId}")
    @Operation(summary = "Stop workflow chat stream", description = "Actively stop specified workflow chat stream")
    public void stopWorkflowStream(
            @Parameter(description = "Stream ID", required = true)
            @PathVariable String streamId) {
        log.info("Stopping workflow chat stream, streamId: {}", streamId);

        SseEmitterUtil.stopStream(streamId);
    }

    /**
     * Get workflow chat status
     *
     * @param chatId Chat ID
     * @param userId User ID
     * @return Chat status information
     */
    @GetMapping("/chat/status")
    @Operation(summary = "Get workflow chat status", description = "Query current status of specified workflow chat")
    public String getWorkflowChatStatus(
            @Parameter(description = "Chat ID", required = true)
            @RequestParam String chatId,
            @Parameter(description = "User ID", required = true)
            @RequestParam String userId) {
        log.info("Querying workflow chat status, chatId: {}, userId: {}", chatId, userId);

        // Status query logic can be implemented here as needed
        return "active";
    }

    /**
     * Health check
     *
     * @return Health status
     */
    @GetMapping("/health")
    @Operation(summary = "Workflow service health check", description = "Check if workflow chat service is running normally")
    public String healthCheck() {
        return "Workflow Chat Service is running";
    }
}
