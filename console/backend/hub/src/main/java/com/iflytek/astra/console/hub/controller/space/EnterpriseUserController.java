package com.iflytek.astra.console.hub.controller.space;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.iflytek.astra.console.commons.response.ApiResult;
import com.iflytek.astra.console.commons.annotation.RateLimit;
import com.iflytek.astra.console.commons.annotation.space.EnterprisePreAuth;
import com.iflytek.astra.console.commons.dto.space.EnterpriseUserParam;
import com.iflytek.astra.console.commons.util.space.EnterpriseInfoUtil;
import com.iflytek.astra.console.commons.dto.space.EnterpriseUserVO;
import com.iflytek.astra.console.commons.dto.space.UserLimitVO;
import com.iflytek.astra.console.commons.service.space.EnterpriseUserService;
import com.iflytek.astra.console.hub.service.space.EnterpriseUserBizService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import jakarta.annotation.Resource;
import jakarta.validation.Valid;

/**
 * Enterprise Team User
 */
@Slf4j
@RestController
@RequestMapping("/enterprise-user")
@Tag(name = "Enterprise Team User")
@Validated
public class EnterpriseUserController {
    @Resource
    private EnterpriseUserService enterpriseUserService;
    @Autowired
    private EnterpriseUserBizService enterpriseUserBizService;

    @DeleteMapping("/remove")
    @EnterprisePreAuth(module = "Enterprise Team User Management", description = "Remove user", key = "EnterpriseUserController_remove_DELETE")
    @Operation(summary = "Remove User")
    @RateLimit(dimension = "USER", window = 1, limit = 1)
    public ApiResult<String> remove(@RequestParam("uid") String uid) {
        return enterpriseUserBizService.remove(uid);
    }

    @PostMapping("/update-role")
    @EnterprisePreAuth(module = "Enterprise Team User Management", description = "Update user role", key = "EnterpriseUserController_updateRole_POST")
    @Operation(summary = "Update User Role")
    @RateLimit(dimension = "USER", window = 1, limit = 1)
    public ApiResult<String> updateRole(@RequestParam("uid") String uid, @RequestParam("role") Integer role) {
        return enterpriseUserBizService.updateRole(uid, role);
    }

    @PostMapping("/page")
    @EnterprisePreAuth(module = "Enterprise Team User Management", description = "Team user list", key = "EnterpriseUserController_page_POST")
    @Operation(summary = "Team User List")
    public ApiResult<Page<EnterpriseUserVO>> page(@RequestBody @Valid EnterpriseUserParam param) {
        return ApiResult.success(enterpriseUserService.page(param));
    }

    @PostMapping("/quit-enterprise")
    @EnterprisePreAuth(module = "Enterprise Team User Management", description = "Quit enterprise team", key = "EnterpriseUserController_quitEnterprise_POST")
    @Operation(summary = "Quit Enterprise Team")
    @RateLimit(dimension = "USER", window = 1, limit = 1)
    public ApiResult<String> quitEnterprise() {
        return enterpriseUserBizService.quitEnterprise();
    }


    @GetMapping("/get-user-limit")
    @EnterprisePreAuth(module = "Enterprise Team User Management", description = "Get user limit", key = "EnterpriseUserController_getUserLimit_GET")
    @Operation(summary = "Get User Limit")
    public ApiResult<UserLimitVO> getUserLimit() {
        return ApiResult.success(enterpriseUserBizService.getUserLimit(EnterpriseInfoUtil.getEnterpriseId()));
    }
}
