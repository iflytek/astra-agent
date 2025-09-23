package com.iflytek.astron.console.toolkit.controller.common;


import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.iflytek.astron.console.commons.response.ApiResult;
import com.iflytek.astron.console.toolkit.entity.table.ConfigInfo;
import com.iflytek.astron.console.toolkit.service.common.ConfigInfoService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import org.springframework.web.bind.annotation.*;

import java.util.Arrays;
import java.util.List;

/**
 * <p>
 * 配置表 前端控制器
 * </p>
 *
 * @author xywang73
 * @since 2022-05-05
 */
@RestController
@RequestMapping("/config-info")
@Tag(name = "Config management interface")
public class ConfigInfoController {
    @Resource
    private ConfigInfoService configInfoService;

    @GetMapping("/get-list-by-category")
    public ApiResult<List<ConfigInfo>> getListByCategory(@RequestParam("category") String category) {
        // 专业版目前只使用CBG，分片策略使用CBG
        /*
         * if (category.equals("DEFAULT_SLICE_RULES") || category.equals("CUSTOM_SLICE_RULES")) { category =
         * category + "_CBG"; }
         */
        return ApiResult.success(configInfoService.list(Wrappers.lambdaQuery(ConfigInfo.class).eq(ConfigInfo::getCategory, category).eq(ConfigInfo::getIsValid, 1)));
    }

    @GetMapping("/get-by-category-and-code")
    public ApiResult<ConfigInfo> getByCategoryAndCode(@RequestParam("category") String category, @RequestParam("code") String code) {
        return ApiResult.success(configInfoService.getBaseMapper().selectOne(Wrappers.lambdaQuery(ConfigInfo.class).eq(ConfigInfo::getCategory, category).eq(ConfigInfo::getCode, code).eq(ConfigInfo::getIsValid, 1).last("limit 1")));
    }

    @GetMapping("/list-by-category-and-code")
    public ApiResult<List<ConfigInfo>> listByCategoryAndCode(@RequestParam("category") String category, @RequestParam("code") String code) {
        return ApiResult.success(configInfoService.list(Wrappers.lambdaQuery(ConfigInfo.class).eq(ConfigInfo::getCategory, category).eq(ConfigInfo::getCode, code).eq(ConfigInfo::getIsValid, 1)));
    }

    @GetMapping("/tags")
    public ApiResult<List<ConfigInfo>> getTags(@RequestParam(value = "flag") String flag) {
        return ApiResult.success(configInfoService.getTags(flag));
    }

    @GetMapping("/workflow/categories")
    public ApiResult<List<String>> getTags() {
        ConfigInfo config = configInfoService.getOne(new LambdaQueryWrapper<ConfigInfo>()
                .eq(ConfigInfo::getCategory, "WORKFLOW_CATEGORY")
                .eq(ConfigInfo::getIsValid, 1));
        return ApiResult.success(Arrays.asList(config.getValue().split(",")));
    }

}
