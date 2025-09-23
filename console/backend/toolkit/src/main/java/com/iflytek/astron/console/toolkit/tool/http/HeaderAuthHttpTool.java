package com.iflytek.astron.console.toolkit.tool.http;

import com.alibaba.fastjson2.JSON;
import lombok.extern.slf4j.Slf4j;
import okhttp3.*;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.text.SimpleDateFormat;
import java.util.*;

/**
 * HTTP client tool with header-based authentication. This tool provides authenticated HTTP
 * operations (GET, POST, PUT, DELETE, PATCH) with HMAC signature authentication.
 *
 * @author astron-console-toolkit
 */
@Slf4j
public class HeaderAuthHttpTool {

    /**
     * Executes an authenticated HTTP PUT request.
     *
     * @param url the target URL
     * @param apiKey the API key for authentication
     * @param apiSecret the API secret for signing
     * @param body the request body as JSON string
     * @return the response body as string
     * @throws IOException if the HTTP request fails
     * @throws NoSuchAlgorithmException if the signature algorithm is not available
     * @throws InvalidKeyException if the API secret is invalid
     */
    public static String put(String url, String apiKey, String apiSecret, String body) throws IOException, NoSuchAlgorithmException, InvalidKeyException {
        AssembleParam param = new AssembleParam();
        param.setApiKey(apiKey);
        param.setApiSecret(apiSecret);
        param.setUrl(url);
        param.setMethod("PUT");
        param.setBody(body.getBytes(StandardCharsets.UTF_8));
        Map<String, String> headMap = assemble(param);
        RequestBody requestBody = RequestBody.create(body, MediaType.parse("application/json;charset=utf-8"));
        Request.Builder build = new Request.Builder().url(url).//
                addHeader("Content-Type", "application/json").//
                addHeader("Date", headMap.get("date")).//
                addHeader("Digest", headMap.get("digest")).//
                addHeader("Host", headMap.get("host"));
        build.addHeader("Authorization", headMap.get("authorization"));
        Request request = build.put(requestBody).build();
        OkHttpClient client = new OkHttpClient.Builder().build();
        String res;
        try (Response resp = client.newCall(request).execute()) {
            res = JSON.parse(Objects.requireNonNull(resp.body()).bytes()).toString();
        }
        log.debug("HeaderAuthHttpTool [{}]url = {}, body = {}, resp = {}", param.getMethod(), url, body, res);
        return res;
    }

    /**
     * Executes an authenticated HTTP DELETE request.
     *
     * @param url the target URL
     * @param apiKey the API key for authentication
     * @param apiSecret the API secret for signing
     * @param body the request body as JSON string
     * @return the response body as string
     * @throws IOException if the HTTP request fails
     * @throws NoSuchAlgorithmException if the signature algorithm is not available
     * @throws InvalidKeyException if the API secret is invalid
     */
    public static String delete(String url, String apiKey, String apiSecret, String body) throws IOException, NoSuchAlgorithmException, InvalidKeyException {
        AssembleParam param = new AssembleParam();
        param.setApiKey(apiKey);
        param.setApiSecret(apiSecret);
        param.setUrl(url);
        param.setMethod("DELETE");
        param.setBody(body.getBytes(StandardCharsets.UTF_8));
        Map<String, String> headMap = assemble(param);
        RequestBody requestBody = RequestBody.create(body, MediaType.parse("application/json;charset=utf-8"));
        Request.Builder build = new Request.Builder().url(url).//
                addHeader("Content-Type", "application/json").//
                addHeader("Date", headMap.get("date")).//
                addHeader("Digest", headMap.get("digest")).//
                addHeader("Host", headMap.get("host"));
        build.addHeader("Authorization", headMap.get("authorization"));
        Request request = build.delete(requestBody).build();
        OkHttpClient client = new OkHttpClient.Builder().build();
        String res;
        try (Response resp = client.newCall(request).execute()) {
            res = JSON.parse(Objects.requireNonNull(resp.body()).bytes()).toString();
        }
        log.debug("HeaderAuthHttpTool [{}]url = {}, body = {}, resp = {}", param.getMethod(), url, body, res);
        return res;
    }

    /**
     * Executes an authenticated HTTP GET request.
     *
     * @param url the target URL
     * @param apiKey the API key for authentication
     * @param apiSecret the API secret for signing
     * @return the response body as string
     * @throws NoSuchAlgorithmException if the signature algorithm is not available
     * @throws InvalidKeyException if the API secret is invalid
     * @throws IOException if the HTTP request fails
     */
    public static String get(String url, String apiKey, String apiSecret) throws NoSuchAlgorithmException, InvalidKeyException, IOException {
        AssembleParam param = new AssembleParam();
        param.setApiKey(apiKey);
        param.setApiSecret(apiSecret);
        param.setUrl(url);
        param.setMethod("GET");
        Map<String, String> headMap = assemble(param);
        Request.Builder build = new Request.Builder().url(url).//
                addHeader("Content-Type", "text/html").//
                addHeader("Date", headMap.get("date")).//
                addHeader("Host", headMap.get("host"));
        build.addHeader("Authorization", headMap.get("authorization"));
        Request request = build.get().build();
        OkHttpClient client = new OkHttpClient.Builder().build();
        String res;
        try (Response resp = client.newCall(request).execute()) {
            log.info("HeaderAuthHttpTool get resp = {}", resp);
            ResponseBody body = resp.body();
            res = JSON.parse(Objects.requireNonNull(body).bytes()).toString();
        }
        log.debug(url + " call result: " + res);
        return res;
    }

    /**
     * Executes an authenticated HTTP POST request.
     *
     * @param url the target URL
     * @param apiKey the API key for authentication
     * @param apiSecret the API secret for signing
     * @param body the request body as JSON string
     * @return the response body as string
     * @throws IOException if the HTTP request fails
     * @throws NoSuchAlgorithmException if the signature algorithm is not available
     * @throws InvalidKeyException if the API secret is invalid
     */
    public static String post(String url, String apiKey, String apiSecret, String body) throws IOException, NoSuchAlgorithmException, InvalidKeyException {
        System.out.println(body);
        AssembleParam param = new AssembleParam();
        param.setApiKey(apiKey);
        param.setApiSecret(apiSecret);
        param.setUrl(url);
        param.setMethod("POST");
        param.setBody(body.getBytes(StandardCharsets.UTF_8));
        Map<String, String> headMap = assemble(param);
        RequestBody requestBody = RequestBody.create(body, MediaType.parse("application/json;charset=utf-8"));
        Request.Builder build = new Request.Builder().url(url).//
                addHeader("Content-Type", "application/json").//
                addHeader("Date", headMap.get("date")).//
                addHeader("Digest", headMap.get("digest")).//
                addHeader("Host", headMap.get("host"));
        build.addHeader("Authorization", headMap.get("authorization"));
        Request request = build.post(requestBody).build();
        OkHttpClient client = new OkHttpClient.Builder().build();
        String res;
        try (Response resp = client.newCall(request).execute()) {
            res = JSON.parse(Objects.requireNonNull(resp.body()).bytes()).toString();
        }
        log.debug("HeaderAuthHttpTool [{}]url = {}, body = {}, resp = {}", param.getMethod(), url, body, res);
        return res;
    }

    /**
     * Executes an authenticated HTTP PATCH request.
     *
     * @param url the target URL
     * @param apiKey the API key for authentication
     * @param apiSecret the API secret for signing
     * @param body the request body as JSON string
     * @return the response body as string
     * @throws IOException if the HTTP request fails
     * @throws NoSuchAlgorithmException if the signature algorithm is not available
     * @throws InvalidKeyException if the API secret is invalid
     */
    public static String patch(String url, String apiKey, String apiSecret, String body) throws IOException, NoSuchAlgorithmException, InvalidKeyException {
        System.out.println(body);
        AssembleParam param = new AssembleParam();
        param.setApiKey(apiKey);
        param.setApiSecret(apiSecret);
        param.setUrl(url);
        param.setMethod("PATCH");
        param.setBody(body.getBytes(StandardCharsets.UTF_8));
        Map<String, String> headMap = assemble(param);
        RequestBody requestBody = RequestBody.create(body, MediaType.parse("application/json;charset=utf-8"));
        Request.Builder build = new Request.Builder().url(url).//
                addHeader("Content-Type", "application/json").//
                addHeader("Date", headMap.get("date")).//
                addHeader("Digest", headMap.get("digest")).//
                addHeader("Host", headMap.get("host"));
        build.addHeader("Authorization", headMap.get("authorization"));
        Request request = build.patch(requestBody).build();
        OkHttpClient client = new OkHttpClient.Builder().build();
        String res;
        try (Response resp = client.newCall(request).execute()) {
            res = JSON.parse(Objects.requireNonNull(resp.body()).bytes()).toString();
        }
        log.debug("HeaderAuthHttpTool [{}]url = {}, body = {}, resp = {}", param.getMethod(), url, body, res);
        return res;
    }


    /**
     * Assembles HTTP headers with HMAC signature authentication.
     *
     * @param param the parameters for assembling authentication headers
     * @return a map containing the authentication headers (date, digest, host, authorization)
     * @throws NoSuchAlgorithmException if the signature algorithm is not available
     * @throws MalformedURLException if the URL is malformed
     * @throws InvalidKeyException if the API secret is invalid
     */
    public static Map<String, String> assemble(AssembleParam param) throws NoSuchAlgorithmException, MalformedURLException, InvalidKeyException {
        Map<String, String> headMap = new HashMap<>();
        SimpleDateFormat format = new SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss z", Locale.US);
        format.setTimeZone(TimeZone.getTimeZone("GMT"));
        String date = format.format(new Date());
        headMap.put("date", date);
        if (param.getBody() != null && param.getBody().length > 0) {
            MessageDigest messageDigest = MessageDigest.getInstance("SHA-256");
            messageDigest.update(param.getBody());
            String digest = "SHA-256=" + Base64.getEncoder().encodeToString(messageDigest.digest());
            headMap.put("digest", digest);
        }
        URL url = new URL(param.getUrl());
        headMap.put("host", url.getHost());
        StringBuilder builder = new StringBuilder("host: ").append(url.getHost()).append("\n");
        builder.append("date: ").append(date).append("\n").append(param.getMethod()).//
                append(" ").append(url.getPath()).append(" HTTP/1.1");//
        if (headMap.containsKey("digest")) {
            builder.append("\n").append("digest: ").append(headMap.get("digest"));
        }
        System.out.println("builder:" + builder.toString());
        Charset charset = StandardCharsets.UTF_8;
        Mac mac = Mac.getInstance("hmacsha256");
        SecretKeySpec spec = new SecretKeySpec(param.getApiSecret().getBytes(charset), "hmacsha256");
        mac.init(spec);
        byte[] hexDigits = mac.doFinal(builder.toString().getBytes(charset));
        String sign = Base64.getEncoder().encodeToString(hexDigits);
        if (headMap.containsKey("digest")) {
            String authorization = String.format("hmac username=\"%s\", algorithm=\"%s\", headers=\"%s\", signature=\"%s\"", //
                    param.getApiKey(), "hmac-sha256", "host date request-line digest", sign);
            System.out.println(authorization);
            headMap.put("authorization", authorization);
            return headMap;
        }
        String authorization = String.format("hmac username=\"%s\", algorithm=\"%s\", headers=\"%s\", signature=\"%s\"", //
                param.getApiKey(), "hmac-sha256", "host date request-line", sign);
        headMap.put("authorization", authorization);
        return headMap;

    }
}
