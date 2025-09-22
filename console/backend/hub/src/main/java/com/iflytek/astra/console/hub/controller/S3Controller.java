package com.iflytek.astra.console.hub.controller;

import com.iflytek.astra.console.commons.response.ApiResult;
import com.iflytek.astra.console.commons.util.S3ClientUtil;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/s3")
@RequiredArgsConstructor
@Validated
public class S3Controller {

    private final S3ClientUtil s3ClientUtil;

    @GetMapping("/presign")
    public ApiResult<PresignResp> presignPut(@RequestParam("objectKey") String objectKey, @RequestParam(value = "contentType", required = false) String contentType) {
        // contentType is only used by frontend to set request headers, not involved in signature
        String bucket = s3ClientUtil.getDefaultBucket();
        int expiry = s3ClientUtil.getPresignExpirySeconds();
        String url = s3ClientUtil.generatePresignedPutUrl(bucket, objectKey, expiry);
        return ApiResult.success(new PresignResp(url, bucket, objectKey));
    }

    @Data
    @AllArgsConstructor
    public static class PresignResp {
        private String url;
        private String bucket;
        private String objectKey;
    }
}
