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

    // 允许的 Content-Type（按需扩展）
    private static final String[] ALLOWED_TYPES = {
            "image/png", "image/jpeg", "image/jpg", "image/gif", "image/webp", "image/svg+xml"
    };

    // MinIO/亚马逊分片上传最小建议：5MB
    private static final long MULTIPART_PART_SIZE = 5L * 1024 * 1024;

    /**
     * 上传图片并返回可访问的 URL（若桶策略非公开，可改为返回 key 或预签名地址）
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
                // 已知大小直传（优先）
                s3UtilClient.putObject(objectKey, in, size, contentType);
            } else {
                // 未知大小，走 multipart 分片
                s3UtilClient.putObject(objectKey, in, contentType, MULTIPART_PART_SIZE);
            }
        } catch (Exception e) {
            log.error("Upload image failed, name={}, size={}, type={}, err={}",
                    original, size, contentType, e.getMessage(), e);
            throw new BusinessException(ResponseEnum.S3_UPLOAD_ERROR);
        }
        return objectKey;
    }

    private static boolean isAllowedType(String contentType) {
        if (contentType == null)
            return false;
        for (String t : ALLOWED_TYPES) {
            if (t.equalsIgnoreCase(contentType))
                return true;
        }
        // 兜底允许 image/*（可按需关闭）
        return contentType.toLowerCase(Locale.ROOT).startsWith("image/");
    }

    private static String normalizeContentType(String ct) {
        if (ct == null || ct.isBlank())
            return "application/octet-stream";
        return ct.trim();
    }

    private static String buildSafeFileName(String original, String contentType) {
        // 生成可追踪但不暴露原名的文件名：sparkBot_<uuid>.<ext>
        String ext = guessExtension(original, contentType);
        String uuid = UUID.randomUUID().toString().replace("-", "");
        return "sparkBot_" + uuid + (ext.isEmpty() ? "" : "." + ext);
    }

    private static String guessExtension(String original, String contentType) {
        // 先从原始文件名取扩展名
        String ext = "";
        if (original != null) {
            String clean = stripUnsafe(original);
            int dot = clean.lastIndexOf('.');
            if (dot > -1 && dot < clean.length() - 1) {
                ext = clean.substring(dot + 1);
            }
        }
        // 内容类型兜底推断
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
                    ext = ""; // 保持无后缀
            }
        }
        return ext.toLowerCase(Locale.ROOT);
    }

    private static String stripUnsafe(String name) {
        // 去掉空白与危险字符，避免路径穿越；保留基本可读性
        String cleaned = new String(name.getBytes(StandardCharsets.UTF_8), StandardCharsets.UTF_8)
                .replaceAll("\\s+", "")
                .replaceAll("[\\\\/:*?\"<>|]+", "_");
        // 防止包含路径
        cleaned = cleaned.replaceAll("\\.\\.+", ".");
        cleaned = cleaned.replaceAll("^\\.+", "");
        return cleaned;
    }
}
