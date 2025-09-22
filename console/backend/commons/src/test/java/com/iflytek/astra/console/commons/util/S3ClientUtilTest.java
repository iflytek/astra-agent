package com.iflytek.astra.console.commons.util;

import com.iflytek.astra.console.commons.constant.ResponseEnum;
import com.iflytek.astra.console.commons.exception.BusinessException;
import io.minio.BucketExistsArgs;
import io.minio.MakeBucketArgs;
import io.minio.MinioClient;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.ConnectException;
import java.net.HttpURLConnection;
import java.net.URL;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledIf;
import org.springframework.test.util.ReflectionTestUtils;

/**
 * S3ClientUtil Integration Tests
 *
 * Requires MinIO test environment to run these tests
 *
 * Test configuration: - MinIO Endpoint: http://172.31.205.72:17900 - Admin credentials:
 * minioadmin/minioadmin - Test Bucket: astra-agent
 *
 * Note: If MinIO service is unavailable, some tests will be skipped
 */
class S3ClientUtilTest {

    private S3ClientUtil s3ClientUtil;

    // MinIO test environment configuration - from .env.dev and docker-compose
    private static final String TEST_ENDPOINT = "http://172.31.205.72:17900";
    // Use admin credentials for testing (astra-uploader user may need additional configuration)
    private static final String TEST_ACCESS_KEY = "minioadmin";
    private static final String TEST_SECRET_KEY = "minioadmin";
    private static final String TEST_BUCKET = "astra-agent";

    // Configuration for testing invalid credentials
    private static final String INVALID_ACCESS_KEY = "astra-uploader";
    private static final String INVALID_SECRET_KEY = "astra-uploader-secret";

    private static boolean minioAvailable = true;

    @BeforeEach
    void setUp() throws Exception {
        s3ClientUtil = new S3ClientUtil();

        // Use real MinIO test environment configuration
        ReflectionTestUtils.setField(s3ClientUtil, "endpoint", TEST_ENDPOINT);
        ReflectionTestUtils.setField(s3ClientUtil, "accessKey", TEST_ACCESS_KEY);
        ReflectionTestUtils.setField(s3ClientUtil, "secretKey", TEST_SECRET_KEY);
        ReflectionTestUtils.setField(s3ClientUtil, "defaultBucket", TEST_BUCKET);
        ReflectionTestUtils.setField(s3ClientUtil, "presignExpirySeconds", 600);

        // Initialize MinIO client
        s3ClientUtil.init();

        // Try to ensure test bucket exists, mark MinIO unavailable if failed
        try {
            ensureBucketExists();
        } catch (Exception e) {
            if (e.getCause() instanceof ConnectException) {
                minioAvailable = false;
                System.out.println("Warning: MinIO service is unavailable, related tests will be skipped");
            } else {
                throw e;
            }
        }
    }

    private void ensureBucketExists() throws Exception {
        MinioClient client = MinioClient.builder()
                        .endpoint(TEST_ENDPOINT)
                        .credentials(TEST_ACCESS_KEY, TEST_SECRET_KEY)
                        .build();

        boolean bucketExists = client.bucketExists(BucketExistsArgs.builder()
                        .bucket(TEST_BUCKET)
                        .build());

        if (!bucketExists) {
            client.makeBucket(MakeBucketArgs.builder()
                            .bucket(TEST_BUCKET)
                            .build());
        }
    }

    static boolean isMinioUnavailable() {
        return !minioAvailable;
    }

    @Test
    @DisabledIf("isMinioUnavailable")
    void uploadObject_success() {
        // Prepare test data
        String objectKey = "test/upload_success_" + System.currentTimeMillis() + ".txt";
        String contentType = "text/plain";
        byte[] testContent = "Hello MinIO Test!".getBytes();
        InputStream inputStream = new ByteArrayInputStream(testContent);

        // Execute test
        String result = s3ClientUtil.uploadObject(TEST_BUCKET, objectKey, contentType, inputStream, testContent.length, -1);

        // Verify returned URL format is correct
        String expectedUrl = TEST_ENDPOINT + "/" + TEST_BUCKET + "/" + objectKey;
        Assertions.assertEquals(expectedUrl, result);
    }

    @Test
    @DisabledIf("isMinioUnavailable")
    void uploadObject_withNullContentType() {
        // Prepare test data
        String objectKey = "test/upload_null_content_type_" + System.currentTimeMillis() + ".txt";
        byte[] testContent = "Test content with null content type".getBytes();
        InputStream inputStream = new ByteArrayInputStream(testContent);

        // Execute test - contentType is null
        String result = s3ClientUtil.uploadObject(TEST_BUCKET, objectKey, null, inputStream, testContent.length, -1);

        // Verify returned URL
        String expectedUrl = TEST_ENDPOINT + "/" + TEST_BUCKET + "/" + objectKey;
        Assertions.assertEquals(expectedUrl, result);
    }

    @Test
    @DisabledIf("isMinioUnavailable")
    void uploadObject_withEmptyContentType() {
        // Prepare test data
        String objectKey = "test/upload_empty_content_type_" + System.currentTimeMillis() + ".txt";
        byte[] testContent = "Test content with empty content type".getBytes();
        InputStream inputStream = new ByteArrayInputStream(testContent);

        // Execute test - contentType is empty string
        String result = s3ClientUtil.uploadObject(TEST_BUCKET, objectKey, "", inputStream, testContent.length, -1);

        // Verify returned URL
        String expectedUrl = TEST_ENDPOINT + "/" + TEST_BUCKET + "/" + objectKey;
        Assertions.assertEquals(expectedUrl, result);
    }

    @Test
    @DisabledIf("isMinioUnavailable")
    void uploadObject_withInvalidCredentials() {
        // Create an S3ClientUtil using invalid credentials
        S3ClientUtil invalidS3ClientUtil = new S3ClientUtil();
        ReflectionTestUtils.setField(invalidS3ClientUtil, "endpoint", TEST_ENDPOINT);
        ReflectionTestUtils.setField(invalidS3ClientUtil, "accessKey", INVALID_ACCESS_KEY);
        ReflectionTestUtils.setField(invalidS3ClientUtil, "secretKey", INVALID_SECRET_KEY);
        ReflectionTestUtils.setField(invalidS3ClientUtil, "defaultBucket", TEST_BUCKET);
        invalidS3ClientUtil.init();

        String objectKey = "test/should_fail.txt";
        String contentType = "text/plain";
        InputStream inputStream = new ByteArrayInputStream("test content".getBytes());

        // Verify BusinessException is thrown (due to invalid credentials)
        BusinessException exception = Assertions.assertThrows(BusinessException.class,
                        () -> invalidS3ClientUtil.uploadObject(TEST_BUCKET, objectKey, contentType, inputStream, 12, -1));

        Assertions.assertEquals(ResponseEnum.S3_UPLOAD_ERROR.getCode(), exception.getCode());
    }

    @Test
    @DisabledIf("isMinioUnavailable")
    void generatePresignedPutUrl_success() {
        // Prepare test data
        String objectKey = "test/presigned_" + System.currentTimeMillis() + ".txt";
        int expirySeconds = 3600;

        // Execute test
        String actualUrl = s3ClientUtil.generatePresignedPutUrl(TEST_BUCKET, objectKey, expirySeconds);

        // Verify result contains necessary components
        Assertions.assertNotNull(actualUrl);
        Assertions.assertTrue(actualUrl.startsWith(TEST_ENDPOINT));
        Assertions.assertTrue(actualUrl.contains(TEST_BUCKET));
        Assertions.assertTrue(actualUrl.contains(objectKey));
        Assertions.assertTrue(actualUrl.contains("X-Amz-Algorithm=AWS4-HMAC-SHA256"));
    }

    @Test
    @DisabledIf("isMinioUnavailable")
    void generatePresignedPutUrl_withInvalidCredentials() {
        // Create an S3ClientUtil using invalid credentials
        S3ClientUtil invalidS3ClientUtil = new S3ClientUtil();
        ReflectionTestUtils.setField(invalidS3ClientUtil, "endpoint", TEST_ENDPOINT);
        ReflectionTestUtils.setField(invalidS3ClientUtil, "accessKey", INVALID_ACCESS_KEY);
        ReflectionTestUtils.setField(invalidS3ClientUtil, "secretKey", INVALID_SECRET_KEY);
        ReflectionTestUtils.setField(invalidS3ClientUtil, "defaultBucket", TEST_BUCKET);
        invalidS3ClientUtil.init();

        String objectKey = "test/should_fail.txt";
        int expirySeconds = 3600;

        // Verify BusinessException is thrown (due to invalid credentials)
        BusinessException exception = Assertions.assertThrows(BusinessException.class,
                        () -> invalidS3ClientUtil.generatePresignedPutUrl(TEST_BUCKET, objectKey, expirySeconds));

        Assertions.assertEquals(ResponseEnum.S3_PRESIGN_ERROR.getCode(), exception.getCode());
    }

    @Test
    void getDefaultBucket_success() {
        // Verify getter method
        String defaultBucket = s3ClientUtil.getDefaultBucket();
        Assertions.assertEquals(TEST_BUCKET, defaultBucket);
    }

    @Test
    void getPresignExpirySeconds_success() {
        // Verify getter method
        int presignExpirySeconds = s3ClientUtil.getPresignExpirySeconds();
        Assertions.assertEquals(600, presignExpirySeconds);
    }

    @Test
    @DisabledIf("isMinioUnavailable")
    void uploadObject_withDefaultBucket_success() {
        // Prepare test data
        String objectKey = "test/default_bucket_" + System.currentTimeMillis() + ".txt";
        String contentType = "text/plain";
        byte[] testContent = "Test with default bucket".getBytes();
        InputStream inputStream = new ByteArrayInputStream(testContent);

        // Execute test - using default bucket
        String result = s3ClientUtil.uploadObject(objectKey, contentType, inputStream, testContent.length, -1);

        // Verify returned URL
        String expectedUrl = TEST_ENDPOINT + "/" + TEST_BUCKET + "/" + objectKey;
        Assertions.assertEquals(expectedUrl, result);
    }

    @Test
    @DisabledIf("isMinioUnavailable")
    void generatePresignedPutUrl_withDefaultBucketAndExpiry_success() {
        // Prepare test data
        String objectKey = "test/presigned_default_" + System.currentTimeMillis() + ".txt";

        // Execute test - using default bucket and expiry time
        String actualUrl = s3ClientUtil.generatePresignedPutUrl(objectKey);

        // Verify result
        Assertions.assertNotNull(actualUrl);
        Assertions.assertTrue(actualUrl.startsWith(TEST_ENDPOINT));
        Assertions.assertTrue(actualUrl.contains(TEST_BUCKET));
        Assertions.assertTrue(actualUrl.contains(objectKey));
        Assertions.assertTrue(actualUrl.contains("X-Amz-Algorithm=AWS4-HMAC-SHA256"));
    }

    @Test
    @DisabledIf("isMinioUnavailable")
    void uploadObject_withByteArray_success() {
        // Prepare test data
        String objectKey = "test/byte_array_" + System.currentTimeMillis() + ".txt";
        String contentType = "text/plain";
        byte[] data = "Test content from byte array".getBytes();

        // Execute test
        String result = s3ClientUtil.uploadObject(TEST_BUCKET, objectKey, contentType, data);

        // Verify returned URL
        String expectedUrl = TEST_ENDPOINT + "/" + TEST_BUCKET + "/" + objectKey;
        Assertions.assertEquals(expectedUrl, result);
    }

    @Test
    @DisabledIf("isMinioUnavailable")
    void uploadObject_simplified_success() {
        // Prepare test data
        String objectKey = "test/simplified_" + System.currentTimeMillis() + ".txt";
        String contentType = "text/plain";
        InputStream inputStream = new ByteArrayInputStream("Test simplified upload".getBytes());

        // Execute test - simplified version (auto detect size)
        String result = s3ClientUtil.uploadObject(TEST_BUCKET, objectKey, contentType, inputStream);

        // Verify returned URL
        String expectedUrl = TEST_ENDPOINT + "/" + TEST_BUCKET + "/" + objectKey;
        Assertions.assertEquals(expectedUrl, result);
    }

    @Test
    @DisabledIf("isMinioUnavailable")
    void uploadObject_toDefaultBucketWithByteArray_success() {
        // Prepare test data
        String objectKey = "test/default_bucket_byte_array_" + System.currentTimeMillis() + ".txt";
        String contentType = "text/plain";
        byte[] data = "Test content to default bucket from byte array".getBytes();

        // Execute test - upload to default bucket using byte array
        String result = s3ClientUtil.uploadObject(objectKey, contentType, data);

        // Verify returned URL
        String expectedUrl = TEST_ENDPOINT + "/" + TEST_BUCKET + "/" + objectKey;
        Assertions.assertEquals(expectedUrl, result);
    }

    @Test
    @DisabledIf("isMinioUnavailable")
    void uploadObject_toDefaultBucketSimplified_success() {
        // Prepare test data
        String objectKey = "test/default_bucket_simplified_" + System.currentTimeMillis() + ".txt";
        String contentType = "text/plain";
        InputStream inputStream = new ByteArrayInputStream("Test simplified upload to default bucket".getBytes());

        // Execute test - simplified version upload to default bucket
        String result = s3ClientUtil.uploadObject(objectKey, contentType, inputStream);

        // Verify returned URL
        String expectedUrl = TEST_ENDPOINT + "/" + TEST_BUCKET + "/" + objectKey;
        Assertions.assertEquals(expectedUrl, result);
    }

    // URL availability test helper method
    private boolean isUrlAccessible(String urlString) {
        try {
            URL url = new URL(urlString);
            HttpURLConnection connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod("HEAD");
            connection.setConnectTimeout(5000);
            connection.setReadTimeout(5000);
            int responseCode = connection.getResponseCode();
            connection.disconnect();
            return responseCode == 200;
        } catch (IOException e) {
            return false;
        }
    }

    private String readFromUrl(String urlString) throws IOException {
        URL url = new URL(urlString);
        HttpURLConnection connection = (HttpURLConnection) url.openConnection();
        connection.setRequestMethod("GET");
        connection.setConnectTimeout(5000);
        connection.setReadTimeout(5000);

        try (InputStream inputStream = connection.getInputStream()) {
            return new String(inputStream.readAllBytes());
        } finally {
            connection.disconnect();
        }
    }

    @Test
    @DisabledIf("isMinioUnavailable")
    void uploadObject_generatedUrlIsAccessible() {
        // Prepare test data
        String objectKey = "test/url_accessible_" + System.currentTimeMillis() + ".txt";
        String contentType = "text/plain";
        String testContent = "Test content for URL accessibility";
        byte[] testContentBytes = testContent.getBytes();
        InputStream inputStream = new ByteArrayInputStream(testContentBytes);

        // Execute upload
        String generatedUrl = s3ClientUtil.uploadObject(TEST_BUCKET, objectKey, contentType, inputStream, testContentBytes.length, -1);

        // Verify URL format
        String expectedUrl = TEST_ENDPOINT + "/" + TEST_BUCKET + "/" + objectKey;
        Assertions.assertEquals(expectedUrl, generatedUrl);

        // Verify if URL is accessible
        Assertions.assertTrue(isUrlAccessible(generatedUrl),
                        "Generated URL should be accessible: " + generatedUrl);

        // Verify correct content can be read through URL
        try {
            String downloadedContent = readFromUrl(generatedUrl);
            Assertions.assertEquals(testContent, downloadedContent,
                            "Content downloaded via URL should match uploaded content");
        } catch (IOException e) {
            Assertions.fail("Failed to read content via URL: " + e.getMessage());
        }
    }

    @Test
    @DisabledIf("isMinioUnavailable")
    void uploadObject_withByteArray_generatedUrlIsAccessible() {
        // Prepare test data
        String objectKey = "test/byte_array_url_accessible_" + System.currentTimeMillis() + ".txt";
        String contentType = "application/json";
        String testContent = "{\"message\": \"Hello from S3 byte array upload\", \"timestamp\": " + System.currentTimeMillis() + "}";
        byte[] data = testContent.getBytes();

        // Execute test
        String generatedUrl = s3ClientUtil.uploadObject(TEST_BUCKET, objectKey, contentType, data);

        // Verify URL format
        String expectedUrl = TEST_ENDPOINT + "/" + TEST_BUCKET + "/" + objectKey;
        Assertions.assertEquals(expectedUrl, generatedUrl);

        // Verify if URL is accessible
        Assertions.assertTrue(isUrlAccessible(generatedUrl),
                        "Generated URL should be accessible: " + generatedUrl);

        // Verify correct content can be read through URL
        try {
            String downloadedContent = readFromUrl(generatedUrl);
            Assertions.assertEquals(testContent, downloadedContent,
                            "Content downloaded via URL should match uploaded content");
        } catch (IOException e) {
            Assertions.fail("Failed to read content via URL: " + e.getMessage());
        }
    }

    @Test
    @DisabledIf("isMinioUnavailable")
    void generatePresignedPutUrl_canBeUsedForUpload() throws IOException {
        // Prepare test data
        String objectKey = "test/presigned_upload_" + System.currentTimeMillis() + ".txt";
        String testContent = "Content uploaded via presigned URL";
        byte[] testContentBytes = testContent.getBytes();

        // Generate presigned URL
        String presignedUrl = s3ClientUtil.generatePresignedPutUrl(TEST_BUCKET, objectKey, 600);

        // Verify presigned URL format
        Assertions.assertNotNull(presignedUrl);
        Assertions.assertTrue(presignedUrl.startsWith(TEST_ENDPOINT));
        Assertions.assertTrue(presignedUrl.contains(TEST_BUCKET));
        Assertions.assertTrue(presignedUrl.contains(objectKey));
        Assertions.assertTrue(presignedUrl.contains("X-Amz-Algorithm=AWS4-HMAC-SHA256"));

        // Upload file using presigned URL
        URL url = new URL(presignedUrl);
        HttpURLConnection connection = (HttpURLConnection) url.openConnection();
        connection.setRequestMethod("PUT");
        connection.setDoOutput(true);
        connection.setRequestProperty("Content-Type", "text/plain");
        connection.setConnectTimeout(10000);
        connection.setReadTimeout(10000);

        try {
            connection.getOutputStream().write(testContentBytes);
            int responseCode = connection.getResponseCode();
            Assertions.assertEquals(200, responseCode,
                            "Presigned URL upload should succeed, response code should be 200");
        } finally {
            connection.disconnect();
        }

        // Verify uploaded file can be accessed via direct URL
        String directUrl = TEST_ENDPOINT + "/" + TEST_BUCKET + "/" + objectKey;
        Assertions.assertTrue(isUrlAccessible(directUrl),
                        "Uploaded file should be accessible via direct URL");

        // Verify uploaded content is correct
        try {
            String downloadedContent = readFromUrl(directUrl);
            Assertions.assertEquals(testContent, downloadedContent,
                            "Content downloaded via direct URL should match uploaded content");
        } catch (IOException e) {
            Assertions.fail("Failed to read content via direct URL: " + e.getMessage());
        }
    }

    @Test
    @DisabledIf("isMinioUnavailable")
    void uploadObject_invalidUrl_shouldNotBeAccessible() {
        // Construct a non-existent URL
        String invalidUrl = TEST_ENDPOINT + "/" + TEST_BUCKET + "/nonexistent/file_" + System.currentTimeMillis() + ".txt";

        // Verify non-existent URL is not accessible
        Assertions.assertFalse(isUrlAccessible(invalidUrl),
                        "Non-existent file URL should not be accessible");
    }
}
