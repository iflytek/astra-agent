package com.iflytek.astron.console.toolkit.util;

import cn.hutool.core.util.ArrayUtil;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.Cookie;
import okhttp3.*;
import okhttp3.internal.sse.RealEventSource;
import okhttp3.sse.EventSourceListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.TimeUnit;

/**
 * HTTP utility based on OkHttp.
 *
 * <p>
 * Provides common HTTP operations (HEAD/GET/POST/PUT/PATCH/DELETE), header and query-parameter
 * builders, multipart form helpers, cookie aggregation, and SSE (Server-Sent Events) connection
 * helpers.
 * </p>
 *
 * <p>
 * <b>Thread-safety:</b> The underlying {@link OkHttpClient} is a singleton configured with a shared
 * {@link ConnectionPool} and {@link Dispatcher}.
 * </p>
 *
 * <p>
 * <b>Timeout unit:</b> All timeout constants below are in <em>seconds</em>.
 * </p>
 *
 * <author>tctan</author>
 */
public class OkHttpUtil {
    private static final Logger logger = LoggerFactory.getLogger(OkHttpUtil.class);

    /** HTTP connect timeout (seconds). */
    private static final int CONNECT_TIMEOUT = 600;
    /** HTTP write timeout (seconds). */
    private static final int WRITE_TIMEOUT = 600;
    /** HTTP read timeout (seconds). */
    private static final int READ_TIMEOUT = 600;
    /** HTTP async call timeout (seconds). */
    private static final int CALL_TIMEOUT = 600;
    /** HTTP connection pool max idle connections. */
    private static final int CONNECTION_POOL_SIZE = 256;

    /** Static shared connection pool. */
    private static final ConnectionPool CONNECTION_POOL = new ConnectionPool(CONNECTION_POOL_SIZE, 10, TimeUnit.MINUTES);

    private static final OkHttpClient HTTP_CLIENT;

    static {
        HTTP_CLIENT = initHttpClient();
    }

    /**
     * Initialize the shared {@link OkHttpClient}.
     *
     * <p>
     * Dispatcher limits:
     * </p>
     * <ul>
     * <li>Global concurrency cap: 100</li>
     * <li>Per-host concurrency cap: 50</li>
     * </ul>
     *
     * @return configured {@link OkHttpClient} instance
     */
    private static OkHttpClient initHttpClient() {
        Dispatcher dispatcher = new Dispatcher();
        dispatcher.setMaxRequests(100); // Global concurrency cap
        dispatcher.setMaxRequestsPerHost(50); // Per-host concurrency cap

        return new OkHttpClient.Builder()
                .connectTimeout(CONNECT_TIMEOUT, TimeUnit.SECONDS)
                .readTimeout(READ_TIMEOUT, TimeUnit.SECONDS)
                .writeTimeout(WRITE_TIMEOUT, TimeUnit.SECONDS)
                .callTimeout(CALL_TIMEOUT, TimeUnit.SECONDS)
                .dispatcher(dispatcher)
                .connectionPool(CONNECTION_POOL)
                .build();
    }


    /**
     * Returns a facade client cloned from the shared singleton. It shares Dispatcher and ConnectionPool
     * but is a distinct instance to avoid exposing the internal reference.
     */
    public static OkHttpClient getHttpClient() {
        return HTTP_CLIENT.newBuilder().build();
    }

    // ============================== HEAD ==============================

    /**
     * Send an HTTP HEAD request and return response body as bytes.
     *
     * @param url target URL
     * @return response body bytes
     * @throws RuntimeException if the request fails or I/O error occurs
     */
    public static byte[] headForBytes(String url) {
        Request request = new Request.Builder()
                .url(url)
                .head()
                .build();
        try {
            try (Response response = HTTP_CLIENT.newCall(request).execute()) {
                return Objects.requireNonNull(response.body()).bytes();
            }
        } catch (IOException e) {
            logger.error(e.getMessage(), e);
            throw new RuntimeException("http head failed!");
        }
    }

    /**
     * Send an HTTP HEAD request and return response body as string (platform default charset).
     *
     * @param url target URL
     * @return response body as string
     * @throws RuntimeException if the request fails or I/O error occurs
     */
    public static String head(String url) {
        return new String(headForBytes(url), StandardCharsets.UTF_8);
    }

    // ============================== GET ==============================

    /**
     * Send an HTTP GET request and return response body as bytes.
     *
     * @param url target URL
     * @return response body bytes
     * @throws RuntimeException if the request fails or I/O error occurs
     */
    public static byte[] getForBytes(String url) {
        Request request = new Request.Builder()
                .url(url)
                .get()
                .build();
        try (Response response = HTTP_CLIENT.newCall(request).execute()) {
            return Objects.requireNonNull(response.body()).bytes();
        } catch (IOException e) {
            logger.error(e.getMessage(), e);
            throw new RuntimeException("http get failed!");
        }
    }

    /**
     * Send an HTTP GET request and return response body as {@link InputStream}.
     * <p>
     * <b>Note:</b> The returned stream belongs to the underlying response body, callers should read it
     * immediately; the response is closed by try-with-resources in this method.
     * </p>
     *
     * @param url target URL
     * @return response body stream
     * @throws RuntimeException if the request fails or I/O error occurs
     */
    public static InputStream getForInputStream(String url) {
        Request request = new Request.Builder()
                .url(url)
                .get()
                .build();
        try {
            try (Response response = HTTP_CLIENT.newCall(request).execute()) {
                return Objects.requireNonNull(response.body()).byteStream();
            }
        } catch (IOException e) {
            logger.error(e.getMessage(), e);
            throw new RuntimeException("http get failed!");
        }
    }

    /**
     * Send an HTTP GET with headers and return response body as bytes.
     *
     * @param url target URL
     * @param headerMap headers to add
     * @return response body bytes
     * @throws RuntimeException if the request fails or I/O error occurs
     */
    public static byte[] getForBytes(String url, Map<String, String> headerMap) {
        Headers headers = buildHeaders(headerMap);
        Request request = new Request.Builder()
                .headers(headers)
                .url(url)
                .get()
                .build();
        try (Response response = HTTP_CLIENT.newCall(request).execute()) {
            return Objects.requireNonNull(response.body()).bytes();
        } catch (IOException e) {
            logger.error(e.getMessage(), e);
            throw new RuntimeException("http get failed!");
        }
    }

    /**
     * Send an HTTP GET with headers and query parameters, and return response body as bytes.
     *
     * @param url base URL
     * @param headerMap headers to add
     * @param urlParams query parameters (will be appended to URL)
     * @return response body bytes
     * @throws RuntimeException if the request fails or I/O error occurs
     */
    public static byte[] getForBytes(String url, Map<String, String> headerMap, Map<String, String> urlParams) {
        url = buildUrlParameter(url, urlParams);
        Headers headers = buildHeaders(headerMap);
        Request request = new Request.Builder()
                .headers(headers)
                .url(url)
                .get()
                .build();
        try (Response response = HTTP_CLIENT.newCall(request).execute()) {
            return Objects.requireNonNull(response.body()).bytes();
        } catch (IOException e) {
            logger.error(e.getMessage(), e);
            throw new RuntimeException("http get failed!");
        }
    }

    /**
     * Send an HTTP GET and return response body as string (platform default charset).
     *
     * @param url target URL
     * @return response body string
     * @throws RuntimeException if the request fails or I/O error occurs
     */
    public static String get(String url) {
        return new String(getForBytes(url), StandardCharsets.UTF_8);
    }

    /**
     * Send an HTTP GET with headers and return response body as string.
     *
     * @param url target URL
     * @param headerMap headers to add
     * @return response body string
     * @throws RuntimeException if the request fails or I/O error occurs
     */
    public static String get(String url, Map<String, String> headerMap) {
        return new String(getForBytes(url, headerMap), StandardCharsets.UTF_8);
    }

    /**
     * Send an HTTP GET with headers and query parameters, and return response body as string.
     *
     * @param url base URL
     * @param headerMap headers to add
     * @param urlParams query parameters
     * @return response body string
     * @throws RuntimeException if the request fails or I/O error occurs
     */
    public static String get(String url, Map<String, String> headerMap, Map<String, String> urlParams) {
        return new String(getForBytes(url, headerMap, urlParams), StandardCharsets.UTF_8);
    }

    // ============================== POST ==============================

    /**
     * Send an HTTP POST with optional JSON body and query parameters, return response bytes.
     *
     * @param url base URL
     * @param urlParams query parameters
     * @param body JSON string body (nullable)
     * @return response body bytes
     * @throws RuntimeException if the request fails or I/O error occurs
     */
    public static byte[] postForBytes(String url, Map<String, String> urlParams, String body) {
        url = buildUrlParameter(url, urlParams);
        RequestBody requestBody = okhttp3.internal.Util.EMPTY_REQUEST;
        if (body != null) {
            requestBody = RequestBody.create(body, MediaType.parse("application/json;charset=utf-8"));
        }
        Request request = new Request.Builder()
                .post(requestBody)
                .url(url)
                .build();
        try (Response response = HTTP_CLIENT.newCall(request).execute()) {
            return Objects.requireNonNull(response.body()).bytes();
        } catch (IOException e) {
            logger.error(e.getMessage(), e);
            throw new RuntimeException("http post failed!");
        }
    }

    /**
     * Send an HTTP POST with headers, optional JSON body and query parameters, return response bytes.
     *
     * @param url base URL
     * @param urlParams query parameters
     * @param headerMap headers to add
     * @param body JSON string body (nullable)
     * @return response body bytes
     * @throws RuntimeException if the request fails or I/O error occurs
     */
    public static byte[] postForBytes(String url, Map<String, String> urlParams, Map<String, String> headerMap, String body) {
        url = buildUrlParameter(url, urlParams);
        Headers headers = buildHeaders(headerMap);
        RequestBody requestBody = okhttp3.internal.Util.EMPTY_REQUEST;
        if (body != null) {
            requestBody = RequestBody.create(body, MediaType.parse("application/json;charset=utf-8"));
        }
        Request request = new Request.Builder()
                .headers(headers)
                .post(requestBody)
                .url(url)
                .build();
        try (Response response = HTTP_CLIENT.newCall(request).execute()) {
            return Objects.requireNonNull(response.body()).bytes();
        } catch (IOException e) {
            logger.error(e.getMessage(), e);
            throw new RuntimeException("http post failed!");
        }
    }

    /**
     * Send a multipart/form-data POST.
     *
     * @param url target URL
     * @param headerMap headers to add (nullable)
     * @param urlParams query parameters (nullable)
     * @param bodyParams form fields; values support {@link String}, {@link MultipartFile},
     *        {@code MultipartFile[]}
     * @param fileBytes raw file bytes to add as an unnamed part (nullable)
     * @return response body bytes
     * @throws IOException if the request fails or any I/O error occurs
     */
    public static byte[] postMultipartForBytes(String url, Map<String, String> headerMap, Map<String, String> urlParams, Map<String, Object> bodyParams, byte[] fileBytes) throws IOException {
        Headers headers = null;
        Request request;
        if (headerMap != null && !headerMap.isEmpty()) {
            headers = buildHeaders(headerMap);
        }
        if (urlParams != null && !urlParams.isEmpty()) {
            url = buildUrlParameter(url, urlParams);
        }
        RequestBody requestBody = buildFormDataPart(bodyParams, fileBytes);
        if (headers != null) {
            request = new Request.Builder()
                    .headers(headers)
                    .post(requestBody)
                    .url(url)
                    .build();
        } else {
            request = new Request.Builder()
                    .post(requestBody)
                    .url(url)
                    .build();
        }

        try (Response response = HTTP_CLIENT.newCall(request).execute()) {
            return Objects.requireNonNull(response.body()).bytes();
        } catch (IOException e) {
            logger.error(e.getMessage(), e);
            throw e;
        }
    }

    /**
     * Send an HTTP POST with optional JSON body and return response string.
     *
     * @param url target URL
     * @param body JSON body (nullable)
     * @return response body string
     * @throws RuntimeException if the request fails or I/O error occurs
     */
    public static String post(String url, String body) {
        return new String(postForBytes(url, null, body), StandardCharsets.UTF_8);
    }

    /**
     * Send an HTTP POST with headers and optional JSON body; return response string.
     *
     * @param url target URL
     * @param headerMap headers to add
     * @param body JSON body (nullable)
     * @return response body string
     * @throws RuntimeException if the request fails or I/O error occurs
     */
    public static String post(String url, Map<String, String> headerMap, String body) {
        return post(url, null, headerMap, body);
    }

    /**
     * Send an HTTP POST with headers, query parameters and optional JSON body; return response string.
     *
     * @param url base URL
     * @param urlParams query parameters
     * @param headerMap headers to add
     * @param body JSON body (nullable)
     * @return response body string
     * @throws RuntimeException if the request fails or I/O error occurs
     */
    public static String post(String url, Map<String, String> urlParams, Map<String, String> headerMap, String body) {
        return new String(postForBytes(url, urlParams, headerMap, body), StandardCharsets.UTF_8);
    }

    /**
     * Multipart POST shortcuts returning response string.
     *
     * @param url target URL
     * @param fileBytes raw file bytes (nullable)
     * @return response body string
     * @throws IOException if the request fails or I/O error occurs
     */
    public static String postMultipart(String url, byte[] fileBytes) throws IOException {
        return postMultipart(url, null, null, null, fileBytes);
    }

    /**
     * Multipart POST with body params returning response string.
     *
     * @param url target URL
     * @param bodyParams form fields map
     * @param fileBytes raw file bytes (nullable)
     * @return response body string
     * @throws IOException if the request fails or I/O error occurs
     */
    public static String postMultipart(String url, Map<String, Object> bodyParams, byte[] fileBytes) throws IOException {
        return postMultipart(url, null, null, bodyParams, fileBytes);
    }

    /**
     * Multipart POST with headers and query params returning response string.
     *
     * @param url target URL
     * @param headerMap headers to add (nullable)
     * @param urlParams query parameters (nullable)
     * @param fileBytes raw file bytes (nullable)
     * @return response body string
     * @throws IOException if the request fails or I/O error occurs
     */
    public static String postMultipart(String url, Map<String, String> headerMap, Map<String, String> urlParams, byte[] fileBytes) throws IOException {
        return postMultipart(url, headerMap, urlParams, null, fileBytes);
    }

    /**
     * Multipart POST with headers and query params returning response string.
     *
     * @param url target URL
     * @param headerMap headers to add (nullable)
     * @param urlParams query parameters (nullable)
     * @param bodyParams form fields map (nullable)
     * @return response body string
     * @throws IOException if the request fails or I/O error occurs
     */
    public static String postMultipart(String url, Map<String, String> headerMap, Map<String, String> urlParams, Map<String, Object> bodyParams) throws IOException {
        return postMultipart(url, headerMap, urlParams, bodyParams, null);
    }

    /**
     * Multipart POST with headers, query params, form fields and file bytes returning response string.
     *
     * @param url target URL
     * @param headerMap headers to add (nullable)
     * @param urlParams query parameters (nullable)
     * @param bodyParams form fields map (nullable)
     * @param fileBytes raw file bytes (nullable)
     * @return response body string
     * @throws IOException if the request fails or I/O error occurs
     */
    public static String postMultipart(String url, Map<String, String> headerMap, Map<String, String> urlParams, Map<String, Object> bodyParams, byte[] fileBytes) throws IOException {
        return new String(postMultipartForBytes(url, headerMap, urlParams, bodyParams, fileBytes), StandardCharsets.UTF_8);
    }

    // ============================== PUT ==============================

    /**
     * Send an HTTP PUT with optional JSON body and return response bytes.
     *
     * @param url target URL
     * @param body JSON body (nullable)
     * @return response body bytes
     * @throws RuntimeException if the request fails or I/O error occurs
     */
    public static byte[] putForBytes(String url, String body) {
        RequestBody requestBody = okhttp3.internal.Util.EMPTY_REQUEST;
        if (body != null) {
            requestBody = RequestBody.create(body, MediaType.parse("application/json;charset=utf-8"));
        }
        Request request = new Request.Builder()
                .put(requestBody)
                .url(url)
                .build();
        try (Response response = HTTP_CLIENT.newCall(request).execute()) {
            return Objects.requireNonNull(response.body()).bytes();
        } catch (IOException e) {
            logger.error(e.getMessage(), e);
            throw new RuntimeException("http put failed!");
        }
    }

    /**
     * Send an HTTP PUT with headers and optional JSON body; return response bytes.
     *
     * @param url target URL
     * @param headerMap headers to add
     * @param body JSON body (nullable)
     * @return response body bytes
     * @throws RuntimeException if the request fails or I/O error occurs
     */
    public static byte[] putForBytes(String url, Map<String, String> headerMap, String body) {
        Headers headers = buildHeaders(headerMap);
        RequestBody requestBody = okhttp3.internal.Util.EMPTY_REQUEST;
        if (body != null) {
            requestBody = RequestBody.create(body, MediaType.parse("application/json;charset=utf-8"));
        }
        Request request = new Request.Builder()
                .headers(headers)
                .put(requestBody)
                .url(url)
                .build();
        try (Response response = HTTP_CLIENT.newCall(request).execute()) {
            return Objects.requireNonNull(response.body()).bytes();
        } catch (IOException e) {
            logger.error(e.getMessage(), e);
            throw new RuntimeException("http put failed!");
        }
    }

    /**
     * Send an HTTP PUT and return response string.
     *
     * @param url target URL
     * @param body JSON body (nullable)
     * @return response body string
     * @throws RuntimeException if the request fails or I/O error occurs
     */
    public static String put(String url, String body) {
        return new String(putForBytes(url, body), StandardCharsets.UTF_8);
    }

    /**
     * Send an HTTP PUT with headers and optional JSON body; return response string.
     *
     * @param url target URL
     * @param headerMap headers to add
     * @param body JSON body (nullable)
     * @return response body string
     * @throws RuntimeException if the request fails or I/O error occurs
     */
    public static String put(String url, Map<String, String> headerMap, String body) {
        return new String(putForBytes(url, headerMap, body), StandardCharsets.UTF_8);
    }

    // ============================== PATCH ==============================

    /**
     * Send an HTTP PATCH with optional JSON body and return response bytes.
     *
     * @param url target URL
     * @param body JSON body (nullable)
     * @return response body bytes
     * @throws RuntimeException if the request fails or I/O error occurs
     */
    public static byte[] patchForBytes(String url, String body) {
        RequestBody requestBody = okhttp3.internal.Util.EMPTY_REQUEST;
        if (body != null) {
            requestBody = RequestBody.create(body, MediaType.parse("application/json;charset=utf-8"));
        }
        Request request = new Request.Builder()
                .patch(requestBody)
                .url(url)
                .build();
        try (Response response = HTTP_CLIENT.newCall(request).execute()) {
            return Objects.requireNonNull(response.body()).bytes();
        } catch (IOException e) {
            logger.error(e.getMessage(), e);
            throw new RuntimeException("http patch failed!");
        }
    }

    /**
     * Send an HTTP PATCH with headers and optional JSON body; return response bytes.
     *
     * @param url target URL
     * @param headerMap headers to add
     * @param body JSON body (nullable)
     * @return response body bytes
     * @throws RuntimeException if the request fails or I/O error occurs
     */
    public static byte[] patchForBytes(String url, Map<String, String> headerMap, String body) {
        Headers headers = buildHeaders(headerMap);
        RequestBody requestBody = okhttp3.internal.Util.EMPTY_REQUEST;
        if (body != null) {
            requestBody = RequestBody.create(body, MediaType.parse("application/json;charset=utf-8"));
        }
        Request request = new Request.Builder()
                .headers(headers)
                .patch(requestBody)
                .url(url)
                .build();
        try (Response response = HTTP_CLIENT.newCall(request).execute()) {
            return Objects.requireNonNull(response.body()).bytes();
        } catch (IOException e) {
            logger.error(e.getMessage(), e);
            throw new RuntimeException("http patch failed!");
        }
    }

    /**
     * Send an HTTP PATCH and return response string.
     *
     * @param url target URL
     * @param body JSON body (nullable)
     * @return response body string
     * @throws RuntimeException if the request fails or I/O error occurs
     */
    public static String patch(String url, String body) {
        return new String(patchForBytes(url, body), StandardCharsets.UTF_8);
    }

    /**
     * Send an HTTP PATCH with headers and optional JSON body; return response string.
     *
     * @param url target URL
     * @param headerMap headers to add
     * @param body JSON body (nullable)
     * @return response body string
     * @throws RuntimeException if the request fails or I/O error occurs
     */
    public static String patch(String url, Map<String, String> headerMap, String body) {
        return new String(patchForBytes(url, headerMap, body), StandardCharsets.UTF_8);
    }

    // ============================== DELETE ==============================

    /**
     * Send an HTTP DELETE with optional JSON body and return response bytes.
     *
     * @param url target URL
     * @param body JSON body (nullable)
     * @return response body bytes
     * @throws RuntimeException if the request fails or I/O error occurs
     */
    public static byte[] deleteForBytes(String url, String body) {
        Request request;
        if (body == null) {
            request = new Request.Builder()
                    .delete()
                    .url(url)
                    .build();
        } else {
            RequestBody requestBody = RequestBody.create(body, MediaType.parse("application/json;charset=utf-8"));
            request = new Request.Builder()
                    .delete(requestBody)
                    .url(url)
                    .build();
        }

        try (Response response = HTTP_CLIENT.newCall(request).execute()) {
            return Objects.requireNonNull(response.body()).bytes();
        } catch (IOException e) {
            logger.error(e.getMessage(), e);
            throw new RuntimeException("http delete failed!");
        }
    }

    /**
     * Send an HTTP DELETE with headers and optional JSON body; return response bytes.
     *
     * @param url target URL
     * @param headerMap headers to add
     * @param body JSON body (nullable)
     * @return response body bytes
     * @throws RuntimeException if the request fails or I/O error occurs
     */
    public static byte[] deleteForBytes(String url, Map<String, String> headerMap, String body) {
        Headers headers = buildHeaders(headerMap);
        Request request;
        if (body == null) {
            request = new Request.Builder()
                    .headers(headers)
                    .delete()
                    .url(url)
                    .build();
        } else {
            RequestBody requestBody = RequestBody.create(body, MediaType.parse("application/json;charset=utf-8"));
            request = new Request.Builder()
                    .headers(headers)
                    .delete(requestBody)
                    .url(url)
                    .build();
        }
        try (Response response = HTTP_CLIENT.newCall(request).execute()) {
            return Objects.requireNonNull(response.body()).bytes();
        } catch (IOException e) {
            logger.error(e.getMessage(), e);
            throw new RuntimeException("http delete failed!");
        }
    }

    /**
     * Send an HTTP DELETE and return response string.
     *
     * @param url target URL
     * @return response body string
     * @throws RuntimeException if the request fails or I/O error occurs
     */
    public static String delete(String url) {
        return new String(deleteForBytes(url, null), StandardCharsets.UTF_8);
    }

    /**
     * Send an HTTP DELETE with optional JSON body and return response string.
     *
     * @param url target URL
     * @param body JSON body (nullable)
     * @return response body string
     * @throws RuntimeException if the request fails or I/O error occurs
     */
    public static String delete(String url, String body) {
        return new String(deleteForBytes(url, body), StandardCharsets.UTF_8);
    }

    /**
     * Send an HTTP DELETE with headers and optional JSON body; return response string.
     *
     * @param url target URL
     * @param headerMap headers to add
     * @param body JSON body (nullable)
     * @return response body string
     * @throws RuntimeException if the request fails or I/O error occurs
     */
    public static String delete(String url, Map<String, String> headerMap, String body) {
        return new String(deleteForBytes(url, headerMap, body), StandardCharsets.UTF_8);
    }

    // ============================== Builders & Helpers ==============================

    /**
     * Build a URL by appending query parameters.
     *
     * @param url base URL
     * @param params key-value query parameters (nullable or empty allowed)
     * @return URL with appended query string (or original if no params)
     * @throws NullPointerException if {@code url} is null or parsed {@link HttpUrl} is null
     */
    private static String buildUrlParameter(String url, Map<String, String> params) {
        if (params != null && !params.isEmpty()) {
            HttpUrl.Builder builder = Objects.requireNonNull(HttpUrl.parse(url)).newBuilder();
            for (Map.Entry<String, String> entry : params.entrySet()) {
                builder.addQueryParameter(entry.getKey(), entry.getValue());
            }
            url = builder.build().toString();
        }
        return url;
    }

    /**
     * Build {@link Headers} from a map; null keys/values are ignored.
     *
     * @param headerMap headers map (nullable)
     * @return built {@link Headers} (never null)
     */
    public static Headers buildHeaders(Map<String, String> headerMap) {
        Headers.Builder headerBuilder = new Headers.Builder();
        if (headerMap != null) {
            for (Map.Entry<String, String> entry : headerMap.entrySet()) {
                if (entry.getKey() != null && entry.getValue() != null) {
                    headerBuilder.add(entry.getKey(), entry.getValue());
                }
            }
        }
        return headerBuilder.build();
    }

    /**
     * Build a multipart/form-data request body.
     *
     * <p>
     * Supported param value types:
     * </p>
     * <ul>
     * <li>{@link MultipartFile}</li>
     * <li>{@code MultipartFile[]}</li>
     * <li>Other types will be converted via {@code toString()}</li>
     * </ul>
     *
     * @param params form fields map (nullable)
     * @param fileBytes extra raw file bytes to add as an unnamed part (nullable)
     * @return built multipart {@link RequestBody}
     * @throws IOException if reading {@link MultipartFile} bytes fails
     */
    private static RequestBody buildFormDataPart(Map<String, Object> params, byte[] fileBytes) throws IOException {
        MultipartBody.Builder builder = new MultipartBody.Builder();
        builder.setType(Objects.requireNonNull(MediaType.parse("multipart/form-data")));
        if (params != null) {
            for (String key : params.keySet()) {
                // Append form field
                Object object = params.get(key);
                if (object == null) {
                    continue;
                }
                if (object instanceof MultipartFile) {
                    MultipartFile multipartFile = (MultipartFile) object;
                    builder.addFormDataPart(key, multipartFile.getOriginalFilename(),
                            RequestBody.create(multipartFile.getBytes(), MediaType.parse("multipart/form-data")));
                } else if (object instanceof MultipartFile[]) {
                    // Handle MultipartFile[] type
                    MultipartFile[] multipartFiles = (MultipartFile[]) object;
                    for (MultipartFile multipartFile : multipartFiles) {
                        builder.addFormDataPart(key, multipartFile.getOriginalFilename(),
                                RequestBody.create(multipartFile.getBytes(), MediaType.parse("multipart/form-data")));
                    }
                } else {
                    builder.addFormDataPart(key, object.toString());
                }
            }
        }
        if (fileBytes != null) {
            builder.addPart(RequestBody.create(fileBytes, MediaType.parse("multipart/form-data")));
        }

        return builder.build();
    }

    /**
     * Concatenate all cookies of the request into a single <code>Cookie</code> header string.
     *
     * @param httpServletRequest HTTP servlet request
     * @return cookie header string like "k1=v1; k2=v2", or {@code null} if no cookies
     */
    public static String getCookieString(HttpServletRequest httpServletRequest) {
        StringBuilder sb = new StringBuilder();
        Cookie[] cookies = httpServletRequest.getCookies();
        if (ArrayUtil.isEmpty(cookies)) {
            logger.warn("httpServletRequest[{}] cookie is empty", httpServletRequest);
            return null;
        }
        for (Cookie cookie : cookies) {
            sb.append(cookie.getName()).append("=").append(cookie.getValue()).append("; ");
        }
        return sb.substring(0, sb.length() - 2);
    }

    // ============================== SSE (Server-Sent Events) ==============================

    /**
     * Establish an SSE (Server-Sent Events) connection using a JSON string body.
     *
     * @param url target URL
     * @param headerMap headers to add (nullable)
     * @param body JSON body (nullable; when null, an empty request body is sent)
     * @param listener event source listener
     * @throws NullPointerException if {@code url} or {@code listener} is null
     */
    public static void connectRealEventSource(String url, Map<String, String> headerMap, String body, EventSourceListener listener) {
        Request request;
        Headers headers = buildHeaders(headerMap);
        if (body != null) {
            RequestBody requestBody = RequestBody.create(body, MediaType.parse("application/json;charset=utf-8"));
            request = new Request.Builder()
                    .url(url)
                    .headers(headers)
                    .post(requestBody)
                    .build();
        } else {
            request = new Request.Builder()
                    .url(url)
                    .headers(headers)
                    .post(okhttp3.internal.Util.EMPTY_REQUEST)
                    .build();
        }

        // Instantiate EventSource and register the listener
        RealEventSource realEventSource = new RealEventSource(request, listener);
        realEventSource.connect(HTTP_CLIENT); // The actual start of the request
    }

    /**
     * Establish an SSE (Server-Sent Events) connection using a prepared {@link RequestBody}.
     *
     * @param url target URL
     * @param headerMap headers to add (nullable)
     * @param body request body (nullable; when null, an empty body is sent)
     * @param listener event source listener
     * @throws NullPointerException if {@code url} or {@code listener} is null
     */
    public static void connectRealEventSource(String url, Map<String, String> headerMap, RequestBody body, EventSourceListener listener) {
        Request request;
        Headers headers = buildHeaders(headerMap);
        if (body != null) {
            request = new Request.Builder()
                    .url(url)
                    .headers(headers)
                    .post(body)
                    .build();
        } else {
            request = new Request.Builder()
                    .url(url)
                    .headers(headers)
                    .post(okhttp3.internal.Util.EMPTY_REQUEST)
                    .build();
        }

        // Instantiate EventSource and register the listener
        RealEventSource realEventSource = new RealEventSource(request, listener);
        realEventSource.connect(HTTP_CLIENT); // The actual start of the request
    }

    // ============================== RequestBody helpers ==============================

    /**
     * Build a UTF-8 JSON {@link RequestBody}.
     *
     * @param reqBody JSON string body
     * @return request body with content type {@code application/json;charset=utf-8}
     * @throws NullPointerException if {@code reqBody} is null
     */
    public static RequestBody buildUTF8RequestBody(String reqBody) {
        return buildRequestBody(reqBody, "application/json;charset=utf-8");
    }

    /**
     * Build a {@link RequestBody} with provided content type.
     *
     * @param reqBody string body
     * @param contentType content type (e.g., {@code application/json;charset=utf-8})
     * @return request body
     * @throws NullPointerException if {@code reqBody} or {@code contentType} is null
     */
    public static RequestBody buildRequestBody(String reqBody, String contentType) {
        return RequestBody.create(reqBody, MediaType.parse(contentType));
    }
}
