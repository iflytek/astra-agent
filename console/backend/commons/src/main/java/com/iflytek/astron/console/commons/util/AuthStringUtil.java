package com.iflytek.astron.console.commons.util;


import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SignatureException;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.stream.Collectors;

public class AuthStringUtil {

    private static final char[] MD5_TABLE = {'0', '1', '2', '3', '4', '5', '6', '7', '8', '9', 'a', 'b', 'c', 'd', 'e', 'f'};

    public static String assembleAuthURL(String uri, String method, String apiKey, String apiSecret, byte[] body) throws Exception {
        URL url = new URL(uri);

        // Get date
        SimpleDateFormat format = new SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss z", Locale.US);
        format.setTimeZone(TimeZone.getTimeZone("UTC"));
        String date = format.format(new Date());

        MessageDigest instance = MessageDigest.getInstance("SHA-256");
        instance.update(body);
        String digest = "SHA256=" + Base64.getEncoder().encodeToString(instance.digest());

        String host = url.getHost();
        int port = url.getPort();
        if (port > 0) {
            host = host + ":" + port;
        }

        StringBuilder builder = new StringBuilder();
        builder.append("host: ")
                .append(host)
                .append("\n")
                .append("date: ")
                .append(date)
                .append("\n")
                .append(method)
                .append(" ")
                .append(url.getPath())
                .append(" HTTP/1.1")
                .append("\n")
                .append("digest: ")
                .append(digest);

        // Use hmac-sha256 to calculate signature
        Charset charset = StandardCharsets.UTF_8;
        Mac mac = Mac.getInstance("hmacsha256");
        SecretKeySpec spec = new SecretKeySpec(apiSecret.getBytes(charset), "hmacsha256");
        mac.init(spec);
        byte[] hexDigits = mac.doFinal(builder.toString().getBytes(charset));
        String sha = Base64.getEncoder().encodeToString(hexDigits);
        String authParam = String.format("hmac-auth api_key=\"%s\", algorithm=\"%s\", headers=\"%s\", signature=\"%s\"",
                apiKey, "hmac-sha256", "host date request-line digest", sha);
        String authorization = Base64.getEncoder().encodeToString(authParam.getBytes());

        Map<String, String> header = new HashMap<>();
        header.put("authorization", authorization);
        header.put("host", host);
        header.put("date", date);
        header.put("digest", digest);

        // Get authentication parameters
        return uri + "?" + header.entrySet()
                .stream()
                .map(entry -> {
                    try {
                        return URLEncoder.encode(entry.getKey(), String.valueOf(StandardCharsets.UTF_8)) + "=" +
                                URLEncoder.encode(entry.getValue(), String.valueOf(StandardCharsets.UTF_8));
                    } catch (Exception e) {
                        throw new RuntimeException(e.getMessage());
                    }
                })
                .collect(Collectors.joining("&"));
    }

    /**
     * Generate URL for authentication
     */
    public static String assembleRequestUrl(String httpRequestUrl, String method, String apiKey, String apiSecret) {
        URL url;
        try {
            url = new URL(httpRequestUrl);
            SimpleDateFormat format = new SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss z", Locale.US);
            format.setTimeZone(TimeZone.getTimeZone("UTC"));
            String date = format.format(new Date());
            String host = url.getHost();
            String builder = "host: " + host + "\n" +
                    "date: " + date + "\n" +
                    method + " " +
                    url.getPath() + " HTTP/1.1";
            Charset charset = StandardCharsets.UTF_8;
            Mac mac = Mac.getInstance("hmacsha256");
            SecretKeySpec spec = new SecretKeySpec(apiSecret.getBytes(charset), "hmacsha256");
            mac.init(spec);
            byte[] hexDigits = mac.doFinal(builder.getBytes(charset));
            String sha = Base64.getEncoder().encodeToString(hexDigits);
            String authorization = String.format("api_key=\"%s\", algorithm=\"%s\", headers=\"%s\", signature=\"%s\"", apiKey, "hmac-sha256", "host date request-line", sha);
            String authBase = Base64.getEncoder().encodeToString(authorization.getBytes(charset));
            return String.format("%s?authorization=%s&host=%s&date=%s", httpRequestUrl, URLEncoder.encode(authBase), URLEncoder.encode(host),
                    URLEncoder.encode(date));
        } catch (Exception e) {
            throw new RuntimeException("assemble requestUrl error:" + e.getMessage());
        }
    }

    public static Map<String, String> authMap(String httpRequestUrl, String method, String apiKey, String apiSecret, String body) {
        try {
            URL url = new URL(httpRequestUrl);
            // Get current time as signature time, RFC1123 format
            SimpleDateFormat format = new SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss z", Locale.US);
            format.setTimeZone(TimeZone.getTimeZone("GMT"));
            String date = format.format(new Date());
            MessageDigest instance = MessageDigest.getInstance("SHA-256");
            instance.update(body.getBytes(StandardCharsets.UTF_8));
            String digest = "SHA-256=" + Base64.getEncoder().encodeToString(instance.digest());
            // Concatenate signature string
            String builder = "host: " + url.getHost() + "\n" +
                    "date: " + date + "\n" +
                    method + " " +
                    url.getPath() + " HTTP/1.1" + "\n" +
                    "digest: " + digest;
            // Signature result, first do HmacSHA256 encryption, then do Base64
            Mac mac = Mac.getInstance("hmacsha256");
            SecretKeySpec spec = new SecretKeySpec(apiSecret.getBytes(StandardCharsets.UTF_8), "hmacsha256");
            mac.init(spec);
            byte[] hexDigits = mac.doFinal(builder.getBytes(StandardCharsets.UTF_8));
            String sha = Base64.getEncoder().encodeToString(hexDigits);
            // Build request parameters, no need for urlencoding at this time
            String authorization = String.format("api_key=\"%s\", algorithm=\"%s\", headers=\"%s\", signature=\"%s\"", apiKey, "hmac-sha256", "host date request-line digest", sha);
            Map<String, String> resultMap = new HashMap<>(4);
            resultMap.put("host", url.getHost());
            resultMap.put("date", date);
            resultMap.put("digest", digest);
            resultMap.put("authorization", authorization);
            return resultMap;
        } catch (Exception e) {
            throw new RuntimeException("get auth map error:" + e.getMessage());
        }
    }

    /**
     * Get signature
     *
     * @param appId Signature key
     * @param secret Signature secret
     * @return Return signature
     */
    public static String getSignature(String appId, String secret, long ts) {
        try {
            String auth = md5(appId + ts);
            return hmacSHA1Encrypt(auth, secret);
        } catch (SignatureException e) {
            return null;
        }
    }

    /**
     * SHA1 encryption
     *
     * @param encryptText Encryption text
     * @param encryptKey Encryption key
     * @return Encryption result
     */
    private static String hmacSHA1Encrypt(String encryptText, String encryptKey) throws SignatureException {
        byte[] rawHmac;
        try {
            byte[] data = encryptKey.getBytes(StandardCharsets.UTF_8);
            SecretKeySpec secretKey = new SecretKeySpec(data, "HmacSHA1");
            Mac mac = Mac.getInstance("HmacSHA1");
            mac.init(secretKey);
            byte[] text = encryptText.getBytes(StandardCharsets.UTF_8);
            rawHmac = mac.doFinal(text);
        } catch (InvalidKeyException e) {
            throw new SignatureException("InvalidKeyException:" + e.getMessage());
        } catch (NoSuchAlgorithmException e) {
            throw new SignatureException("NoSuchAlgorithmException:" + e.getMessage());
        }
        return cn.hutool.core.codec.Base64.encode(rawHmac);
    }

    private static String md5(String cipherText) {
        try {
            byte[] data = cipherText.getBytes();
            // Message digest is a secure one-way hash function that takes data of any size and outputs a
            // fixed-length hash value.
            MessageDigest mdInst = MessageDigest.getInstance("MD5");

            // MessageDigest object processes data by using the update method, updating the digest with the
            // specified byte array
            mdInst.update(data);

            // After the digest is updated, hash calculation is performed by calling digest() to obtain the
            // ciphertext
            byte[] md = mdInst.digest();

            // Convert the ciphertext to hexadecimal string format
            int j = md.length;
            char[] str = new char[j * 2];
            int k = 0;
            for (byte byte0 : md) { // i = 0
                str[k++] = MD5_TABLE[byte0 >>> 4 & 0xf]; // 5
                str[k++] = MD5_TABLE[byte0 & 0xf]; // F
            }
            // Return the encrypted string
            return new String(str);
        } catch (Exception e) {
            return null;
        }
    }
}
