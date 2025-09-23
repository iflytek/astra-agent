package com.iflytek.astron.console.hub.controller;

import com.iflytek.astron.console.hub.dto.DeepSeekChatRequest;
import com.iflytek.astron.console.hub.service.DeepSeekChatService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@Slf4j
@RestController
@RequestMapping("/api/deepseek")
@RequiredArgsConstructor
@Validated
@Tag(name = "DeepSeek LLM", description = "DeepSeek Large Language Model Chat Interface")
public class DeepSeekChatController {

    private final DeepSeekChatService deepSeekChatService;

    @PostMapping(value = "/chat/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    @Operation(
            summary = "DeepSeek LLM Streaming Chat",
            description = "Streaming conversation with DeepSeek LLM, supporting real-time response",
            responses = {
                    @ApiResponse(responseCode = "200", description = "Streaming connection established successfully"),
                    @ApiResponse(responseCode = "400", description = "Invalid request parameters"),
                    @ApiResponse(responseCode = "401", description = "Invalid API key"),
                    @ApiResponse(responseCode = "429", description = "Request rate limit exceeded"),
                    @ApiResponse(responseCode = "500", description = "Internal server error")
            })
    public SseEmitter chatStream(
            @Parameter(description = "DeepSeek chat request parameters")
            @Valid @RequestBody DeepSeekChatRequest request) {
        log.info("Starting DeepSeek LLM streaming chat, chatId: {}, userId: {}, model: {}",
                request.getChatId(), request.getUserId(), request.getModel());

        return deepSeekChatService.chatStream(request);
    }


    @GetMapping("/models")
    @Operation(
            summary = "Get DeepSeek supported model list",
            description = "Returns all model information supported by DeepSeek API")
    public ResponseEntity<String[]> getSupportedModels() {
        String[] models = {
                "deepseek-chat",
                "deepseek-coder"
        };
        return ResponseEntity.ok(models);
    }

    @PostMapping("/chat/validate")
    @Operation(
            summary = "Validate chat request parameters",
            description = "Validates whether DeepSeek chat request parameters are valid")
    public ResponseEntity<String> validateRequest(
            @Parameter(description = "DeepSeek chat request parameters")
            @Valid @RequestBody DeepSeekChatRequest request) {
        log.info("Validating DeepSeek chat request parameters, chatId: {}, userId: {}",
                request.getChatId(), request.getUserId());

        String validationError = validateRequestParameters(request);
        if (validationError != null) {
            return ResponseEntity.badRequest().body(validationError);
        }

        return ResponseEntity.ok("Parameter validation passed");
    }

    private String validateRequestParameters(DeepSeekChatRequest request) {
        if (request.getMessages() == null || request.getMessages().isEmpty()) {
            return "Message list cannot be empty";
        }

        String temperatureError = validateTemperature(request.getTemperature());
        if (temperatureError != null) {
            return temperatureError;
        }

        String topPError = validateTopP(request.getTopP());
        if (topPError != null) {
            return topPError;
        }

        String maxTokensError = validateMaxTokens(request.getMaxTokens());
        if (maxTokensError != null) {
            return maxTokensError;
        }

        return null;
    }

    private String validateTemperature(Double temperature) {
        if (temperature != null && (temperature < 0.0 || temperature > 2.0)) {
            return "temperature parameter must be between 0.0-2.0";
        }
        return null;
    }

    private String validateTopP(Double topP) {
        if (topP != null && (topP < 0.0 || topP > 1.0)) {
            return "top_p parameter must be between 0.0-1.0";
        }
        return null;
    }

    private String validateMaxTokens(Integer maxTokens) {
        if (maxTokens != null && maxTokens <= 0) {
            return "max_tokens parameter must be greater than 0";
        }
        return null;
    }
}
