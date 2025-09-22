package com.iflytek.astra.console.toolkit.tool.http;

/**
 * Parameter class for assembling authenticated HTTP requests. This class holds the necessary
 * information for generating HMAC signature authentication.
 *
 * @author astra-console-toolkit
 */
public class AssembleParam {
    /**
     * The target URL for the HTTP request.
     */
    private String url;

    /**
     * The API key for authentication.
     */
    private String apiKey;

    /**
     * The API secret used for generating signatures.
     */
    private String apiSecret;

    /**
     * The HTTP method (GET, POST, PUT, DELETE, PATCH).
     */
    private String method;

    /**
     * The request body as byte array.
     */
    private byte[] body;

    public String getUrl() {
        return url;
    }

    public void setUrl(String url) {
        this.url = url;
    }

    public String getApiKey() {
        return apiKey;
    }

    public void setApiKey(String apiKey) {
        this.apiKey = apiKey;
    }

    public String getApiSecret() {
        return apiSecret;
    }

    public void setApiSecret(String apiSecret) {
        this.apiSecret = apiSecret;
    }

    public String getMethod() {
        return method;
    }

    public void setMethod(String method) {
        this.method = method;
    }

    public byte[] getBody() {
        return body;
    }

    public void setBody(byte[] body) {
        this.body = body;
    }
}
