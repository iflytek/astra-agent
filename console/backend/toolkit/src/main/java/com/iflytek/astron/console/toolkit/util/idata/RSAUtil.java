package com.iflytek.astron.console.toolkit.util.idata;

import lombok.extern.slf4j.Slf4j;

import javax.crypto.Cipher;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.*;

@Slf4j
public class RSAUtil {
    private static final String ALGORITHM = "RSA";
    private static final String TRANSFORMATION = "RSA/ECB/PKCS1Padding";

    /**
     * Load public key from an input stream.
     *
     * @param in Input stream containing the public key
     * @return RSAPublicKey instance or null if failed
     * @throws Exception if any error occurs while loading the public key
     */
    public static RSAPublicKey loadPublicKey(InputStream in) throws Exception {
        try (BufferedReader br = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
            String readLine;
            StringBuilder sb = new StringBuilder();
            while ((readLine = br.readLine()) != null) {
                if (readLine.charAt(0) == '-') {
                    continue;
                } else {
                    sb.append(readLine);
                    sb.append('\r');
                }
            }
            return loadPublicKey(sb.toString());
        } catch (Exception e) {
            log.error("loadPublicKey error, return null", e);
        }
        return null;
    }

    /**
     * Load public key from a string.
     *
     * @param publicKeyStr Public key string
     * @return RSAPublicKey instance or null if failed
     * @throws Exception if any error occurs while loading the public key
     */
    public static RSAPublicKey loadPublicKey(String publicKeyStr) throws Exception {
        try {
            byte[] buffer = Base64.getDecoder().decode(publicKeyStr);
            KeyFactory keyFactory = KeyFactory.getInstance(ALGORITHM);
            X509EncodedKeySpec keySpec = new X509EncodedKeySpec(buffer);
            return (RSAPublicKey) keyFactory.generatePublic(keySpec);
        } catch (Exception e) {
            log.error("loadPublicKey error, return null", e);
        }
        return null;
    }

    /**
     * Load private key from an input stream.
     *
     * @param in Input stream containing the private key
     * @return RSAPrivateKey instance or null if failed
     * @throws Exception if any error occurs while loading the private key
     */
    public static RSAPrivateKey loadPrivateKey(InputStream in) throws Exception {
        try (BufferedReader br = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
            String readLine;
            StringBuilder sb = new StringBuilder();
            while ((readLine = br.readLine()) != null) {
                if (readLine.charAt(0) == '-') {
                    continue;
                } else {
                    sb.append(readLine);
                    sb.append('\r');
                }
            }
            return loadPrivateKey(sb.toString());
        } catch (Exception e) {
            log.error("loadPrivateKey error, return null", e);
        }
        return null;
    }

    /**
     * Load private key from a string.
     *
     * @param privateKeyStr Private key string
     * @return RSAPrivateKey instance or null if failed
     * @throws Exception if any error occurs while loading the private key
     */
    public static RSAPrivateKey loadPrivateKey(String privateKeyStr) throws Exception {
        try {
            byte[] buffer = Base64.getDecoder().decode(privateKeyStr);
            PKCS8EncodedKeySpec keySpec = new PKCS8EncodedKeySpec(buffer);
            KeyFactory keyFactory = KeyFactory.getInstance(ALGORITHM);
            return (RSAPrivateKey) keyFactory.generatePrivate(keySpec);
        } catch (Exception e) {
            log.error("loadPrivateKey error, return null", e);
        }
        return null;
    }

    /**
     * Encrypt data with a public key, returning a hex string. Splits data into chunks if larger than
     * (key length - 11).
     *
     * @param data Plaintext string
     * @param publicKey RSAPublicKey used for encryption
     * @return Encrypted string in hex format
     */
    public static String encryptByPublicKey(String data, RSAPublicKey publicKey) {
        int key_len = publicKey.getModulus().bitLength() / 8;
        String[] datas = splitString(data, key_len - 11);
        StringBuilder builder = new StringBuilder();
        try {
            for (String s : datas) {
                builder.append(bcd2Str(encryptByPublicKey(s.getBytes(StandardCharsets.UTF_8), publicKey)));
            }
        } catch (Exception e) {
            log.error("encryptByPublicKey error", e);
        }
        return builder.toString();
    }

    /**
     * Encrypt byte array with a public key.
     *
     * @param data Plaintext byte array
     * @param publicKey RSAPublicKey used for encryption
     * @return Encrypted byte array
     * @throws Exception if encryption fails
     */
    public static byte[] encryptByPublicKey(byte[] data, RSAPublicKey publicKey) throws Exception {
        Cipher cipher = Cipher.getInstance(TRANSFORMATION);
        cipher.init(Cipher.ENCRYPT_MODE, publicKey);
        return cipher.doFinal(data);
    }

    /**
     * Encrypt byte array with a private key.
     *
     * @param data Plaintext byte array
     * @param privateKey RSAPrivateKey used for encryption
     * @return Encrypted byte array
     * @throws Exception if encryption fails
     */
    public static byte[] encryptByPrivateKey(byte[] data, RSAPrivateKey privateKey) throws Exception {
        Cipher cipher = Cipher.getInstance(TRANSFORMATION);
        cipher.init(Cipher.ENCRYPT_MODE, privateKey);
        return cipher.doFinal(data);
    }

    /**
     * Encrypt data with a private key, returning a hex string. Splits data into chunks if larger than
     * (key length - 11).
     *
     * @param data Plaintext string
     * @param privateKey RSAPrivateKey used for encryption
     * @return Encrypted string in hex format
     * @throws Exception if encryption fails
     */
    public static String encryptByPrivateKey(String data, RSAPrivateKey privateKey) throws Exception {
        int key_len = privateKey.getModulus().bitLength() / 8;
        String[] datas = splitString(data, key_len - 11);
        StringBuilder builder = new StringBuilder();
        for (String s : datas) {
            builder.append(bcd2Str(encryptByPrivateKey(s.getBytes(StandardCharsets.UTF_8), privateKey)));
        }
        return builder.toString();
    }

    /**
     * Decrypt ciphertext (hex string) using a private key.
     *
     * @param data Encrypted string in hex format
     * @param privateKey RSAPrivateKey used for decryption
     * @return Decrypted plaintext string
     * @throws Exception if decryption fails
     */
    public static String decryptByPrivateKey(String data, RSAPrivateKey privateKey) throws Exception {
        int key_len = privateKey.getModulus().bitLength() / 8;
        byte[] bytes = data.getBytes(StandardCharsets.US_ASCII);
        byte[] bcd = ASCII_To_BCD(bytes, bytes.length);
        StringBuilder sb = new StringBuilder();
        byte[][] arrays = splitArray(bcd, key_len);
        for (byte[] arr : arrays) {
            sb.append(new String(decryptByPrivateKey(arr, privateKey), StandardCharsets.UTF_8));
        }
        return sb.toString();
    }

    /**
     * Decrypt byte array using a private key.
     *
     * @param data Encrypted byte array
     * @param privateKey RSAPrivateKey used for decryption
     * @return Decrypted byte array
     * @throws Exception if decryption fails
     */
    public static byte[] decryptByPrivateKey(byte[] data, RSAPrivateKey privateKey) throws Exception {
        Cipher cipher = Cipher.getInstance(TRANSFORMATION);
        cipher.init(Cipher.DECRYPT_MODE, privateKey);
        return cipher.doFinal(data);
    }

    /**
     * Decrypt ciphertext (hex string) using a public key.
     *
     * @param data Encrypted string in hex format
     * @param publicKey RSAPublicKey used for decryption
     * @return Decrypted plaintext string
     * @throws Exception if decryption fails
     */
    public static String decryptByPublicKey(String data, RSAPublicKey publicKey) throws Exception {
        int key_len = publicKey.getModulus().bitLength() / 8;
        byte[] bytes = data.getBytes(StandardCharsets.US_ASCII);
        byte[] bcd = ASCII_To_BCD(bytes, bytes.length);
        StringBuilder sb = new StringBuilder();
        byte[][] arrays = splitArray(bcd, key_len);
        for (byte[] arr : arrays) {
            sb.append(new String(decryptByPublicKey(arr, publicKey), StandardCharsets.UTF_8));
        }
        return sb.toString();
    }

    /**
     * Decrypt byte array using a public key.
     *
     * @param data Encrypted byte array
     * @param publicKey RSAPublicKey used for decryption
     * @return Decrypted byte array
     * @throws Exception if decryption fails
     */
    public static byte[] decryptByPublicKey(byte[] data, RSAPublicKey publicKey) throws Exception {
        Cipher cipher = Cipher.getInstance(TRANSFORMATION);
        cipher.init(Cipher.DECRYPT_MODE, publicKey);
        return cipher.doFinal(data);
    }

    /**
     * Convert ASCII code to BCD code.
     *
     * @param ascii ASCII byte array
     * @param asc_len Length of ASCII data
     * @return BCD byte array
     */
    private static byte[] ASCII_To_BCD(byte[] ascii, int asc_len) {
        byte[] bcd = new byte[asc_len / 2];
        int j = 0;
        for (int i = 0; i < (asc_len + 1) / 2; i++) {
            bcd[i] = asc_to_bcd(ascii[j++]);
            bcd[i] = (byte) (((j >= asc_len) ? 0x00 : asc_to_bcd(ascii[j++])) + (bcd[i] << 4));
        }
        return bcd;
    }

    private static byte asc_to_bcd(byte asc) {
        byte bcd;
        if ((asc >= '0') && (asc <= '9')) {
            bcd = (byte) (asc - '0');
        } else if ((asc >= 'A') && (asc <= 'F')) {
            bcd = (byte) (asc - 'A' + 10);
        } else if ((asc >= 'a') && (asc <= 'f')) {
            bcd = (byte) (asc - 'a' + 10);
        } else {
            bcd = (byte) (asc - 48);
        }
        return bcd;
    }

    /**
     * Convert BCD code to string.
     *
     * @param bytes BCD byte array
     * @return Converted string
     */
    private static String bcd2Str(byte[] bytes) {
        char[] temp = new char[bytes.length * 2];
        char val;
        for (int i = 0; i < bytes.length; i++) {
            val = (char) (((bytes[i] & 0xf0) >> 4) & 0x0f);
            temp[i * 2] = (char) (val > 9 ? val + 'A' - 10 : val + '0');
            val = (char) (bytes[i] & 0x0f);
            temp[i * 2 + 1] = (char) (val > 9 ? val + 'A' - 10 : val + '0');
        }
        return new String(temp);
    }

    /**
     * Split string into chunks of a specified length.
     *
     * @param string Input string
     * @param len Maximum length of each chunk
     * @return Array of string chunks
     */
    private static String[] splitString(String string, int len) {
        int x = string.length() / len;
        int y = string.length() % len;
        int z = (y != 0) ? 1 : 0;
        String[] strings = new String[x + z];
        for (int i = 0; i < x + z; i++) {
            if (i == x + z - 1 && y != 0) {
                strings[i] = string.substring(i * len, i * len + y);
            } else {
                strings[i] = string.substring(i * len, i * len + len);
            }
        }
        return strings;
    }

    /**
     * Split byte array into chunks of a specified length.
     *
     * @param data Input byte array
     * @param len Length of each chunk
     * @return 2D byte array
     */
    private static byte[][] splitArray(byte[] data, int len) {
        int x = data.length / len;
        int y = data.length % len;
        int z = (y != 0) ? 1 : 0;
        byte[][] arrays = new byte[x + z][];
        for (int i = 0; i < x + z; i++) {
            byte[] arr = new byte[len];
            if (i == x + z - 1 && y != 0) {
                System.arraycopy(data, i * len, arr, 0, y);
            } else {
                System.arraycopy(data, i * len, arr, 0, len);
            }
            arrays[i] = arr;
        }
        return arrays;
    }

    /**
     * Decrypt base64-encoded ciphertext using a private key. Used when the frontend encrypts with
     * JSEncrypt.
     *
     * @param base64CipherText Base64-encoded ciphertext
     * @param privateKey RSAPrivateKey used for decryption
     * @return Decrypted plaintext string
     * @throws Exception if decryption fails
     */
    public static String decryptByPrivateKeyBase64(String base64CipherText, RSAPrivateKey privateKey) throws Exception {
        byte[] cipherBytes = Base64.getDecoder().decode(base64CipherText);
        int keyLen = privateKey.getModulus().bitLength() / 8;
        byte[][] arrays = splitArray(cipherBytes, keyLen);
        StringBuilder result = new StringBuilder();
        for (byte[] arr : arrays) {
            result.append(new String(decryptByPrivateKey(arr, privateKey), StandardCharsets.UTF_8));
        }
        return result.toString();
    }

    /**
     * Generate an RSA key pair (2048 bits) and return both keys in Base64 format.
     *
     * @return Map with keys: "publicKey", "privateKey"
     * @throws Exception if key generation fails
     */
    public static Map<String, String> generateRsaKeyPair() throws Exception {
        Map<String, String> keyMap = new HashMap<>();
        KeyPairGenerator keyGen = KeyPairGenerator.getInstance(ALGORITHM);
        keyGen.initialize(2048);
        KeyPair keyPair = keyGen.generateKeyPair();

        String publicKeyStr = Base64.getEncoder().encodeToString(keyPair.getPublic().getEncoded());
        String privateKeyStr = Base64.getEncoder().encodeToString(keyPair.getPrivate().getEncoded());

        keyMap.put("publicKey", publicKeyStr);
        keyMap.put("privateKey", privateKeyStr);
        return keyMap;
    }
}
