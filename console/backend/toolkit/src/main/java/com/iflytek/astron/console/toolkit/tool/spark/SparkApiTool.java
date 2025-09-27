package com.iflytek.astron.console.toolkit.tool.spark;

import cn.hutool.core.util.IdUtil;
import com.alibaba.fastjson2.JSON;
import com.iflytek.astron.console.commons.constant.ResponseEnum;
import com.iflytek.astron.console.commons.exception.BusinessException;
import com.iflytek.astron.console.toolkit.config.properties.CommonConfig;
import com.iflytek.astron.console.toolkit.entity.spark.SparkApiProtocol;
import com.iflytek.astron.console.toolkit.entity.spark.Text;
import com.iflytek.astron.console.toolkit.entity.spark.chat.ChatResponse;
import com.iflytek.astron.console.toolkit.handler.UserInfoManagerHandler;
import com.iflytek.astron.console.commons.util.SseEmitterUtil;
import com.iflytek.astron.console.toolkit.tool.http.HttpAuthTool;
import com.iflytek.astron.console.toolkit.util.OkHttpUtil;
import lombok.extern.slf4j.Slf4j;
import okhttp3.*;
import okio.ByteString;
import org.jetbrains.annotations.NotNull;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpMethod;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.CountDownLatch;

/**
 * Spark API WebSocket client tool for real-time chat communication.
 *
 * Provides functionality to communicate with Spark AI API via WebSocket, supporting both full
 * response retrieval and streaming SSE responses.
 *
 * @author Spark API Team
 * @since 2023
 */
@Component
@Slf4j
public class SparkApiTool {

    public static final String sparkMaxUrl = "wss://spark-api.xf-yun.com/v3.5/chat";

    public static final String sparkCodeUrl = "ws://spark-api-n.xf-yun.com/v1.1/chat";

    public static final String CODE_DOMAIN = "iflycode.ge7btest";

    @Autowired
    private CommonConfig commonConfig;

    /**
     * Send a chat message and return the complete response via WebSocket.
     *
     * @param content the message content to send
     * @return the complete response from Spark API
     * @throws InterruptedException if the operation is interrupted
     */
    public String onceChatReturnWholeByWs(String content) throws InterruptedException {
        StringBuilder wholeMsg = new StringBuilder();
        CountDownLatch latch = new CountDownLatch(1);

        // Authentication and encryption
        String signedSparkUrl = HttpAuthTool.assembleRequestUrl(sparkMaxUrl, HttpMethod.GET.name(), commonConfig.getApiKey(), commonConfig.getApiSecret());
        Request request = (new Request.Builder()).url(signedSparkUrl).build();
        WebSocket webSocket = OkHttpUtil.getHttpClient().newWebSocket(request, new WebSocketListener() {
            @Override
            public void onOpen(@NotNull WebSocket webSocket, @NotNull Response response) {
                log.info("onceChatReturnWholeByWs spark api link open");
            }

            @Override
            public void onMessage(@NotNull WebSocket webSocket, @NotNull String text) {
                log.info("onceChatReturnWholeByWs spark api receive message:{}", text);
                dealOnMessage(webSocket, text);
            }

            @Override
            public void onMessage(@NotNull WebSocket webSocket, @NotNull ByteString bytes) {
                log.info("onceChatReturnWholeByWs spark api receive message(ByteString): {}", bytes.string(StandardCharsets.UTF_8));
                dealOnMessage(webSocket, bytes.string(StandardCharsets.UTF_8));
            }

            @Override
            public void onClosing(@NotNull WebSocket webSocket, int code, @NotNull String reason) {
                log.info("onceChatReturnWholeByWs spark api link closing, code is {} , reason is [{}]", code, reason);
            }

            @Override
            public void onClosed(@NotNull WebSocket webSocket, int code, @NotNull String reason) {
                log.info("onceChatReturnWholeByWs spark api link closed, code is {} , reason is [{}]", code, reason);
            }

            @Override
            public void onFailure(@NotNull WebSocket webSocket, @NotNull Throwable t, Response response) {
                log.error("onceChatReturnWholeByWs spark api link failed，reason:{}", t.getMessage(), t);
            }

            private void dealOnMessage(WebSocket webSocket, String message) {
                SparkApiProtocol responseDto = JSON.parseObject(message, SparkApiProtocol.class);
                if (responseDto.getHeader().getCode() != 0) {
                    wholeMsg.append(responseDto.getHeader().getMessage());
                } else {
                    Text payloadText = responseDto.getPayload().getChoices().getText().get(0);
                    wholeMsg.append(payloadText.getContent());
                }

                if (responseDto.getHeader().getStatus() == 2) {
                    latch.countDown();
                    onClosing(webSocket, 1000, "onceChatReturnWholeByWs status=2 over");
                    onClosed(webSocket, 1000, "onceChatReturnWholeByWs status=2 over");
                }
            }

        });

        String message = MessageBuilder.buildSparkApiRequest(content, commonConfig.getAppId());
        log.info("send msg = {}", message);
        webSocket.send(message);
        latch.await();
        log.info("wholeResp = {}", wholeMsg);
        return wholeMsg.toString();
    }

    /**
     * Send a chat message and return SSE stream response via WebSocket.
     *
     * @param content the message content to send
     * @return SseEmitter for streaming response
     */
    public SseEmitter onceChatReturnSseByWs(String content) {
        return onceChatReturnSseByWs(sparkMaxUrl, null, content);
    }

    /**
     * Send a chat message and return SSE stream response via WebSocket with custom URL and domain.
     *
     * @param url the WebSocket URL to connect to
     * @param domain the domain parameter for the request
     * @param content the message content to send
     * @return SseEmitter for streaming response
     */
    public SseEmitter onceChatReturnSseByWs(String url, String domain, String content) {
        String userId = UserInfoManagerHandler.getUserId();
        if (SseEmitterUtil.exist(userId)) {
            return SseEmitterUtil.newSseAndSendMessageClose(JSON.toJSONString(new ChatResponse(null, "Access too frequent, please try again later")));
        }

        SseEmitter emitter = SseEmitterUtil.create(userId, 300_000L);
        String chatId = IdUtil.getSnowflakeNextIdStr();

        // Authentication and encryption
        String signedSparkUrl = null;
        signedSparkUrl = HttpAuthTool.assembleRequestUrl(url, HttpMethod.GET.name(), commonConfig.getApiKey(), commonConfig.getApiSecret());

        Request request = (new Request.Builder()).url(signedSparkUrl).build();
        WebSocket webSocket = OkHttpUtil.getHttpClient().newWebSocket(request, new WebSocketListener() {
            @Override
            public void onOpen(@NotNull WebSocket webSocket, @NotNull Response response) {
                log.info("onceChatReturnSseByWs onOpen");
            }

            @Override
            public void onMessage(@NotNull WebSocket webSocket, @NotNull String text) {
                log.info("onceChatReturnSseByWs onMessage:{}", text);

                SparkApiProtocol responseDto = JSON.parseObject(text, SparkApiProtocol.class);
                if (responseDto.getHeader().getCode() != 0) {
                    SseEmitterUtil.sendMessage(userId, responseDto.getHeader().getMessage());
                    SseEmitterUtil.close(userId);
                }

                Integer status = responseDto.getHeader().getStatus();
                String msg = String.valueOf(responseDto.getPayload().getChoices().getText().get(0).getContent());
                if (responseDto.getPayload().getChoices().getSeq() == 0 || responseDto.getPayload().getChoices().getSeq() == 1) {
                    msg = msg.replace("python", "");
                }
                msg = msg.replace("```", "");

                ChatResponse chatResponse = new ChatResponse(chatId, status == 2, status, msg);
                chatResponse.getHeader().setSid(responseDto.getHeader().getSid());
                chatResponse.getHeader().setSeq(responseDto.getPayload().getChoices().getSeq());
                SseEmitterUtil.sendMessage(userId, chatResponse);
                if (responseDto.getHeader().getStatus() == 2) {
                    onClosing(webSocket, 1000, "onceChatReturnSseByWs status=2 over");
                    onClosed(webSocket, 1000, "onceChatReturnSseByWs status=2 over");
                    SseEmitterUtil.close(userId);
                }
            }

            @Override
            public void onMessage(@NotNull WebSocket webSocket, @NotNull ByteString bytes) {
                log.info("onceChatReturnSseByWs onMessage(ByteString): {}", bytes);
            }

            @Override
            public void onClosing(@NotNull WebSocket webSocket, int code, @NotNull String reason) {
                log.info("onceChatReturnSseByWs onClosing, code is {} , reason is [{}]", code, reason);
            }

            @Override
            public void onClosed(@NotNull WebSocket webSocket, int code, @NotNull String reason) {
                log.info("onceChatReturnSseByWs onClosed, code is {} , reason is [{}]", code, reason);
            }

            @Override
            public void onFailure(@NotNull WebSocket webSocket, @NotNull Throwable t, Response response) {
                log.error("onceChatReturnSseByWs onFailure, response = {}, t = {}", response, t.getMessage(), t);
                SseEmitterUtil.error(userId, t);
            }
        });
        String message;
        message = MessageBuilder.buildSparkApiRequest(content, commonConfig.getAppId(), domain);
        log.info("send msg = {}", message);
        webSocket.send(message);

        return emitter;
    }

    /**
     * Send a chat message and return streaming response (deprecated method).
     *
     * @param content the message content to send
     * @return SseEmitter for streaming response
     * @throws InterruptedException if the operation is interrupted
     * @deprecated Use onceChatReturnSseByWs instead
     */
    @Deprecated
    public SseEmitter onceChatReturnStream(String content) throws InterruptedException {
        SseEmitter sseEmitter = new SseEmitter(180000L);

        // Authentication and encryption
        String signedSparkUrl = HttpAuthTool.assembleRequestUrl(sparkMaxUrl, HttpMethod.GET.name(), commonConfig.getApiKey(), commonConfig.getApiSecret());
        Request request = (new Request.Builder()).url(signedSparkUrl).build();
        WebSocket webSocket = OkHttpUtil.getHttpClient().newWebSocket(request, new WebSocketListener() {
            @Override
            public void onOpen(@NotNull WebSocket webSocket, @NotNull Response response) {
                log.info("onceChatReturnStream spark api link open");
            }

            @Override
            public void onMessage(@NotNull WebSocket webSocket, @NotNull String text) {
                log.info("onceChatReturnStream spark api receive message:{}", text);

                SparkApiProtocol responseDto = JSON.parseObject(text, SparkApiProtocol.class);
                if (responseDto.getHeader().getCode() != 0) {
                    sseEmitter.complete();
                    throw new BusinessException(ResponseEnum.RESPONSE_FAILED, text);
                }

                try {
                    sseEmitter.send(text);
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }

                if (responseDto.getHeader().getStatus() == 2) {
                    sseEmitter.complete();
                    onClosing(webSocket, 1000, "onceChatReturnStream status=2 over");
                    onClosed(webSocket, 1000, "onceChatReturnStream status=2 over");
                }
            }

            @Override
            public void onMessage(@NotNull WebSocket webSocket, @NotNull ByteString bytes) {
                log.info("onceChatReturnStream spark api receive message(ByteString): {}", bytes);
            }

            @Override
            public void onClosing(@NotNull WebSocket webSocket, int code, @NotNull String reason) {
                log.info("onceChatReturnStream spark api link closing, code is {} , reason is [{}]", code, reason);
            }

            @Override
            public void onClosed(@NotNull WebSocket webSocket, int code, @NotNull String reason) {
                log.info("onceChatReturnStream spark api link closed, code is {} , reason is [{}]", code, reason);
            }

            @Override
            public void onFailure(@NotNull WebSocket webSocket, @NotNull Throwable t, Response response) {
                log.error("onceChatReturnStream spark api link failed, reason:{}", t.getMessage(), t);
                sseEmitter.completeWithError(t);
            }
        });

        String message = MessageBuilder.buildSparkApiRequest(content, commonConfig.getAppId());
        log.info("send msg = {}", message);
        webSocket.send(message);

        return sseEmitter;
    }



}
