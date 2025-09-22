package com.iflytek.astra.console.hub.controller.space;

import com.iflytek.astra.console.commons.annotation.RateLimit;
import com.iflytek.astra.console.commons.response.ApiResult;
import com.iflytek.astra.console.commons.annotation.space.EnterprisePreAuth;
import com.iflytek.astra.console.commons.dto.space.EnterpriseAddDTO;
import com.iflytek.astra.console.commons.dto.space.EnterpriseVO;
import com.iflytek.astra.console.commons.service.space.EnterpriseService;
import com.iflytek.astra.console.hub.service.space.EnterpriseBizService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.extern.slf4j.Slf4j;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import java.util.List;

/**
 * Enterprise Team
 */
@Slf4j
@RestController
@RequestMapping("/enterprise")
@Tag(name = "Enterprise Team")
@Validated
public class EnterpriseController {
    @Resource
    private EnterpriseService enterpriseService;
    @Resource
    private EnterpriseBizService enterpriseBizService;

    @GetMapping("/visit-enterprise")
    @Operation(summary = "Visit enterprise team")
    public ApiResult<Boolean> visitEnterprise(@RequestParam(value = "enterpriseId", required = false) Long enterpriseId) {
        return enterpriseBizService.visitEnterprise(enterpriseId);
    }

    @GetMapping("/check-need-create-team")
    @Operation(summary = "Check if team creation is needed", description = "Returns 0: No need to create team, Returns 1: Need to create team, Returns 2: Need to create enterprise team")
    public ApiResult<Integer> checkNeedCreateTeam() {
        return ApiResult.success(enterpriseService.checkNeedCreateTeam());
    }

    @GetMapping("/check-certification")
    @Operation(summary = "Check enterprise certification")
    public ApiResult<Boolean> checkCertification() {
        return ApiResult.success(enterpriseService.checkCertification());
    }

    @PostMapping("/create")
    @Operation(summary = "Create team")
    @RateLimit(dimension = "USER", window = 1, limit = 1)
    public ApiResult<Long> create(@RequestBody @Valid EnterpriseAddDTO enterpriseAddDTO) {
        return enterpriseBizService.create(enterpriseAddDTO);
    }

    @GetMapping("/check-name")
    @Operation(summary = "Check if name exists")
    public ApiResult<Boolean> checkName(@RequestParam(value = "name") String name, @RequestParam(value = "id", required = false) Long id) {
        return ApiResult.success(enterpriseService.checkExistByName(name, id));
    }

    @PostMapping("/update-name")
    @Operation(summary = "Update enterprise team name")
    @EnterprisePreAuth(key = "EnterpriseController_updateName_POST", module = "Team/Enterprise Information Settings (Team Management)", description = "Set team/enterprise name")
    @RateLimit(dimension = "USER", window = 1, limit = 1)
    public ApiResult<String> updateName(@RequestParam(value = "name") String name) {
        return enterpriseBizService.updateName(name);
    }

    @PostMapping("/update-logo")
    @Operation(summary = "Set team/enterprise LOGO")
    @EnterprisePreAuth(key = "EnterpriseController_updateLogo_POST", module = "Team/Enterprise Information Settings (Team Management)", description = "Set team/enterprise LOGO")
    @RateLimit(dimension = "USER", window = 1, limit = 1)
    public ApiResult<String> updateLogo(@RequestParam(value = "logoUrl") String logoUrl) {
        return enterpriseBizService.updateLogo(logoUrl);
    }

    @PostMapping("/update-avatar")
    @Operation(summary = "Set team/enterprise avatar")
    @EnterprisePreAuth(key = "EnterpriseController_updateAvatar_POST", module = "Team/Enterprise Information Settings (Team Management)", description = "Set team/enterprise avatar")
    @RateLimit(dimension = "USER", window = 1, limit = 1)
    public ApiResult<String> updateAvatar(@RequestParam(value = "avatarUrl") String avatarUrl) {
        return enterpriseBizService.updateAvatar(avatarUrl);
    }

    @GetMapping("/detail")
    @Operation(summary = "Team details")
    @EnterprisePreAuth(key = "EnterpriseController_detail_GET", module = "Team/Enterprise Information View", description = "View team/enterprise details")
    public ApiResult<EnterpriseVO> detail() {
        return ApiResult.success(enterpriseService.detail());
    }

    @GetMapping("/join-list")
    @Operation(summary = "All teams")
    public ApiResult<List<EnterpriseVO>> joinList() {
        return ApiResult.success(enterpriseService.joinList());
    }


}
