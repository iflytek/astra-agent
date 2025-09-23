package com.iflytek.astron.console.hub.controller;

import com.iflytek.astron.console.commons.dto.llm.SparkChatRequest;
import com.iflytek.astron.console.hub.service.SparkChatService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@Slf4j
@RestController
@RequestMapping("/api/spark")
@RequiredArgsConstructor
@Validated
@Tag(name = "Spark Large Model", description = "Spark large model chat interface")
public class SparkChatController {

    private final SparkChatService sparkChatService;

    @PostMapping(value = "/chat/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    @Operation(summary = "Spark Large Model Streaming Chat", description = "Stream conversation with Spark large model, supports real-time response")
    public SseEmitter chatStream(@Parameter(description = "Chat request parameters") @Valid @RequestBody SparkChatRequest request) {

        log.info("Starting Spark large model streaming chat, chatId: {}, userId: {}", request.getChatId(), request.getUserId());

        return sparkChatService.chatStream(request);
    }
}
