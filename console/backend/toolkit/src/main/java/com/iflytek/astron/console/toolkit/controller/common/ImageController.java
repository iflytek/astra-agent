package com.iflytek.astron.console.toolkit.controller.common;

import com.alibaba.fastjson2.JSONObject;
import com.iflytek.astron.console.commons.constant.ResponseEnum;
import com.iflytek.astron.console.commons.exception.BusinessException;
import com.iflytek.astron.console.commons.response.ApiResult;
import com.iflytek.astron.console.toolkit.common.anno.ResponseResultBody;
import com.iflytek.astron.console.toolkit.service.common.ImageService;
import com.iflytek.astron.console.toolkit.util.S3Util;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.Arrays;
import java.util.List;

/**
 * REST controller for image management.
 * <p>
 * Provides APIs to upload images to S3 storage and return the object key and download link.
 * </p>
 */
@RestController
@RequestMapping("/image")
@Slf4j
@ResponseResultBody
public class ImageController {

    @Resource
    private ImageService imageService;

    @Resource
    private S3Util s3UtilClient;

    /**
     * Upload an image file to S3.
     * <p>
     * Validates the file suffix (only supports png, jpg, jpeg), uploads to S3, and returns the object
     * key and download link.
     * </p>
     *
     * @param file multipart file to upload; must not be {@code null}
     * @return {@link ApiResult} wrapping a JSON object containing:
     *         <ul>
     *         <li>{@code s3Key} - object key in S3</li>
     *         <li>{@code downloadLink} - accessible download URL</li>
     *         </ul>
     * @throws BusinessException if the file name is invalid, file suffix is unsupported, or upload
     *         fails
     */
    @PostMapping("/upload")
    public ApiResult<JSONObject> upload(@RequestParam("file") MultipartFile file) {
        // File suffix validation
        List<String> allowedSuffixes = Arrays.asList("png", "jpg", "jpeg");
        String fileName = file.getOriginalFilename();

        if (fileName == null || !fileName.contains(".")) {
            throw new BusinessException(ResponseEnum.RESPONSE_FAILED, "Invalid file format, please upload a png or jpg image");
        }

        String suffix = fileName.substring(fileName.lastIndexOf('.') + 1).toLowerCase();
        if (!allowedSuffixes.contains(suffix)) {
            throw new BusinessException(ResponseEnum.RESPONSE_FAILED, "Invalid file format, please upload a png or jpg image");
        }

        String s3Key = imageService.upload(file);
        JSONObject res = new JSONObject();
        // Generate unique file name
        res.put("s3Key", s3Key);
        res.put("downloadLink", s3UtilClient.getS3Url(s3Key));
        return ApiResult.success(res);
    }
}
