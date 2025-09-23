package com.iflytek.astra.console.hub.util;

import cn.hutool.core.util.StrUtil;
import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.iflytek.astra.console.commons.constant.ResponseEnum;
import com.iflytek.astra.console.commons.exception.BusinessException;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import okhttp3.*;
import org.jetbrains.annotations.NotNull;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * AI service client integrating image generation, text generation and other AI service functions
 *
 */
@Slf4j
@Component
public class BotAIServiceClient {

    private static final List<Integer> ALLOWED_IMAGE_SIZES = Arrays.asList(512, 640, 768, 1024);
    private static final int DEFAULT_IMAGE_SIZE = 1024;
    private static final String IMAGE_GENERATION_DOMAIN = "safecfa46";
    private static final String TEXT_HOST_URL = "https://spark-openapi.cn-huabei-1.xf-yun.com/v4.0/multimodal";
    private static final String imageHost = "http://spark-openapi.cn-huabei-1.xf-yun.com/v2.1/tti";
    private final OkHttpClient httpClient = new OkHttpClient().newBuilder()
            .connectionPool(new ConnectionPool(100, 5, TimeUnit.MINUTES))
            .connectTimeout(60, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .writeTimeout(60, TimeUnit.SECONDS)
            .build();

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Value("${spark.app-id}")
    private String appId;

    @Value("${spark.api-key}")
    private String apiKey;

    @Value("${spark.api-secret}")
    private String apiSecret;

    /**
     * Image generation request
     *
     * @param uid User ID
     * @param prompt Generation prompt
     * @param size Image size, default 1024
     * @return Response result
     */
    public JSONObject generateImage(String uid, String prompt, Integer size) {
        if (uid == null || StrUtil.isBlank(prompt)) {
            throw new IllegalArgumentException("User ID and prompt cannot be empty");
        }

        int imageSize = validateImageSize(size);
        JSONObject requestData = buildImageGenerationRequest(appId, uid, prompt, imageSize);

        try {
            String requestUrl = buildAuthenticatedUrl(imageHost, apiKey, apiSecret, "POST");

            MediaType jsonMediaType = MediaType.get("application/json; charset=utf-8");
            RequestBody requestBody = RequestBody.create(requestData.toString(), jsonMediaType);

            Request request = new Request.Builder()
                    .url(requestUrl)
                    .post(requestBody)
                    .build();

            try (Response response = httpClient.newCall(request).execute()) {
                ResponseBody responseBody = response.body();
                if (responseBody == null) {
                    throw new IllegalStateException("Image generation service response is empty");
                }

                String responseBodyString = responseBody.string();
                JSONObject result = JSONObject.parseObject(responseBodyString);

                log.info("Image generation request completed, user [{}], response code: {}", uid,
                        result.getIntValue("header.code", -1));

                return result;
            }
        } catch (Exception e) {
            log.error("Image generation request failed, user [{}]", uid, e);
            throw new BusinessException(ResponseEnum.SYSTEM_ERROR);
        }
    }

    /**
     * Text generation request (for opening lines generation and other functions)
     *
     * @param question Generation prompt
     * @param domain Model domain
     * @param seconds Timeout (seconds)
     * @return Generated text content
     * @throws BusinessException Business exception
     */
    public String generateText(String question, String domain, int seconds) throws BusinessException, InterruptedException {
        validateTextGenerationParams(question, domain, seconds);

        try {
            String authUrl = buildWebSocketAuthUrl(TEXT_HOST_URL, apiKey, apiSecret);
            String wsUrl = authUrl.replace("http://", "ws://").replace("https://", "wss://");
            Request request = new Request.Builder().url(wsUrl).build();
            CountDownLatch latch = new CountDownLatch(1);
            StringBuilder totalAnswer = new StringBuilder();

            httpClient.newWebSocket(request, new TextGenerationWebSocketListener(
                    appId, question, domain, latch, totalAnswer));

            if (!latch.await(seconds, TimeUnit.SECONDS)) {
                log.error("AI text generation request timeout, timeout: {} seconds", seconds);
                throw new BusinessException(ResponseEnum.SYSTEM_ERROR);
            }

            String result = totalAnswer.toString().trim();
            if (result.isEmpty()) {
                throw new BusinessException(ResponseEnum.SYSTEM_ERROR);
            }

            return result;
        } catch (Exception e) {
            log.error("AI text generation service call exception", e);
            if (e instanceof BusinessException) {
                throw e;
            }
            throw new BusinessException(ResponseEnum.SYSTEM_ERROR);
        }
    }

    /**
     * Validate text generation parameters
     */
    private void validateTextGenerationParams(String question, String domain, int seconds) {
        if (question == null || question.trim().isEmpty()) {
            throw new IllegalArgumentException("Generation prompt cannot be empty");
        }
        if (domain == null || domain.trim().isEmpty()) {
            throw new IllegalArgumentException("Model domain cannot be empty");
        }
        if (seconds <= 0 || seconds > 300) {
            throw new IllegalArgumentException("Timeout must be between 1-300 seconds");
        }
    }

    /**
     * Text generation WebSocket listener
     */
    private class TextGenerationWebSocketListener extends WebSocketListener {
        private final String appId;
        private final String question;
        private final String domain;
        private final CountDownLatch latch;
        private final StringBuilder totalAnswer;

        public TextGenerationWebSocketListener(String appId, String question, String domain,
                CountDownLatch latch, StringBuilder totalAnswer) {
            this.appId = appId;
            this.question = question;
            this.domain = domain;
            this.latch = latch;
            this.totalAnswer = totalAnswer;
        }

        @Override
        public void onOpen(@NotNull WebSocket webSocket, Response response) {
            if (response.code() == 101) {
                try {
                    JSONObject requestJson = buildTextGenerationRequest();
                    log.debug("Sending AI text generation request");
                    webSocket.send(requestJson.toString());
                } catch (Exception e) {
                    log.error("Failed to send AI text generation request", e);
                    webSocket.close(1000, "Request build failed");
                    latch.countDown();
                }
            } else {
                log.error("WebSocket connection failed, status code: {}", response.code());
                latch.countDown();
            }
        }

        @Override
        public void onMessage(@NotNull WebSocket webSocket, @NotNull String text) {
            try {
                WebSocketResponse response = objectMapper.readValue(text, WebSocketResponse.class);

                if (response.getHeader().getCode() != 0) {
                    log.error("AI service returned error, error code: {}, session ID: {}",
                            response.getHeader().getCode(), response.getHeader().getSid());
                    webSocket.close(1001, "AI service error");
                    latch.countDown();
                    return;
                }

                if (response.getPayload() != null && response.getPayload().getChoices() != null) {
                    List<TextContent> textList = response.getPayload().getChoices().getText();
                    if (textList != null) {
                        for (TextContent textContent : textList) {
                            if (textContent.getContent() != null) {
                                totalAnswer.append(textContent.getContent());
                            }
                        }
                    }
                }

                if (response.getHeader().getStatus() == 2) {
                    latch.countDown();
                    webSocket.close(1000, "Processing completed");
                }
            } catch (JsonProcessingException e) {
                log.error("Failed to parse WebSocket response", e);
                webSocket.close(1001, "Parse error");
                latch.countDown();
            }
        }

        @Override
        public void onFailure(@NotNull WebSocket webSocket, Throwable t, Response response) {
            log.error("WebSocket connection failed, reason: {}", t.getMessage());
            if (response != null) {
                log.error("Failure response code: {}", response.code());
            }
            latch.countDown();
        }

        private JSONObject buildTextGenerationRequest() {
            JSONObject requestJson = new JSONObject();

            // Build header
            JSONObject header = new JSONObject();
            header.put("app_id", appId);
            header.put("uid", UUID.randomUUID().toString().substring(0, 10));
            requestJson.put("header", header);

            // Build parameter
            JSONObject parameter = new JSONObject();
            JSONObject chat = new JSONObject();
            chat.put("domain", domain);
            chat.put("temperature", 0.5);
            chat.put("max_tokens", 4096);
            parameter.put("chat", chat);
            requestJson.put("parameter", parameter);

            // Build payload
            JSONObject payload = new JSONObject();
            JSONObject message = new JSONObject();
            JSONArray text = new JSONArray();
            RoleContent roleContent = new RoleContent("user", question);
            text.add(JSON.toJSON(roleContent));
            message.put("text", text);
            payload.put("message", message);
            requestJson.put("payload", payload);

            return requestJson;
        }
    }

    /**
     * Validate image size
     */
    private int validateImageSize(Integer size) {
        if (size == null) {
            return DEFAULT_IMAGE_SIZE;
        }
        if (!ALLOWED_IMAGE_SIZES.contains(size)) {
            log.warn("Unsupported image size: {}, using default size: {}", size, DEFAULT_IMAGE_SIZE);
            return DEFAULT_IMAGE_SIZE;
        }
        return size;
    }

    /**
     * Build image generation request data
     */
    private JSONObject buildImageGenerationRequest(String appId, String uid, String prompt, int size) {
        JSONObject request = new JSONObject();

        // Build header
        JSONObject header = new JSONObject();
        header.put("app_id", appId);
        header.put("uid", uid);
        request.put("header", header);

        // Build parameter
        JSONObject parameter = new JSONObject();
        JSONObject chat = new JSONObject();
        chat.put("domain", IMAGE_GENERATION_DOMAIN);
        chat.put("width", size);
        chat.put("height", size);
        parameter.put("chat", chat);
        request.put("parameter", parameter);

        // Build payload
        JSONObject payload = new JSONObject();
        JSONObject message = new JSONObject();
        JSONArray text = new JSONArray();

        Map<String, String> roleMessage = new HashMap<>();
        roleMessage.put("role", "user");
        roleMessage.put("content", prompt);
        text.add(roleMessage);

        message.put("text", text);
        payload.put("message", message);
        request.put("payload", payload);

        return request;
    }

    /**
     * Build authenticated request URL
     */
    private String buildAuthenticatedUrl(String requestUrl, String apiKey, String apiSecret, String method) {
        try {
            URI uri = URI.create(requestUrl.replace("ws://", "http://").replace("wss://", "https://"));

            SimpleDateFormat format = new SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss z", Locale.US);
            format.setTimeZone(TimeZone.getTimeZone("GMT"));
            String date = format.format(new Date());
            String host = uri.getHost();

            String signatureString = "host: " + host + "\n" +
                    "date: " + date + "\n" +
                    method + " " + uri.getPath() + " HTTP/1.1";

            Mac mac = Mac.getInstance("hmacsha256");
            SecretKeySpec spec = new SecretKeySpec(apiSecret.getBytes(StandardCharsets.UTF_8), "hmacsha256");
            mac.init(spec);

            byte[] hexDigits = mac.doFinal(signatureString.getBytes(StandardCharsets.UTF_8));
            String signature = Base64.getEncoder().encodeToString(hexDigits);

            String authorization = String.format(
                    "hmac username=\"%s\", algorithm=\"%s\", headers=\"%s\", signature=\"%s\"",
                    apiKey, "hmac-sha256", "host date request-line", signature);

            String authBase = Base64.getEncoder().encodeToString(authorization.getBytes(StandardCharsets.UTF_8));

            return String.format("%s?authorization=%s&host=%s&date=%s",
                    requestUrl,
                    URLEncoder.encode(authBase, StandardCharsets.UTF_8),
                    URLEncoder.encode(host, StandardCharsets.UTF_8),
                    URLEncoder.encode(date, StandardCharsets.UTF_8));

        } catch (Exception e) {
            throw new IllegalArgumentException("Failed to build authentication URL", e);
        }
    }

    /**
     * Build WebSocket authentication URL (for text generation)
     */
    private String buildWebSocketAuthUrl(String hostUrl, String apiKey, String apiSecret)
            throws IllegalArgumentException {
        try {
            URI uri = URI.create(hostUrl);
            SimpleDateFormat format = new SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss z", Locale.US);
            format.setTimeZone(TimeZone.getTimeZone("GMT"));
            String date = format.format(new Date());

            String preStr = "host: " + uri.getHost() + "\n" +
                    "date: " + date + "\n" +
                    "GET " + uri.getPath() + " HTTP/1.1";

            Mac mac = Mac.getInstance("hmacsha256");
            SecretKeySpec spec = new SecretKeySpec(apiSecret.getBytes(StandardCharsets.UTF_8), "hmacsha256");
            mac.init(spec);

            byte[] hexDigits = mac.doFinal(preStr.getBytes(StandardCharsets.UTF_8));
            String sha = Base64.getEncoder().encodeToString(hexDigits);

            String authorization = String.format("api_key=\"%s\", algorithm=\"%s\", headers=\"%s\", signature=\"%s\"",
                    apiKey, "hmac-sha256", "host date request-line", sha);

            HttpUrl httpUrl = Objects.requireNonNull(HttpUrl.parse("https://" + uri.getHost() + uri.getPath()))
                    .newBuilder()
                    .addQueryParameter("authorization", Base64.getEncoder().encodeToString(authorization.getBytes(StandardCharsets.UTF_8)))
                    .addQueryParameter("date", date)
                    .addQueryParameter("host", uri.getHost())
                    .build();

            return httpUrl.toString();
        } catch (Exception e) {
            log.error("Failed to build WebSocket authentication URL", e);
            throw new IllegalArgumentException("Invalid host URL or authentication parameters", e);
        }
    }

    /**
     * WebSocket response data structure
     */
    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class WebSocketResponse {
        private ResponseHeader header;
        private ResponsePayload payload;
    }

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class ResponseHeader {
        private int code;
        private String sid;
        private int status;
    }

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class ResponsePayload {
        private ResponseChoices choices;
    }

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class ResponseChoices {
        private List<TextContent> text;
    }

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class TextContent {
        private String content;
        private String role;
    }

    /**
     * Request message role content wrapper class
     */
    @Data
    public static class RoleContent {
        private String role;
        private String content;

        public RoleContent() {}

        public RoleContent(String role, String content) {
            this.role = role;
            this.content = content;
        }
    }
}
