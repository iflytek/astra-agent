package com.iflytek.astra.console.commons.util;


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

        // 获取日期
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

        // 使用hmac-sha256计算签名
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

        // 获取鉴权参数
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
     * 生成用于鉴权的URL
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
            // 获取当前时间作为签名时间，RFC1123格式
            SimpleDateFormat format = new SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss z", Locale.US);
            format.setTimeZone(TimeZone.getTimeZone("GMT"));
            String date = format.format(new Date());
            MessageDigest instance = MessageDigest.getInstance("SHA-256");
            instance.update(body.getBytes(StandardCharsets.UTF_8));
            String digest = "SHA-256=" + Base64.getEncoder().encodeToString(instance.digest());
            // 拼接签名字符串
            String builder = "host: " + url.getHost() + "\n" +
                            "date: " + date + "\n" +
                            method + " " +
                            url.getPath() + " HTTP/1.1" + "\n" +
                            "digest: " + digest;
            // 签名结果,先做HmacSHA256加密，再做Base64
            Mac mac = Mac.getInstance("hmacsha256");
            SecretKeySpec spec = new SecretKeySpec(apiSecret.getBytes(StandardCharsets.UTF_8), "hmacsha256");
            mac.init(spec);
            byte[] hexDigits = mac.doFinal(builder.getBytes(StandardCharsets.UTF_8));
            String sha = Base64.getEncoder().encodeToString(hexDigits);
            // 构建请求参数 此时不需要urlencoding
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
     * 获取签名
     *
     * @param appId    签名的key
     * @param secret 签名秘钥
     * @return 返回签名
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
     * sha1加密
     *
     * @param encryptText 加密文本
     * @param encryptKey  加密键
     * @return 加密
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
            // 信息摘要是安全的单向哈希函数，它接收任意大小的数据，并输出固定长度的哈希值。
            MessageDigest mdInst = MessageDigest.getInstance("MD5");

            // MessageDigest对象通过使用 update方法处理数据， 使用指定的byte数组更新摘要
            mdInst.update(data);

            // 摘要更新之后，通过调用digest（）执行哈希计算，获得密文
            byte[] md = mdInst.digest();

            // 把密文转换成十六进制的字符串形式
            int j = md.length;
            char[] str = new char[j * 2];
            int k = 0;
            for (byte byte0 : md) { // i = 0
                str[k++] = MD5_TABLE[byte0 >>> 4 & 0xf]; // 5
                str[k++] = MD5_TABLE[byte0 & 0xf]; // F
            }
            // 返回经过加密后的字符串
            return new String(str);
        } catch (Exception e) {
            return null;
        }
    }
}
