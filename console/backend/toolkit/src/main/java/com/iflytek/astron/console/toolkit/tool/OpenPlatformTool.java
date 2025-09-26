package com.iflytek.astron.console.toolkit.tool;

import org.apache.commons.codec.binary.Base64;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.util.Objects;

/**
 * OpenPlatformTool provides cryptographic utilities for open platform authentication. This class
 * contains methods for generating signatures using MD5 and HMAC-SHA1 algorithms.
 *
 * @author astron-console-toolkit
 */
public class OpenPlatformTool {

    /**
     * MD5 character table for converting bytes to hexadecimal representation.
     */
    private static final char[] MD5_TABLE = {'0', '1', '2', '3', '4', '5', '6', '7', '8', '9', 'a', 'b', 'c', 'd', 'e', 'f'};

    /**
     * Generates a signature for open platform authentication. This method creates an MD5 hash of the
     * appId and timestamp, then applies HMAC-SHA1 encryption with the secret.
     *
     * @param appId the application ID
     * @param secret the secret key for encryption
     * @param ts the timestamp for signature generation
     * @return the generated signature string, or null if an error occurs
     */
    public static String getSignature(String appId, String secret, long ts) {
        try {
            String auth = md5(appId + ts);
            return hmacSHA1Encrypt(Objects.requireNonNull(auth), secret);
        } catch (SignatureException e) {
            return null;
        }
    }

    /**
     * Generates MD5 hash for the given cipher text. The message digest is a secure one-way hash
     * function that takes arbitrary-sized data and outputs a fixed-length hash value.
     *
     * @param cipherText the text to be hashed
     * @return the MD5 hash as a hexadecimal string, or null if an error occurs
     */
    private static String md5(String cipherText) {
        try {
            byte[] data = cipherText.getBytes(StandardCharsets.UTF_8);
            // Message digest is a secure one-way hash function that accepts data of any size and outputs a
            // fixed-length hash value.
            MessageDigest mdInst = MessageDigest.getInstance("MD5");

            // MessageDigest object processes data using the update method, updating the digest with the
            // specified byte array
            mdInst.update(data);

            // After the digest is updated, hash calculation is performed by calling digest() to obtain the
            // cipher text
            byte[] md = mdInst.digest();

            // Convert the cipher text to hexadecimal string format
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

    /**
     * Encrypts text using HMAC-SHA1 algorithm.
     *
     * @param encryptText the text to be encrypted
     * @param encryptKey the encryption key
     * @return the Base64 encoded HMAC-SHA1 encrypted string
     * @throws SignatureException if encryption fails due to invalid key or algorithm issues
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
        return Base64.encodeBase64String(rawHmac);
    }
}
