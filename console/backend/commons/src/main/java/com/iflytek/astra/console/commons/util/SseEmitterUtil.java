package com.iflytek.astra.console.commons.util;

import cn.hutool.core.thread.ThreadUtil;
import com.alibaba.fastjson2.JSON;
import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import lombok.extern.slf4j.Slf4j;
import okhttp3.sse.EventSource;
import org.apache.logging.log4j.util.Base64Util;
import org.springframework.web.context.request.async.AsyncRequestNotUsableException;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Stream;

@Slf4j
public class SseEmitterUtil {

    private static final long DEFAULT_SSE_TIMEOUT_MS = 8 * 60 * 1000L;
    private static final String END_DATA = "{\"end\":true,\"timestamp\":" + System.currentTimeMillis() + "}";

    private static final Cache<String, Boolean> streamStopSignalSet = CacheBuilder.newBuilder()
                    .expireAfterWrite(16, TimeUnit.SECONDS)
                    .build();

    /**
     * 使用map对象，便于根据userId来获取对应的SseEmitter，或者放redis里面
     */
    private static final Map<String, SseEmitter> SESSION_MAP = new ConcurrentHashMap<>(256);

    public static final Map<String, EventSource> EVENTSOURCE_MAP = new ConcurrentHashMap<>(256);

    public static SseEmitter get(String sseId) {
        return SESSION_MAP.get(sseId);
    }

    public static boolean exist(String sseId) {
        return get(sseId) != null;
    }

    /**
     * sse返回
     */
    public static void sendMsgLikeTypeWriter(String content, String sseId, Long interval) {
        try {
            // 字符串中不包含英文字母，逐个字符输出
            for (int j = 0; j < content.length(); j++) {
                // 逐个字刷入sse进行发送
                SseEmitterUtil.sendMessage(sseId, Base64Util.encode(String.valueOf(content.charAt(j))));
                char codePoint = content.charAt(j);
                if ((codePoint >= 65 && codePoint <= 90)
                                || (codePoint >= 97 && codePoint <= 122)) {
                    ThreadUtil.sleep(1);
                } else {
                    if (interval > 0) {
                        ThreadUtil.sleep(interval);
                    }
                }
            }
        } catch (Exception e) {
            // 关ws
            if (e instanceof IllegalStateException) {
                log.error("过期的发送内容,sse已关闭");
            } else {
                log.error("sse发送异常", e);
            }
        }
    }

    /**
     * 创建用户连接并返回 SseEmitter
     *
     * @return SseEmitter
     */
    public static SseEmitter create(String sseId) {
        SseEmitter sseEmitter = new SseEmitter();
        // 注册回调
        sseEmitter.onCompletion(completionCallBack(sseId));
        sseEmitter.onError(errorCallBack(sseId));
        sseEmitter.onTimeout(timeoutCallBack(sseId));
        SESSION_MAP.put(sseId, sseEmitter);
        return sseEmitter;
    }

    public static SseEmitter create(String sseId, long timeout) {
        SseEmitter sseEmitter = new SseEmitter(timeout);
        // 注册回调
        sseEmitter.onCompletion(completionCallBack(sseId));
        sseEmitter.onError(errorCallBack(sseId));
        sseEmitter.onTimeout(timeoutCallBack(sseId));
        SESSION_MAP.put(sseId, sseEmitter);
        return sseEmitter;
    }

    /**
     * 给指定用户发送信息
     */
    public static void sendMessage(String sseId, Object message) {
        if (SESSION_MAP.containsKey(sseId)) {
            try {
                SESSION_MAP.get(sseId).send(message);
            } catch (IOException e) {
                if (e.getMessage().contains("断开的管道")) {
                    // 前端浏览器存在断开连接，则调整为info级别
                    log.info("SSE[{}]推送异常:{}", sseId, e.getMessage());
                } else {
                    log.error("SSE[{}]推送异常:{}", sseId, e.getMessage());
                }
                close(sseId);
            }
        }
    }

    /**
     * 移除用户连接
     */
    public static void close(String sseId) {
        try {
            SseEmitter sseEmitter = SESSION_MAP.get(sseId);
            if (sseEmitter != null) {
                // 关闭sse
                sseEmitter.complete();
                SESSION_MAP.remove(sseId);
            }
        } catch (IllegalStateException e) {
            log.info("sse已被关闭过 : {}", e.getMessage());
        }
    }

    public static void error(String sseId, Throwable t) {
        try {
            SseEmitter sseEmitter = SESSION_MAP.get(sseId);
            if (sseEmitter != null) {
                // 关闭sse
                sseEmitter.completeWithError(t);
                SESSION_MAP.remove(sseId);
            }
        } catch (IllegalStateException e) {
            log.info("sse已被关闭过 : {}", e.getMessage());
        }
    }

    private static Runnable completionCallBack(String sseId) {
        return () -> {
            log.info("SSE[{}] completionCallBack", sseId);
            close(sseId);

            EventSource eventSource = EVENTSOURCE_MAP.get(sseId);
            if (eventSource != null) {
                eventSource.cancel();
                EVENTSOURCE_MAP.remove(sseId);
            }
        };
    }

    private static Runnable timeoutCallBack(String sseId) {
        return () -> {
            log.warn("SSE[{}] timeoutCallBack", sseId);
            close(sseId);
        };
    }

    private static Consumer<Throwable> errorCallBack(String sseId) {
        return throwable -> {
            log.error("SSE[{}] errorCallBack : {}", sseId, throwable.getMessage(), throwable);
            error(sseId, throwable);
        };
    }

    public static SseEmitter newSseAndSendMessageClose(Object message) {
        SseEmitter sseEmitter = new SseEmitter(10_000L);
        try {
            sseEmitter.send(message);
        } catch (IOException e) {
            log.info("newSseAndSendMessageClose异常:{}", e.getMessage());
        }
        sseEmitter.complete();
        return sseEmitter;
    }

    /**
     * 发送错误信息并关闭连接
     */
    public static void sendAndCompleteWithError(String sseId, Object errorResponse) {
        SseEmitter emitter = SESSION_MAP.get(sseId);
        if (emitter != null) {
            try {
                emitter.send(SseEmitter.event().name("error").data(errorResponse));
            } catch (IOException e) {
                log.warn("SSE[{}] 发送错误消息异常: {}", sseId, e.getMessage(), e);
            } finally {
                try {
                    emitter.completeWithError(new RuntimeException(errorResponse.toString()));
                } catch (Exception ex) {
                    log.warn("SSE[{}] completeWithError 异常: {}", sseId, ex.getMessage(), ex);
                }
                SESSION_MAP.remove(sseId);
            }
        } else {
            log.warn("SSE[{}] 不存在，无法发送错误信息", sseId);
        }
    }

    public static SseEmitter createSseEmitter() {
        return createSseEmitter(DEFAULT_SSE_TIMEOUT_MS);
    }

    public static SseEmitter createSseEmitter(long timeoutMs) {
        SseEmitter emitter = new SseEmitter(timeoutMs);
        emitter.onCompletion(() -> log.debug("SseEmitter completed: {}", emitter.hashCode()));
        emitter.onError(e -> log.error("SseEmitter error: {}, message: {}", emitter.hashCode(), e.getMessage()));
        emitter.onTimeout(() -> log.warn("SseEmitter timeout: {}", emitter.hashCode()));
        return emitter;
    }

    public static void stopStream(String streamId) {
        if (streamId != null) {
            streamStopSignalSet.put(streamId, true);
            log.debug("Stream stop signal set for streamId: {}", streamId);
        }
    }

    public static <T> void asyncSendStreamAndClose(
                    SseEmitter emitter,
                    Stream<T> dataStream,
                    String streamId,
                    Function<T, Object> dataMapper,
                    Consumer<Exception> errorHandler) {
        Thread.startVirtualThread(() -> {
            try {
                sendStream(emitter, dataStream, streamId, dataMapper, errorHandler);
            } catch (Exception e) {
                log.error("Async stream processing failed for streamId: {}", streamId, e);
                if (errorHandler != null) {
                    errorHandler.accept(e);
                }
            } finally {
                sendEndAndComplete(emitter);
            }
        });
    }

    public static <T> void sendStream(
                    SseEmitter emitter,
                    Stream<T> dataStream,
                    String streamId,
                    Function<T, Object> dataMapper,
                    Consumer<Exception> errorHandler) {
        if (dataStream == null) {
            log.warn("Data stream is null for streamId: {}", streamId);
            return;
        }

        try (dataStream) {
            Iterator<T> iterator = dataStream.iterator();
            while (iterator.hasNext()) {
                // Check stop signal
                if (isStreamStopped(streamId)) {
                    log.info("Stream stopped by signal for streamId: {}", streamId);
                    break;
                }

                T data = iterator.next();
                if (data == null) {
                    continue;
                }

                try {
                    Object mappedData = dataMapper != null ? dataMapper.apply(data) : data;
                    sendData(emitter, mappedData);
                } catch (Exception e) {
                    log.error("Error processing stream data for streamId: {}", streamId, e);
                    if (errorHandler != null) {
                        errorHandler.accept(e);
                    }
                }
            }
        } catch (Exception e) {
            log.error("Stream processing failed for streamId: {}", streamId, e);
            if (errorHandler != null) {
                errorHandler.accept(e);
            }
        }
    }

    public static void sendBufferedStream(
                    SseEmitter emitter,
                    Stream<String> dataStream,
                    String streamId,
                    int bufferSize,
                    Consumer<String> onBufferReady) {
        if (dataStream == null) {
            log.warn("Data stream is null for streamId: {}", streamId);
            return;
        }

        StringBuilder buffer = new StringBuilder();

        try (dataStream) {
            Iterator<String> iterator = dataStream.iterator();
            while (iterator.hasNext()) {
                if (isStreamStopped(streamId)) {
                    log.info("Buffered stream stopped by signal for streamId: {}", streamId);
                    break;
                }

                String data = iterator.next();
                if (data != null) {
                    buffer.append(data);

                    if (buffer.length() >= bufferSize) {
                        flushBuffer(emitter, buffer, onBufferReady);
                    }
                }
            }

            // Send remaining buffer data
            if (buffer.length() > 0) {
                flushBuffer(emitter, buffer, onBufferReady);
            }

        } catch (Exception e) {
            log.error("Buffered stream processing failed for streamId: {}", streamId, e);
        }
    }

    public static void sendWithCallback(
                    SseEmitter emitter,
                    Supplier<Object> dataSupplier,
                    Consumer<Object> beforeSend,
                    Consumer<Object> afterSend,
                    Consumer<Exception> errorHandler) {
        try {
            Object data = dataSupplier.get();

            if (beforeSend != null) {
                beforeSend.accept(data);
            }

            sendData(emitter, data);

            if (afterSend != null) {
                afterSend.accept(data);
            }

        } catch (Exception e) {
            log.error("Callback send failed", e);
            if (errorHandler != null) {
                errorHandler.accept(e);
            }
        }
    }

    public static void sendData(SseEmitter emitter, Object data) {
        if (emitter == null) {
            log.warn("SseEmitter is null, cannot send data");
            return;
        }

        if (data == null) {
            log.warn("Attempted to send null data");
            return;
        }

        try {
            String jsonData = data instanceof String ? (String) data : JSON.toJSONString(data);
            emitter.send(SseEmitter.event().name("data").data(jsonData));

        } catch (AsyncRequestNotUsableException e) {
            log.warn("SSE client connection terminated: {}", e.getMessage());
        } catch (IOException e) {
            log.error("Failed to send SSE data: {}", e.getMessage(), e);
        } catch (IllegalStateException e) {
            log.debug("SseEmitter already completed: {}", e.getMessage());
        } catch (Exception e) {
            log.error("Unexpected error sending SSE data", e);
        }
    }

    public static void sendError(SseEmitter emitter, String errorMessage) {
        if (emitter == null) {
            return;
        }

        try {
            Map<String, Object> errorData = Map.of(
                            "error", true,
                            "message", errorMessage != null ? errorMessage : "Unknown error",
                            "timestamp", System.currentTimeMillis());

            emitter.send(SseEmitter.event().name("error").data(JSON.toJSONString(errorData)));

        } catch (Exception e) {
            log.error("Failed to send error message via SSE", e);
        }
    }

    public static void sendComplete(SseEmitter emitter) {
        sendComplete(emitter, null);
    }

    public static void sendComplete(SseEmitter emitter, Map<String, Object> completionData) {
        if (emitter == null) {
            return;
        }

        try {
            Map<String, Object> completeData = Map.of(
                            "complete", true,
                            "timestamp", System.currentTimeMillis(),
                            "data", completionData != null ? completionData : Map.of());

            emitter.send(SseEmitter.event().name("complete").data(JSON.toJSONString(completeData)));

        } catch (Exception e) {
            log.error("Failed to send completion message via SSE", e);
        }
    }

    public static void sendEndAndComplete(SseEmitter emitter) {
        if (emitter == null) {
            return;
        }

        try {
            emitter.send(SseEmitter.event().name("end").data(END_DATA));
        } catch (AsyncRequestNotUsableException e) {
            log.warn("Client connection already closed when sending end signal: {}", e.getMessage());
        } catch (Exception e) {
            log.error("Failed to send end signal via SSE", e);
        } finally {
            try {
                emitter.complete();
            } catch (IllegalStateException e) {
                log.debug("SseEmitter already completed: {}", e.getMessage());
            } catch (Exception e) {
                log.error("Failed to complete SseEmitter", e);
            }
        }
    }

    public static void completeWithError(SseEmitter emitter, String errorMessage) {
        if (emitter == null) {
            return;
        }

        sendError(emitter, errorMessage);

        try {
            emitter.complete();
        } catch (Exception e) {
            log.error("Failed to complete SseEmitter with error", e);
        }
    }

    public static boolean isStreamStopped(String streamId) {
        if (streamId == null) {
            return false;
        }

        Boolean stopped = streamStopSignalSet.getIfPresent(streamId);
        if (stopped != null && stopped) {
            streamStopSignalSet.invalidate(streamId);
            return true;
        }
        return false;
    }

    private static void flushBuffer(SseEmitter emitter, StringBuilder buffer, Consumer<String> onBufferReady) {
        String content = buffer.toString();
        buffer.setLength(0);

        if (onBufferReady != null) {
            onBufferReady.accept(content);
        }

        sendData(emitter, content);
    }

    public static class StreamProcessor<T> {
        private final SseEmitter emitter;
        private final String streamId;
        private Function<T, Object> dataMapper;
        private Consumer<Exception> errorHandler;
        private Consumer<T> beforeProcess;
        private Consumer<Object> afterProcess;
        private int bufferSize = 0;
        private StringBuilder buffer;

        public StreamProcessor(SseEmitter emitter, String streamId) {
            this.emitter = emitter;
            this.streamId = streamId;
            this.buffer = new StringBuilder();
        }

        public StreamProcessor<T> withDataMapper(Function<T, Object> dataMapper) {
            this.dataMapper = dataMapper;
            return this;
        }

        public StreamProcessor<T> withErrorHandler(Consumer<Exception> errorHandler) {
            this.errorHandler = errorHandler;
            return this;
        }

        public StreamProcessor<T> withBeforeProcess(Consumer<T> beforeProcess) {
            this.beforeProcess = beforeProcess;
            return this;
        }

        public StreamProcessor<T> withAfterProcess(Consumer<Object> afterProcess) {
            this.afterProcess = afterProcess;
            return this;
        }

        public StreamProcessor<T> withBuffer(int bufferSize) {
            this.bufferSize = bufferSize;
            this.buffer = new StringBuilder();
            return this;
        }

        public void processStream(Stream<T> dataStream) {
            if (bufferSize > 0 && buffer != null) {
                asyncSendStreamAndCloseWithBuffer(emitter, dataStream, streamId, data -> {
                    if (beforeProcess != null) {
                        beforeProcess.accept(data);
                    }

                    Object mappedData = dataMapper != null ? dataMapper.apply(data) : data;

                    if (afterProcess != null) {
                        afterProcess.accept(mappedData);
                    }

                    return mappedData;
                }, errorHandler);
            } else {
                asyncSendStreamAndClose(emitter, dataStream, streamId, data -> {
                    if (beforeProcess != null) {
                        beforeProcess.accept(data);
                    }

                    Object mappedData = dataMapper != null ? dataMapper.apply(data) : data;

                    if (afterProcess != null) {
                        afterProcess.accept(mappedData);
                    }

                    return mappedData;
                }, errorHandler);
            }
        }

        private void asyncSendStreamAndCloseWithBuffer(SseEmitter emitter, Stream<T> dataStream, String streamId,
                        Function<T, Object> processor, Consumer<Exception> errorHandler) {
            Thread.startVirtualThread(() -> {
                try {
                    List<Object> processedDataList = new ArrayList<>();
                    dataStream.forEach(data -> {
                        try {
                            Object processedData = processor.apply(data);
                            buffer.append(processedData.toString());
                            processedDataList.add(processedData);

                            // Use bufferSize as batch size limit
                            if (processedDataList.size() >= bufferSize) {
                                sendData(emitter, buffer.toString());
                                buffer.setLength(0);
                                processedDataList.clear();
                            }
                        } catch (Exception e) {
                            log.error("Error processing stream data, streamId: {}", streamId, e);
                            if (errorHandler != null) {
                                errorHandler.accept(e);
                            }
                        }
                    });

                    // Send remaining buffered data
                    if (!buffer.isEmpty()) {
                        sendData(emitter, buffer.toString());
                        buffer.setLength(0);
                    }

                    sendEndAndComplete(emitter);
                } catch (Exception e) {
                    log.error("Error in async stream sending, streamId: {}", streamId, e);
                    if (errorHandler != null) {
                        errorHandler.accept(e);
                    }
                    completeWithError(emitter, e.getMessage());
                }
            });
        }
    }

}
