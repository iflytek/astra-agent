package com.iflytek.astron.console.hub.util;

import cn.hutool.core.img.ImgUtil;
import cn.hutool.core.io.IoUtil;
import com.iflytek.astron.console.commons.exception.BusinessException;
import lombok.extern.slf4j.Slf4j;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.util.Base64;

import static com.iflytek.astron.console.commons.constant.ResponseEnum.SYSTEM_ERROR;

/**
 * Image processing utility class
 *
 */
@Slf4j
public class ImageUtil {

    /**
     * Convert base64 string to InputStream
     *
     * @param base64String Base64 encoded image string
     * @return InputStream object
     */
    public static InputStream base64ToImageInputStream(String base64String) {
        if (base64String == null || base64String.trim().isEmpty()) {
            throw new IllegalArgumentException("Base64 string cannot be empty");
        }

        try {
            byte[] byteArray = Base64.getDecoder().decode(base64String);
            return new ByteArrayInputStream(byteArray);
        } catch (Exception e) {
            log.error("Failed to convert Base64 string to InputStream", e);
            throw new BusinessException(SYSTEM_ERROR);
        }
    }

    /**
     * Compress image
     *
     * @param inputStream Original image input stream
     * @param scale Compression ratio (0.0-1.0)
     * @return Compressed image input stream
     */
    public static InputStream compressImage(InputStream inputStream, float scale) {
        if (inputStream == null) {
            throw new IllegalArgumentException("Input stream cannot be empty");
        }
        if (scale <= 0 || scale > 1) {
            throw new IllegalArgumentException("Compression ratio must be between 0-1");
        }

        ByteArrayOutputStream outputStream = null;
        try {
            outputStream = new ByteArrayOutputStream();
            ImgUtil.scale(inputStream, outputStream, scale);
            return new ByteArrayInputStream(outputStream.toByteArray());
        } catch (Exception e) {
            log.error("Image compression failed, scale: {}", scale, e);
            throw new BusinessException(SYSTEM_ERROR);
        } finally {
            IoUtil.close(inputStream);
            IoUtil.close(outputStream);
        }
    }
}
