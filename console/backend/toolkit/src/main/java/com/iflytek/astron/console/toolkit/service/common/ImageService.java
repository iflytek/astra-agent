package com.iflytek.astron.console.toolkit.service.common;

import com.iflytek.astron.console.commons.constant.ResponseEnum;
import com.iflytek.astron.console.commons.exception.BusinessException;
import com.iflytek.astron.console.toolkit.util.S3Util;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.UUID;

@Service
@Slf4j
public class ImageService {

    @Resource
    private S3Util s3UtilClient;

    // Allowed Content-Types (extend as needed)
    private static final String[] ALLOWED_TYPES = {
            "image/png", "image/jpeg", "image/jpg", "image/gif", "image/webp", "image/svg+xml"
    };

    // Recommended minimal part size for MinIO/Amazon multipart upload: 5MB
    private static final long MULTIPART_PART_SIZE = 5L * 1024 * 1024;

    /**
     * Upload an image and return an accessible URL (if the bucket policy is not public, consider
     * returning the object key or a pre-signed URL instead).
     *
     * @param file multipart file to upload; must not be {@code null} or empty
     * @return the object key (or URL depending on bucket policy) of the uploaded image
     * @throws BusinessException if validation fails, upload fails, or an I/O error occurs
     */
    public String upload(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BusinessException(ResponseEnum.RESPONSE_FAILED, "Empty file");
        }

        final String contentType = normalizeContentType(file.getContentType());
        if (!isAllowedType(contentType)) {
            throw new BusinessException(ResponseEnum.RESPONSE_FAILED, "Unsupported content type: " + contentType);
        }

        final long size = file.getSize();

        final String original = file.getOriginalFilename();
        final String safeName = buildSafeFileName(original, contentType);
        final String objectKey = "icon/user/" + safeName;

        try (InputStream in = file.getInputStream()) {
            if (size > 0) {
                // Known content length: prefer direct upload
                s3UtilClient.putObject(objectKey, in, size, contentType);
            } else {
                // Unknown content length: fallback to multipart upload
                s3UtilClient.putObject(objectKey, in, contentType, MULTIPART_PART_SIZE);
            }
        } catch (Exception e) {
            log.error("Upload image failed, name={}, size={}, type={}, err={}",
                    original, size, contentType, e.getMessage(), e);
            throw new BusinessException(ResponseEnum.S3_UPLOAD_ERROR);
        }
        return objectKey;
    }

    /**
     * Check whether the given Content-Type is allowed.
     * <p>
     * Fallback allows any {@code image/*} if needed.
     * </p>
     *
     * @param contentType HTTP Content-Type of the file
     * @return {@code true} if allowed; {@code false} otherwise
     */
    private static boolean isAllowedType(String contentType) {
        if (contentType == null)
            return false;
        for (String t : ALLOWED_TYPES) {
            if (t.equalsIgnoreCase(contentType))
                return true;
        }
        // Fallback: allow image/* (can be disabled as needed)
        return contentType.toLowerCase(Locale.ROOT).startsWith("image/");
    }

    /**
     * Normalize the Content-Type.
     *
     * @param ct raw content type from request
     * @return trimmed content type; returns {@code application/octet-stream} if blank
     */
    private static String normalizeContentType(String ct) {
        if (ct == null || ct.isBlank())
            return "application/octet-stream";
        return ct.trim();
    }

    /**
     * Build a safe, non-identifying filename.
     * <p>
     * Pattern: {@code sparkBot_<uuid>.<ext>} where {@code <ext>} is inferred.
     * </p>
     *
     * @param original original filename (may be {@code null})
     * @param contentType HTTP Content-Type used for extension inference if needed
     * @return sanitized file name suitable for use as an object key suffix
     */
    private static String buildSafeFileName(String original, String contentType) {
        // Generate a traceable but non-identifying filename: sparkBot_<uuid>.<ext>
        String ext = guessExtension(original, contentType);
        String uuid = UUID.randomUUID().toString().replace("-", "");
        return "sparkBot_" + uuid + (ext.isEmpty() ? "" : "." + ext);
    }

    /**
     * Infer file extension by original name first, then by Content-Type as a fallback.
     *
     * @param original original filename (may be {@code null})
     * @param contentType HTTP Content-Type
     * @return lower-cased file extension without leading dot; empty string if unknown
     */
    private static String guessExtension(String original, String contentType) {
        // Prefer extension from original filename
        String ext = "";
        if (original != null) {
            String clean = stripUnsafe(original);
            int dot = clean.lastIndexOf('.');
            if (dot > -1 && dot < clean.length() - 1) {
                ext = clean.substring(dot + 1);
            }
        }
        // Fallback: infer from content type
        if (ext.isBlank() && contentType != null) {
            switch (contentType.toLowerCase(Locale.ROOT)) {
                case "image/png":
                    ext = "png";
                    break;
                case "image/jpeg":
                case "image/jpg":
                    ext = "jpg";
                    break;
                case "image/gif":
                    ext = "gif";
                    break;
                case "image/webp":
                    ext = "webp";
                    break;
                case "image/svg+xml":
                    ext = "svg";
                    break;
                default:
                    ext = ""; // keep no extension
            }
        }
        return ext.toLowerCase(Locale.ROOT);
    }

    /**
     * Remove unsafe characters to avoid path traversal and keep minimal readability.
     *
     * @param name original filename
     * @return sanitized filename without suspicious characters or path segments
     */
    private static String stripUnsafe(String name) {
        // Remove whitespaces and dangerous characters to avoid path traversal while keeping basic
        // readability
        String cleaned = new String(name.getBytes(StandardCharsets.UTF_8), StandardCharsets.UTF_8)
                .replaceAll("\\s+", "")
                .replaceAll("[\\\\/:*?\"<>|]+", "_");
        // Prevent embedded paths
        cleaned = cleaned.replaceAll("\\.\\.+", ".");
        cleaned = cleaned.replaceAll("^\\.+", "");
        return cleaned;
    }
}
