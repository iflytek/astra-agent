package com.iflytek.astra.console.hub.service;

import com.alibaba.fastjson2.JSON;
import com.iflytek.astra.console.commons.util.SseEmitterUtil;
import com.iflytek.astra.console.hub.config.DeepSeekConfig;
import com.iflytek.astra.console.hub.dto.DeepSeekChatRequest;
import com.iflytek.astra.console.hub.dto.DeepSeekChatResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import okhttp3.*;
import okio.BufferedSource;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class DeepSeekChatService {

    private final DeepSeekConfig deepSeekConfig;
    private final OkHttpClient deepSeekHttpClient;

    public SseEmitter chatStream(DeepSeekChatRequest request) {
        SseEmitter emitter = SseEmitterUtil.createSseEmitter(600000L);
        String streamId = generateStreamId(request);

        Thread.startVirtualThread(() -> {
            try {
                performChatRequest(request, emitter, streamId);
            } catch (Exception e) {
                log.error("DeepSeek chat stream processing exception", e);
                SseEmitterUtil.completeWithError(emitter, "Processing exception: " + e.getMessage());
            }
        });

        return emitter;
    }

    private void performChatRequest(DeepSeekChatRequest request, SseEmitter emitter, String streamId) throws IOException {
        request.setStream(true);
        String requestBody = buildRequestBody(request);

        Request httpRequest = new Request.Builder()
                .url(deepSeekConfig.getChatCompletionUrl())
                .post(RequestBody.create(requestBody, MediaType.get("application/json; charset=utf-8")))
                .addHeader("Authorization", "Bearer " + deepSeekConfig.getApiKey())
                .addHeader("Content-Type", "application/json")
                .addHeader("Accept", "text/event-stream")
                .build();

        Call call = deepSeekHttpClient.newCall(httpRequest);

        call.enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                log.error("DeepSeek API call failed", e);
                SseEmitterUtil.completeWithError(emitter, "API call failed: " + e.getMessage());
            }

            @Override
            public void onResponse(Call call, Response response) {
                if (!response.isSuccessful()) {
                    log.error("DeepSeek API response failed, status code: {}, reason: {}", response.code(), response.message());
                    SseEmitterUtil.completeWithError(emitter, "API response failed: " + response.message());
                    return;
                }

                ResponseBody body = response.body();
                if (body != null) {
                    processStreamResponse(body, emitter, streamId);
                }
            }
        });
    }

    private void processStreamResponse(ResponseBody body, SseEmitter emitter, String streamId) {
        BufferedSource source = body.source();
        StringBuilder completeContent = new StringBuilder();

        try {
            while (true) {
                String line = source.readUtf8Line();
                if (line == null) {
                    break;
                }

                if (line.startsWith("data: ")) {
                    String data = line.substring(6).trim();

                    if ("[DONE]".equals(data)) {
                        handleStreamComplete(emitter, completeContent);
                        break;
                    }

                    if (!data.isEmpty()) {
                        parseAndSendData(data, emitter, completeContent);
                    }
                }
            }
        } catch (IOException e) {
            log.error("Error processing DeepSeek stream response", e);
            SseEmitterUtil.completeWithError(emitter, "Stream response processing exception: " + e.getMessage());
        } finally {
            try {
                body.close();
            } catch (Exception e) {
                log.warn("Exception closing response body", e);
            }
        }
    }

    private void parseAndSendData(String data, SseEmitter emitter, StringBuilder completeContent) {
        try {
            DeepSeekChatResponse response = JSON.parseObject(data, DeepSeekChatResponse.class);

            if (response.getChoices() != null && !response.getChoices().isEmpty()) {
                DeepSeekChatResponse.Choice choice = response.getChoices().get(0);
                if (choice.getDelta() != null && choice.getDelta().getContent() != null) {
                    completeContent.append(choice.getDelta().getContent());
                }
            }

            SseEmitterUtil.sendData(emitter, response);

        } catch (Exception e) {
            log.error("Exception parsing DeepSeek response data: {}", data, e);
            DeepSeekChatResponse errorResponse = createErrorResponse("Response parsing exception: " + e.getMessage());
            SseEmitterUtil.sendData(emitter, errorResponse);
        }
    }

    private void handleStreamComplete(SseEmitter emitter, StringBuilder completeContent) {
        Map<String, Object> completeData = new HashMap<>();
        completeData.put("completeContent", completeContent.toString());

        SseEmitterUtil.sendComplete(emitter, completeData);
        SseEmitterUtil.sendEndAndComplete(emitter);
    }

    private String generateStreamId(DeepSeekChatRequest request) {
        return "deepseek_" + request.getChatId() + "_" + System.currentTimeMillis();
    }

    private String buildRequestBody(DeepSeekChatRequest request) {
        Map<String, Object> requestMap = new HashMap<>();
        requestMap.put("model", request.getModel());
        requestMap.put("messages", buildMessages(request.getMessages()));
        requestMap.put("stream", request.getStream());

        if (request.getTemperature() != null) {
            requestMap.put("temperature", request.getTemperature());
        }
        if (request.getTopP() != null) {
            requestMap.put("top_p", request.getTopP());
        }
        if (request.getMaxTokens() != null) {
            requestMap.put("max_tokens", request.getMaxTokens());
        }
        if (request.getStop() != null && !request.getStop().isEmpty()) {
            requestMap.put("stop", request.getStop());
        }
        if (request.getFrequencyPenalty() != null) {
            requestMap.put("frequency_penalty", request.getFrequencyPenalty());
        }
        if (request.getPresencePenalty() != null) {
            requestMap.put("presence_penalty", request.getPresencePenalty());
        }

        return JSON.toJSONString(requestMap);
    }

    private List<Map<String, String>> buildMessages(List<DeepSeekChatRequest.MessageDto> messages) {
        return messages.stream()
                .map(msg -> {
                    Map<String, String> message = new HashMap<>();
                    message.put("role", msg.getRole());
                    message.put("content", msg.getContent());
                    return message;
                })
                .collect(Collectors.toList());
    }

    private DeepSeekChatResponse createErrorResponse(String errorMessage) {
        DeepSeekChatResponse errorResponse = new DeepSeekChatResponse();
        errorResponse.setObject("error");

        DeepSeekChatResponse.Choice choice = new DeepSeekChatResponse.Choice();
        choice.setIndex(0);
        choice.setFinishReason("error");

        DeepSeekChatResponse.Delta delta = new DeepSeekChatResponse.Delta();
        delta.setContent(errorMessage);
        choice.setDelta(delta);

        errorResponse.setChoices(List.of(choice));
        return errorResponse;
    }
}
