package com.iflytek.astron.console.commons.util;

import com.iflytek.astron.console.commons.constant.ResponseEnum;
import com.iflytek.astron.console.commons.exception.BusinessException;
import io.minio.GetPresignedObjectUrlArgs;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
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
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/** Concise S3 (MinIO) client utility providing upload and presign capabilities. */
@Slf4j
@Component
public class S3ClientUtil {

    @Value("${s3.endpoint}")
    private String endpoint;

    @Value("${s3.accessKey}")
    private String accessKey;

    @Value("${s3.secretKey}")
    private String secretKey;

    @Getter
    @Value("${s3.bucket}")
    private String defaultBucket;

    @Getter
    @Value("${s3.presignExpirySeconds:600}")
    private int presignExpirySeconds;

    private MinioClient minioClient;

    @PostConstruct
    public void init() {
        this.minioClient = MinioClient.builder().endpoint(endpoint).credentials(accessKey, secretKey).build();
    }

    /**
     * Upload object (stream). Caller is responsible for closing the input stream.
     *
     * @param bucketName target bucket
     * @param objectKey object key (path)
     * @param contentType MIME type, e.g., "application/octet-stream" or a specific type
     * @param inputStream input stream
     * @param objectSize total object size (-1 if unknown, provide partSize)
     * @param partSize part size (required when objectSize=-1, recommend >= 10MB)
     * @return uploaded object URL
     */
    public String uploadObject(String bucketName, String objectKey, String contentType, InputStream inputStream, long objectSize, long partSize) {
        try {
            PutObjectArgs.Builder builder = PutObjectArgs.builder().bucket(bucketName).object(objectKey).stream(inputStream, objectSize, partSize);

            if (contentType != null && !contentType.isEmpty()) {
                builder.contentType(contentType);
            }

            minioClient.putObject(builder.build());

            // Build object URL
            return buildObjectUrl(bucketName, objectKey);
        } catch (ErrorResponseException | InsufficientDataException | InternalException | InvalidKeyException | InvalidResponseException | IOException | NoSuchAlgorithmException | ServerException | XmlParserException e) {
            if (log.isErrorEnabled()) {
                log.error("S3 error on upload: {}", e.getMessage(), e);
            }
            throw new BusinessException(ResponseEnum.S3_UPLOAD_ERROR);
        }
    }

    /**
     * Build object URL.
     *
     * @param bucketName bucket name
     * @param objectKey object key
     * @return full object URL
     */
    private String buildObjectUrl(String bucketName, String objectKey) {
        return String.format("%s/%s/%s", endpoint, bucketName, objectKey);
    }

    /**
     * Upload object to default bucket (stream). Caller closes the stream.
     *
     * @param objectKey object key (path)
     * @param contentType MIME type
     * @param inputStream input stream
     * @param objectSize total object size (-1 if unknown, provide partSize)
     * @param partSize part size (required when objectSize=-1, recommend >= 10MB)
     * @return uploaded object URL
     */
    public String uploadObject(String objectKey, String contentType, InputStream inputStream, long objectSize, long partSize) {
        return uploadObject(defaultBucket, objectKey, contentType, inputStream, objectSize, partSize);
    }


    /**
     * Upload byte array.
     *
     * @param bucketName target bucket
     * @param objectKey object key (path)
     * @param contentType MIME type
     * @param data byte array
     * @return uploaded object URL
     */
    public String uploadObject(String bucketName, String objectKey, String contentType, byte[] data) {
        try (InputStream inputStream = new ByteArrayInputStream(data)) {
            return uploadObject(bucketName, objectKey, contentType, inputStream, data.length, -1);
        } catch (IOException e) {
            // ByteArrayInputStream.close won't throw IOException; present to satisfy try-with-resources
            throw new BusinessException(ResponseEnum.S3_UPLOAD_ERROR);
        }
    }

    /**
     * Upload byte array to default bucket.
     *
     * @param objectKey object key (path)
     * @param contentType MIME type
     * @param data byte array
     * @return uploaded object URL
     */
    public String uploadObject(String objectKey, String contentType, byte[] data) {
        return uploadObject(defaultBucket, objectKey, contentType, data);
    }

    /**
     * Simplified upload with auto-detected file size. Caller closes the stream.
     *
     * @param bucketName target bucket
     * @param objectKey object key (path)
     * @param contentType MIME type
     * @param inputStream input stream
     * @return uploaded object URL
     */
    public String uploadObject(String bucketName, String objectKey, String contentType, InputStream inputStream) {
        // Use -1 as objectSize; MinIO will use multipart upload (recommend 5MB part size)
        return uploadObject(bucketName, objectKey, contentType, inputStream, -1, 5L * 1024 * 1024);
    }

    /**
     * Simplified upload to default bucket; auto-detect size. Caller closes the stream.
     *
     * @param objectKey object key (path)
     * @param contentType MIME type
     * @param inputStream input stream
     * @return uploaded object URL
     */
    public String uploadObject(String objectKey, String contentType, InputStream inputStream) {
        return uploadObject(defaultBucket, objectKey, contentType, inputStream);
    }

    /**
     * Generate a presigned PUT URL for browser direct upload.
     *
     * @param bucketName target bucket
     * @param objectKey object key
     * @param expirySeconds expiry in seconds (MinIO requires 1..604800)
     * @return URL usable for HTTP PUT
     */
    public String generatePresignedPutUrl(String bucketName, String objectKey, int expirySeconds) {
        try {
            return minioClient.getPresignedObjectUrl(GetPresignedObjectUrlArgs.builder().method(Method.PUT).bucket(bucketName).object(objectKey).expiry(expirySeconds).build());
        } catch (ErrorResponseException | InsufficientDataException | InternalException | InvalidKeyException | InvalidResponseException | IOException | NoSuchAlgorithmException | XmlParserException | ServerException e) {
            log.debug("S3 error on presign:", e);
            throw new BusinessException(ResponseEnum.S3_PRESIGN_ERROR);
        }
    }

    /**
     * Generate a presigned PUT URL in the default bucket using default expiry.
     *
     * @param objectKey object key
     * @return URL usable for HTTP PUT
     */
    public String generatePresignedPutUrl(String objectKey) {
        return generatePresignedPutUrl(defaultBucket, objectKey, presignExpirySeconds);
    }
}
