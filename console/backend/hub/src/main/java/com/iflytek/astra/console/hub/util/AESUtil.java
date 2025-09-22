package com.iflytek.astra.console.hub.util;

import lombok.extern.slf4j.Slf4j;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.HexFormat;

/**
 * AES encryption utility class using AES-256-GCM mode to provide secure encryption and decryption
 * functionality. Uses JDK 21's HexFormat for optimal performance.
 */
@Slf4j
public class AESUtil {

    private static final String ALGORITHM = "AES";
    private static final String TRANSFORMATION = "AES/GCM/NoPadding";
    private static final int IV_LENGTH = 12; // GCM recommended 96 bits (12 bytes)
    private static final int TAG_LENGTH = 128; // GCM authentication tag length 128 bits
    private static final HexFormat HEX_FORMAT = HexFormat.of();

    // Private constructor to prevent instantiation
    private AESUtil() {
        // Prevent instance creation via reflection
    }

    /**
     * Create SecretKey from string key (internal use)
     *
     * @param key Key string (32-byte hexadecimal string)
     * @return SecretKey object
     * @throws IllegalArgumentException if key format is incorrect
     */
    private static SecretKeySpec createSecretKey(String key) {
        if (key == null || key.length() != 64) { // 32 bytes = 64 hexadecimal characters
            throw new IllegalArgumentException("Key must be a 64-character hexadecimal string");
        }
        try {
            byte[] keyBytes = HEX_FORMAT.parseHex(key);
            return new SecretKeySpec(keyBytes, ALGORITHM);
        } catch (Exception e) {
            throw new IllegalArgumentException("Invalid hexadecimal key format", e);
        }
    }

    /**
     * Encrypt string
     *
     * @param plainText The plaintext to encrypt
     * @param key Key string (64-character hexadecimal)
     * @return Encrypted hexadecimal string (including IV)
     * @throws IllegalArgumentException if encryption fails
     */
    public static String encrypt(String plainText, String key) {
        try {
            SecretKeySpec secretKey = createSecretKey(key);
            byte[] encryptedBytes = encryptBytes(plainText.getBytes(StandardCharsets.UTF_8), secretKey);
            return HEX_FORMAT.formatHex(encryptedBytes);
        } catch (Exception e) {
            log.error("AES encryption failed", e);
            throw new IllegalArgumentException("AES encryption failed", e);
        }
    }

    /**
     * Decrypt string
     *
     * @param encryptedHex Encrypted hexadecimal string (including IV)
     * @param key Key string (64-character hexadecimal)
     * @return Decrypted plaintext
     * @throws IllegalArgumentException if decryption fails
     */
    public static String decrypt(String encryptedHex, String key) {
        try {
            SecretKeySpec secretKey = createSecretKey(key);
            byte[] encryptedData = HEX_FORMAT.parseHex(encryptedHex);
            byte[] decryptedBytes = decryptBytes(encryptedData, secretKey);
            return new String(decryptedBytes, StandardCharsets.UTF_8);
        } catch (Exception e) {
            log.error("AES decryption failed", e);
            throw new IllegalArgumentException("AES decryption failed", e);
        }
    }

    /**
     * Encrypt byte array
     *
     * @param data Data to encrypt
     * @param secretKey Key object
     * @return Encrypted byte array (including IV)
     * @throws IllegalArgumentException if encryption fails
     */
    private static byte[] encryptBytes(byte[] data, SecretKeySpec secretKey) {
        try {
            Cipher cipher = Cipher.getInstance(TRANSFORMATION);

            // Generate random IV
            byte[] iv = new byte[IV_LENGTH];
            SecureRandom.getInstanceStrong().nextBytes(iv);
            GCMParameterSpec gcmSpec = new GCMParameterSpec(TAG_LENGTH, iv);

            cipher.init(Cipher.ENCRYPT_MODE, secretKey, gcmSpec);
            byte[] encryptedData = cipher.doFinal(data);

            // Combine IV and encrypted data
            byte[] result = new byte[IV_LENGTH + encryptedData.length];
            System.arraycopy(iv, 0, result, 0, IV_LENGTH);
            System.arraycopy(encryptedData, 0, result, IV_LENGTH, encryptedData.length);

            return result;
        } catch (Exception e) {
            log.error("AES encryption failed", e);
            throw new IllegalArgumentException("AES encryption failed", e);
        }
    }

    /**
     * Decrypt byte array
     *
     * @param encryptedData Encrypted data (including IV)
     * @param secretKey Key object
     * @return Decrypted byte array
     * @throws IllegalArgumentException if decryption fails
     */
    private static byte[] decryptBytes(byte[] encryptedData, SecretKeySpec secretKey) {
        try {
            if (encryptedData.length < IV_LENGTH) {
                throw new IllegalArgumentException("Insufficient encrypted data length");
            }

            // Extract IV and actual encrypted data
            byte[] iv = new byte[IV_LENGTH];
            byte[] cipherData = new byte[encryptedData.length - IV_LENGTH];
            System.arraycopy(encryptedData, 0, iv, 0, IV_LENGTH);
            System.arraycopy(encryptedData, IV_LENGTH, cipherData, 0, cipherData.length);

            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            GCMParameterSpec gcmSpec = new GCMParameterSpec(TAG_LENGTH, iv);
            cipher.init(Cipher.DECRYPT_MODE, secretKey, gcmSpec);

            return cipher.doFinal(cipherData);
        } catch (Exception e) {
            log.error("AES decryption failed", e);
            throw new IllegalArgumentException("AES decryption failed", e);
        }
    }

    /**
     * Validate if the key is valid
     *
     * @param key Key string
     * @return Returns true if the key is valid, false otherwise
     */
    public static boolean isValidKey(String key) {
        try {
            createSecretKey(key);
            return true;
        } catch (Exception e) {
            return false;
        }
    }
}
