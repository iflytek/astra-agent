package com.iflytek.astra.console.toolkit.tool.http;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.*;

/**
 * URL signing tool for generating authenticated URLs. This utility is suitable for AIPaaS
 * capabilities, large language models, and other services.
 *
 * @author tctan
 */
public class HttpAuthTool {

    public static final Logger logger = LoggerFactory.getLogger(HttpAuthTool.class);

    /**
     * Default encryption algorithm: hmac-sha256
     */
    private static final String ALGORITHM_JAVA = "HmacSHA256";

    /**
     * HTTP algorithm identifier for hmac-sha256
     */
    private static final String ALGORITHM_HTTP = "hmac-sha256";


    /**
     * Assembles a request URL with authentication signature using default GET method.
     *
     * @param requestUrl the base request URL
     * @param apiKey the API key for authentication
     * @param apiSecret the API secret for signing
     * @return the authenticated URL with signature parameters
     */
    public static String assembleRequestUrl(String requestUrl, String apiKey, String apiSecret) {
        return assembleRequestUrl(requestUrl, "GET", apiKey, apiSecret, ALGORITHM_JAVA, ALGORITHM_HTTP);
    }

    /**
     * Assembles a request URL with authentication signature using specified HTTP method.
     *
     * @param requestUrl the base request URL
     * @param requestMethod the HTTP method (GET, POST, etc.)
     * @param apiKey the API key for authentication
     * @param apiSecret the API secret for signing
     * @return the authenticated URL with signature parameters
     */
    public static String assembleRequestUrl(String requestUrl, String requestMethod, String apiKey, String apiSecret) {
        return assembleRequestUrl(requestUrl, requestMethod, apiKey, apiSecret, ALGORITHM_JAVA, ALGORITHM_HTTP);
    }

    /**
     * Assembles a request URL with authentication signature using custom algorithms.
     *
     * @param requestUrl the base request URL
     * @param requestMethod the HTTP method (GET, POST, etc.)
     * @param apiKey the API key for authentication
     * @param apiSecret the API secret for signing
     * @param javaAlgorithm the Java algorithm identifier for HMAC
     * @param httpAlgorithm the HTTP algorithm identifier for the authorization header
     * @return the authenticated URL with signature parameters
     * @throws RuntimeException if URL assembly fails
     */
    public static String assembleRequestUrl(String requestUrl, String requestMethod, String apiKey, String apiSecret, String javaAlgorithm, String httpAlgorithm) {
        String httpRequestUrl = requestUrl.replace("ws://", "http://").replace("wss://", "https://");
        try {
            URL url = new URL(httpRequestUrl);
            SimpleDateFormat format = new SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss z", Locale.US);
            format.setTimeZone(TimeZone.getTimeZone("GMT"));
            String date = format.format(new Date());
            String plainText = "host: " + url.getHost() + "\ndate: " + date + "\n" + requestMethod + " " + url.getPath() + " HTTP/1.1";
            Mac mac = Mac.getInstance(javaAlgorithm);
            SecretKeySpec spec = new SecretKeySpec(apiSecret.getBytes(StandardCharsets.UTF_8), javaAlgorithm);
            mac.init(spec);
            byte[] rawHmac = mac.doFinal(plainText.getBytes(StandardCharsets.UTF_8));
            String signature = Base64.getEncoder().encodeToString(rawHmac);
            String authorization = String.format("api_key=\"%s\", algorithm=\"%s\", headers=\"%s\", signature=\"%s\"", apiKey, httpAlgorithm, "host date request-line", signature);
            String authBase = Base64.getEncoder().encodeToString(authorization.getBytes(StandardCharsets.UTF_8));
            return String.format("%s?authorization=%s&host=%s&date=%s", requestUrl, URLEncoder.encode(authBase, "UTF-8"), URLEncoder.encode(url.getHost(), "UTF-8"), URLEncoder.encode(date, "UTF-8"));
        } catch (Exception e) {
            logger.error("assemble requestUrl error: {}", e.getMessage(), e);
            throw new RuntimeException("assemble requestUrl error: " + e.getMessage());
        }
    }

    /**
     * Generates a signature for query parameters using HMAC-SHA1.
     *
     * @param accessKeySecret the access key secret for signing
     * @param queryParam the query parameters to be signed
     * @return the Base64 encoded signature
     * @throws Exception if signature generation fails
     */
    public static String signature(String accessKeySecret, Map<String, String> queryParam) throws Exception {
        // Sort parameters
        TreeMap<String, String> treeMap = new TreeMap<>(queryParam);
        // Remove signature parameter as it doesn't participate in signing
        treeMap.remove("signature");
        // Generate baseString
        StringBuilder builder = new StringBuilder();
        for (Map.Entry<String, String> entry : treeMap.entrySet()) {
            String value = entry.getValue();
            // Parameters with empty values don't participate in signing
            if (value != null && !value.isEmpty()) {
                // Parameter values need URL encoding
                String encode = URLEncoder.encode(value, StandardCharsets.UTF_8.name());
                builder.append(entry.getKey()).append("=").append(encode).append("&");
            }
        }
        // Remove the last '&' symbol
        if (builder.length() > 0) {
            builder.deleteCharAt(builder.length() - 1);
        }
        String baseString = builder.toString();
        Mac mac = Mac.getInstance("HmacSHA1");
        SecretKeySpec keySpec = new SecretKeySpec(accessKeySecret.getBytes(StandardCharsets.UTF_8),
                        StandardCharsets.UTF_8.name());
        mac.init(keySpec);
        // Get signature bytes
        byte[] signBytes = mac.doFinal(baseString.getBytes(StandardCharsets.UTF_8));
        // Base64 encode the bytes
        return Base64.getEncoder().encodeToString(signBytes);
    }

}
