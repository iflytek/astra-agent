package com.iflytek.astra.console.toolkit.tool;

import com.alibaba.fastjson2.JSONObject;
import com.iflytek.astra.console.toolkit.common.constant.ProjectContent;
import com.iflytek.astra.console.toolkit.util.S3Util;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import jakarta.annotation.Resource;

/**
 * File upload utility tool for handling file uploads to S3 storage
 */
@Component
public class FileUploadTool {

    /**
     * S3 utility client for file operations
     */
    @Resource
    S3Util s3UtilClient;

    /**
     * Upload file to S3 storage with tag-based naming
     *
     * @param file The multipart file to upload
     * @param tag The tag to determine file naming strategy
     * @return JSONObject containing s3Key and downloadLink
     */
    public JSONObject uploadFile(MultipartFile file, String tag) {
        JSONObject res = new JSONObject();
        String originalFilename = file.getOriginalFilename();
        if (originalFilename == null) {
            return res;
        }

        String fileName = "sparkBot_" + System.currentTimeMillis() + "_" + originalFilename;
        if (ProjectContent.isCbgRagCompatible(tag)) {
            String fileSuffix = originalFilename.substring(originalFilename.lastIndexOf("."));
            if (originalFilename.length() - fileSuffix.length() > 20) {
                // Truncate to first 20 characters and append suffix
                fileName = "sparkBot_" + System.currentTimeMillis() + "_" + originalFilename.substring(0, 20) + fileSuffix;
            }
        }

        // Set file path and name in S3 bucket
        String s3Key = "sparkBot/" + fileName;
        try {
            long size = file.getSize();
            String contentType = file.getContentType(); // May be null, can fallback as needed
            s3UtilClient.putObject(s3Key, file.getInputStream(), size, contentType);
        } catch (Exception e) {
            throw new RuntimeException("File upload failed! e: " + e);
        }
        res.put("s3Key", s3Key);
        res.put("downloadLink", s3UtilClient.getS3Url(s3Key));
        return res;
    }
}
