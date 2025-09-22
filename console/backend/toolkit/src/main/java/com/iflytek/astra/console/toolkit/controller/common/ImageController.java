package com.iflytek.astra.console.toolkit.controller.common;


import com.alibaba.fastjson2.JSONObject;
import com.iflytek.astra.console.toolkit.common.Result;
import com.iflytek.astra.console.toolkit.common.anno.ResponseResultBody;
import com.iflytek.astra.console.toolkit.service.common.ImageService;
import com.iflytek.astra.console.toolkit.util.S3Util;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.Arrays;
import java.util.List;

@RestController
@RequestMapping("/image")
@Slf4j
@ResponseResultBody
public class ImageController {
    @Resource
    private ImageService imageService;
    @Resource
    private S3Util s3UtilClient;

    @PostMapping("/upload")
    public Result<JSONObject> upload(@RequestParam("file") MultipartFile file) {
        //文件名称后缀校验
        List<String> allowedSuffixes = Arrays.asList("png", "jpg", "jpeg");
        String fileName = file.getOriginalFilename();

        if (fileName == null || !fileName.contains(".")) {
            return Result.failure("文件格式不正确，请上传png、jpg格式的图片");
        }

        String suffix = fileName.substring(fileName.lastIndexOf('.') + 1).toLowerCase();
        if (!allowedSuffixes.contains(suffix)) {
            return Result.failure("文件格式不正确，请上传png、jpg格式的图片");
        }
        String s3Key = imageService.upload(file);
        JSONObject res = new JSONObject();
        // 生成唯一的文件名
        res.put("s3Key",s3Key);
        res.put("downloadLink", s3UtilClient.getS3Url(s3Key));
        return Result.success(res);
    }

}
