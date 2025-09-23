package com.iflytek.astron.console.toolkit.sse;


import com.iflytek.astron.console.commons.util.SseEmitterUtil;
import com.iflytek.astron.console.toolkit.entity.spark.chat.ChatResponse;
import com.iflytek.astron.console.toolkit.util.JacksonUtil;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import okhttp3.Response;
import okhttp3.sse.EventSource;
import okhttp3.sse.EventSourceListener;
import org.jetbrains.annotations.NotNull;

@Slf4j
@Getter
public class WorkflowInnerEventSourceListener extends EventSourceListener {

    String sseId;

    public WorkflowInnerEventSourceListener(String sseId) {
        this.sseId = sseId;
    }

    @Override
    public void onOpen(@NotNull EventSource eventSource, @NotNull Response response) {
        log.info("WorkflowSseEventSourceListener[{}] onOpen, response = {}", sseId, response);
        SseEmitterUtil.EVENTSOURCE_MAP.put(sseId, eventSource);
    }

    @Override
    public void onEvent(@NotNull EventSource eventSource, String id, String type, @NotNull String data) {
        log.info("WorkflowSseEventSourceListener[{}] onEvent data = {}", sseId, data);
        ChatResponse chatResponse = JacksonUtil.parseObject(data, ChatResponse.class);
        sendMessage(chatResponse);
    }

    @Override
    public void onClosed(@NotNull EventSource eventSource) {
        log.info("WorkflowSseEventSourceListener[{}] onClosed", sseId);
        SseEmitterUtil.close(sseId);
    }

    private void sendMessage(ChatResponse chatResponse) {
        SseEmitterUtil.sendMessage(sseId, chatResponse);
    }

    @Override
    public void onFailure(@NotNull EventSource eventSource, Throwable t, Response response) {
        log.error("WorkflowSseEventSourceListener[{}] onFailure, response = {}, t = {}", sseId, response, t.getMessage(), t);
        SseEmitterUtil.error(sseId, t);
    }

}
