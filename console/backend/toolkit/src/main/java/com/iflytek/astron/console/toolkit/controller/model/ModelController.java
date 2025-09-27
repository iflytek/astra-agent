package com.iflytek.astron.console.toolkit.controller.model;

import com.iflytek.astron.console.commons.annotation.space.SpacePreAuth;
import com.iflytek.astron.console.commons.constant.ResponseEnum;
import com.iflytek.astron.console.commons.exception.BusinessException;
import com.iflytek.astron.console.commons.response.ApiResult;
import com.iflytek.astron.console.commons.util.space.SpaceInfoUtil;
import com.iflytek.astron.console.toolkit.common.anno.ResponseResultBody;
import com.iflytek.astron.console.toolkit.entity.biz.modelconfig.*;
import com.iflytek.astron.console.toolkit.entity.vo.CategoryTreeVO;
import com.iflytek.astron.console.toolkit.handler.UserInfoManagerHandler;
import com.iflytek.astron.console.toolkit.service.model.ModelService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/model")
@Tag(name = "Model management interface")
@ResponseResultBody
@Slf4j
public class ModelController {
    @Autowired
    private ModelService modelService;

    /**
     * Add or update model
     *
     * @param request
     * @param httpServletRequest
     * @return
     */
    @PostMapping
    @SpacePreAuth(key = "ModelController_create_POST", module = "Model Management", point = "Add/Edit Model", description = "Add/Edit Model")
    public ApiResult validateModel(@RequestBody @Validated ModelValidationRequest request, HttpServletRequest httpServletRequest) {
        String userId = UserInfoManagerHandler.getUserId();
        request.setUid(userId);
        return ApiResult.success(modelService.validateModel(request));
    }

    @GetMapping("/delete")
    @SpacePreAuth(key = "ModelController_delete_GET", module = "Model Management", point = "Delete Model", description = "Delete Model")
    public ApiResult validateModel(@RequestParam(name = "modelId") Long modelId, HttpServletRequest request) {
        return modelService.checkAndDelete(modelId, request);
    }

    @PostMapping("/list")
    @SpacePreAuth(key = "ModelController_list_POST", module = "Model Management", point = "Model List", description = "Model List")
    public ApiResult list(@RequestBody ModelDto dto, HttpServletRequest request) {
        String uid = UserInfoManagerHandler.getUserId();
        dto.setUid(uid);
        dto.setSpaceId(SpaceInfoUtil.getSpaceId());
        return modelService.getList(dto, request);
    }

    @GetMapping("/detail")
    public ApiResult detail(@RequestParam(name = "llmSource") Integer llmSource, @RequestParam(name = "modelId") Long modelId, HttpServletRequest request) {
        return modelService.getDetail(llmSource, modelId, request);
    }

    @GetMapping("/rsa/public-key")
    public ApiResult getRsaPublicKey() {
        try {
            String publicKey = modelService.getPublicKey();
            return ApiResult.success(publicKey);
        } catch (Exception e) {
            log.error("Failed to get RSA public key", e);
            throw new BusinessException(ResponseEnum.RESPONSE_FAILED, "Failed to get RSA public key: " + e.getMessage());
        }
    }

    /**
     * Check model ownership
     *
     * @param llmId
     * @param serviceId
     * @param url
     * @return
     */
    @GetMapping("/check-model-base")
    public ApiResult checkModelBase(@RequestParam(name = "llmId") Long llmId,
            @RequestParam(name = "uid") String uid,
            @RequestParam(name = "spaceId", required = false) Long spaceId,
            @RequestParam(name = "serviceId") String serviceId,
            @RequestParam(name = "url") String url) {
        return ApiResult.success(modelService.checkModelBase(llmId, serviceId, url, uid, spaceId));
    }

    /**
     * For creating models dropdown: Full official category tree
     */
    @GetMapping("/category-tree")
    public ApiResult<List<CategoryTreeVO>> getAllCategoryTree() {
        return ApiResult.success(modelService.getAllCategoryTree());
    }

    /**
     * Enable or disable model
     *
     * @param option
     * @param modelId
     * @param request
     * @return
     */
    @GetMapping("/{option}")
    @SpacePreAuth(key = "ModelController_switchModel_GET", module = "Model Management", point = "Enable/Disable Model", description = "Enable/Disable Model")
    public ApiResult switchModel(@PathVariable String option,
            @RequestParam(name = "llmSource") Integer llmSource,
            @RequestParam(name = "modelId") Long modelId,
            HttpServletRequest request) {
        return modelService.switchModel(modelId, llmSource, option, request);
    }


    /**
     * Take model offline
     *
     * @param llmId
     * @param flowId
     * @return
     */
    @GetMapping("/off-model")
    public ApiResult checkModelBase(@RequestParam(name = "llmId") Long llmId,
            @RequestParam(name = "serviceId") String serviceId,
            @RequestParam(name = "flowId", required = false) String flowId) {
        return ApiResult.success(modelService.offShelfModel(llmId, flowId, serviceId));
    }

    /**
     * Add/Edit local model
     *
     * @param dto
     * @return
     */
    @PostMapping("/local-model")
    @SpacePreAuth(key = "ModelController_localModel_POST", module = "Model Management", point = "Add/Edit Local Model", description = "Add/Edit Local Model")
    public ApiResult localModel(@RequestBody @Validated LocalModelDto dto) {
        String userId = UserInfoManagerHandler.getUserId();
        dto.setUid(userId);
        return ApiResult.success(modelService.localModel(dto));
    }

    /**
     * Get model file directory list
     *
     * @return
     */
    @GetMapping("/local-model/list")
    @SpacePreAuth(key = "ModelController_localModelList_POST", module = "Model Management", point = "Get model file directory list", description = "Get model file directory list")
    public ApiResult localModelList() {
        return ApiResult.success(modelService.localModelList());
    }
}
