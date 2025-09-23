package com.iflytek.astra.console.toolkit.util;

import com.iflytek.astra.console.commons.constant.ResponseEnum;
import com.iflytek.astra.console.commons.exception.BusinessException;
import io.minio.GetPresignedObjectUrlArgs;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import io.minio.RemoveObjectArgs;
import io.minio.RemoveObjectsArgs;
import io.minio.Result;
import io.minio.errors.ErrorResponseException;
import io.minio.errors.InsufficientDataException;
import io.minio.errors.InternalException;
import io.minio.errors.InvalidResponseException;
import io.minio.errors.ServerException;
import io.minio.errors.XmlParserException;
import io.minio.http.Method;
import jakarta.annotation.PostConstruct;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;
import java.util.List;
import java.util.stream.Collectors;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * S3 utility based on MinIO (uses a unified {@link MinioClient}).
 *
 * <p>
 * Features:
 * </p>
 * <ul>
 * <li>Upload with explicit size or unknown size (multipart).</li>
 * <li>Object download and deletion (single/batch).</li>
 * <li>Direct-link URL builders and presigned PUT generation.</li>
 * </ul>
 *
 * <p>
 * Thread-safety: this component holds a single {@link MinioClient} instance, initialized once in
 * {@link #init()}.
 * </p>
 */
@Slf4j
@Component
public class S3Util {

    @Value("${s3.endpoint}")
    private String endpoint;

    @Value("${s3.accessKey}")
    private String accessKey;

    @Value("${s3.secretKey}")
    private String secretKey;

    @Getter
    @Value("${s3.bucket:}")
    private String bucketName;

    @Getter
    @Value("${s3.presignExpirySeconds:600}")
    private int presignExpirySeconds;

    /**
     * Hostname used only when composing direct links (if different from {@code endpoint}). It can be
     * the same as {@code endpoint}.
     */
    @Value("${common.amazon.s3.hostname:}")
    private String hostname;

    private MinioClient minioClient;

    /**
     * Initialize the {@link MinioClient} with endpoint and credentials.
     *
     * @throws RuntimeException never thrown by current implementation; method kept void to preserve
     *         logic
     */
    @PostConstruct
    public void init() {
        MinioClient.Builder builder = MinioClient.builder();
        builder.endpoint(endpoint);
        builder.credentials(accessKey, secretKey);
        this.minioClient = builder.build();
    }

    /* -------------------- Upload -------------------- */

    /**
     * Upload an object with an explicit content length and optional content type.
     *
     * @param key object key (path within the bucket)
     * @param input input stream containing the object data
     * @param objectSize exact length of the object in bytes
     * @param contentType optional MIME type (e.g., {@code image/png}); may be null or empty
     * @throws BusinessException when MinIO returns an error or any I/O/crypto error occurs
     */
    public void putObject(String key, InputStream input, long objectSize, String contentType) {
        try {
            PutObjectArgs.Builder builder = PutObjectArgs.builder();
            builder.bucket(bucketName);
            builder.object(key);
            builder.stream(input, objectSize, -1);
            if (contentType != null && !contentType.isEmpty()) {
                builder.contentType(contentType);
            }
            minioClient.putObject(builder.build());
        } catch (ErrorResponseException
                | InsufficientDataException
                | InternalException
                | InvalidKeyException
                | InvalidResponseException
                | IOException
                | NoSuchAlgorithmException
                | ServerException
                | XmlParserException e) {
            log.error("S3 putObject error: {}", e.getMessage(), e);
            throw new BusinessException(ResponseEnum.S3_UPLOAD_ERROR);
        }
    }

    /**
     * Upload an object of unknown size using multipart; {@code partSize} is required.
     *
     * @param key object key (path within the bucket)
     * @param input input stream containing the object data (size unknown)
     * @param contentType optional MIME type (e.g., {@code application/octet-stream}); may be null or
     *        empty
     * @param partSize multipart chunk size in bytes (recommended ≥ 5MB)
     * @throws BusinessException when MinIO returns an error or any I/O/crypto error occurs
     */
    public void putObject(String key, InputStream input, String contentType, long partSize) {
        try {
            PutObjectArgs.Builder builder = PutObjectArgs.builder();
            builder.bucket(bucketName);
            builder.object(key);
            builder.stream(input, -1, partSize);
            if (contentType != null && !contentType.isEmpty()) {
                builder.contentType(contentType);
            }
            minioClient.putObject(builder.build());
        } catch (ErrorResponseException
                | InsufficientDataException
                | InternalException
                | InvalidKeyException
                | InvalidResponseException
                | IOException
                | NoSuchAlgorithmException
                | ServerException
                | XmlParserException e) {
            log.error("S3 putObject(stream, unknown size) error: {}", e.getMessage(), e);
            throw new BusinessException(ResponseEnum.S3_UPLOAD_ERROR);
        }
    }

    /**
     * Upload a byte array with optional content type.
     *
     * @param key object key
     * @param data object bytes
     * @param contentType optional MIME type; may be null or empty
     * @throws BusinessException when upload fails
     */
    public void putObject(String key, byte[] data, String contentType) {
        try (InputStream in = new ByteArrayInputStream(data)) {
            putObject(key, in, data.length, contentType);
        } catch (IOException e) {
            // ByteArrayInputStream.close() never throws; this catch is a placeholder to keep behavior
            // consistent
            throw new BusinessException(ResponseEnum.S3_UPLOAD_ERROR);
        }
    }

    /**
     * Upload a Base64-encoded payload with optional content type.
     *
     * @param key object key
     * @param base64Data base64-encoded data
     * @param contentType optional MIME type; may be null or empty
     * @throws BusinessException when upload fails
     */
    public void putObjectBase64(String key, String base64Data, String contentType) {
        byte[] bytes = Base64.getDecoder().decode(base64Data);
        putObject(key, bytes, contentType);
    }

    /* -------------------- Download -------------------- */

    /**
     * Get an object as an input stream (the caller is responsible for closing it).
     *
     * @param key object key
     * @return a new {@link ByteArrayInputStream} wrapping the full object content, or {@code null} when
     *         any error occurs
     */
    public InputStream getObject(String key) {
        try {
            byte[] bytes =
                    minioClient
                            .getObject(io.minio.GetObjectArgs.builder().bucket(bucketName).object(key).build())
                            .readAllBytes();
            return new ByteArrayInputStream(bytes);
        } catch (Exception e) {
            log.error("S3 getObject error: {}", e.getMessage(), e);
            return null;
        }
    }

    /* -------------------- Deletion -------------------- */

    /**
     * Delete a single object.
     *
     * @param key object key
     * @throws RuntimeException never thrown by current implementation; errors are logged and swallowed
     */
    public void deleteObject(String key) {
        try {
            minioClient.removeObject(RemoveObjectArgs.builder().bucket(bucketName).object(key).build());
        } catch (Exception e) {
            log.error("S3 deleteObject error: {}", e.getMessage(), e);
        }
    }

    /**
     * Batch delete multiple objects. Errors for individual keys will be logged.
     *
     * @param keysToDelete list of object keys to delete; no-op if null or empty
     * @throws RuntimeException never thrown by current implementation; errors are logged and swallowed
     */
    public void batchDeleteObject(List<String> keysToDelete) {
        if (keysToDelete == null || keysToDelete.isEmpty()) {
            return;
        }
        try {
            Iterable<Result<io.minio.messages.DeleteError>> results =
                    minioClient.removeObjects(
                            RemoveObjectsArgs.builder()
                                    .bucket(bucketName)
                                    .objects(
                                            keysToDelete.stream()
                                                    .map(io.minio.messages.DeleteObject::new)
                                                    .collect(Collectors.toList()))
                                    .build());
            // Actively consume error results to aid troubleshooting in logs
            for (Result<io.minio.messages.DeleteError> r : results) {
                try {
                    io.minio.messages.DeleteError err = r.get();
                    log.warn(
                            "S3 batch delete error: key={}, code={}, message={}",
                            err.objectName(),
                            err.code(),
                            err.message());
                } catch (Exception ex) {
                    log.error("S3 batch delete result parse error", ex);
                }
            }
        } catch (Exception e) {
            log.error("S3 batchDeleteObject error: {}", e.getMessage(), e);
        }
    }

    /* -------------------- URL -------------------- */

    /**
     * Build a "direct link" URL (commonly used by reverse proxy or API gateway passthrough).
     *
     * <p>
     * Each path segment of {@code key} is URL-encoded individually.
     * </p>
     *
     * @param key object key
     * @return direct URL string
     */
    public String getS3Url(String key) {
        String base = (hostname == null || hostname.isEmpty()) ? endpoint : ("https://" + hostname);
        String url = base + "/" + bucketName;
        try {
            for (String p : key.split("/")) {
                url += "/" + URLEncoder.encode(p, StandardCharsets.UTF_8);
            }
        } catch (Exception e) {
            log.warn("URL encode failed, fallback to raw key", e);
            url += "/" + key;
        }
        return url;
    }

    /**
     * Get the URL prefix for the current bucket using {@code endpoint} or {@code hostname}.
     *
     * @return URL prefix ending with "/"
     */
    public String getS3Prefix() {
        String base = (hostname == null || hostname.isEmpty()) ? endpoint : ("https://" + hostname);
        return base + "/" + bucketName + "/";
    }

    /**
     * Build a direct URL for knowledge resources; uses HTTP when {@code hostname} is configured.
     *
     * @param key object key
     * @return direct URL string
     */
    public String getS3UrlForKnowledge(String key) {
        String base = (hostname == null || hostname.isEmpty()) ? endpoint : ("http://" + hostname);
        return base + "/" + bucketName + "/" + key;
    }

    /* -------------------- Presigned -------------------- */

    /**
     * Generate a presigned PUT URL for client-side direct upload.
     *
     * @param objectKey target object key
     * @param expirySeconds optional expiration in seconds; when null or not positive, falls back to
     *        {@link #presignExpirySeconds}
     * @return presigned URL string
     * @throws BusinessException when presign generation fails
     */
    public String generatePresignedPutUrl(String objectKey, Integer expirySeconds) {
        try {
            int exp = (expirySeconds != null && expirySeconds > 0) ? expirySeconds : presignExpirySeconds;
            return minioClient.getPresignedObjectUrl(
                    GetPresignedObjectUrlArgs.builder()
                            .method(Method.PUT)
                            .bucket(bucketName)
                            .object(objectKey)
                            .expiry(exp)
                            .build());
        } catch (ErrorResponseException
                | InsufficientDataException
                | InternalException
                | InvalidKeyException
                | InvalidResponseException
                | IOException
                | NoSuchAlgorithmException
                | XmlParserException
                | ServerException e) {
            log.debug("S3 presign error:", e);
            throw new BusinessException(ResponseEnum.S3_PRESIGN_ERROR);
        }
    }
}
