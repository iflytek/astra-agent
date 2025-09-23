package com.iflytek.astron.console.hub.controller.space;


import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.iflytek.astron.console.commons.response.ApiResult;
import com.iflytek.astron.console.commons.annotation.RateLimit;
import com.iflytek.astron.console.commons.annotation.space.SpacePreAuth;
import com.iflytek.astron.console.commons.dto.space.SpaceUserParam;
import com.iflytek.astron.console.commons.entity.space.SpaceUser;
import com.iflytek.astron.console.commons.dto.space.SpaceUserVO;
import com.iflytek.astron.console.commons.dto.space.UserLimitVO;
import com.iflytek.astron.console.commons.service.space.SpaceUserService;
import com.iflytek.astron.console.hub.service.space.SpaceUserBizService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import jakarta.annotation.Resource;
import java.util.List;

/**
 * Space User
 */
@Slf4j
@RestController
@RequestMapping("/space-user")
@Tag(name = "Space User")
public class SpaceUserController {
    @Resource
    private SpaceUserService spaceUserService;
    @Resource
    private SpaceUserBizService spaceUserBizService;

    @PostMapping("/enterprise-add")
    @SpacePreAuth(module = "Space User Management", description = "Enterprise space add user", requireSpaceId = true, key = "SpaceUserController_enterpriseAdd_POST")
    @Operation(summary = "Enterprise Space Add User")
    @RateLimit(dimension = "USER", window = 1, limit = 1)
    public ApiResult enterpriseAdd(@RequestParam("uid") String uid, @RequestParam("role") Integer role) {
        return spaceUserBizService.enterpriseAdd(uid, role);
    }

    @DeleteMapping("/remove")
    @SpacePreAuth(module = "Space User Management", description = "Remove user", requireSpaceId = true, key = "SpaceUserController_remove_DELETE")
    @Operation(summary = "Remove User")
    @RateLimit(dimension = "USER", window = 1, limit = 1)
    public ApiResult remove(@RequestParam("uid") String uid) {
        return spaceUserBizService.remove(uid);
    }

    @PostMapping("/update-role")
    @SpacePreAuth(module = "Space User Management", description = "Update user role", requireSpaceId = true, key = "SpaceUserController_updateRole_POST")
    @Operation(summary = "Update User Role")
    @RateLimit(dimension = "USER", window = 1, limit = 1)
    public ApiResult updateRole(@RequestParam("uid") String uid, @RequestParam("role") Integer role) {
        return spaceUserBizService.updateRole(uid, role);
    }

    @PostMapping("/page")
    @SpacePreAuth(module = "Space User Management", description = "Space user list", requireSpaceId = true, key = "SpaceUserController_page_POST")
    @Operation(summary = "Space User List")
    public ApiResult<Page<SpaceUserVO>> page(@RequestBody SpaceUserParam param) {
        return ApiResult.success(spaceUserService.page(param));
    }

    @PostMapping("/quit-space")
    @SpacePreAuth(module = "Space User Management", description = "Leave space", requireSpaceId = true, key = "SpaceUserController_quitSpace_POST")
    @Operation(summary = "Leave Space")
    @RateLimit(dimension = "USER", window = 1, limit = 1)
    public ApiResult quitSpace() {
        return spaceUserBizService.quitSpace();
    }

    @GetMapping("/list-space-member")
    @SpacePreAuth(module = "Space User Management", description = "Query all space users (excluding owner)", requireSpaceId = true, key = "SpaceUserController_listSpaceMember_GET")
    @Operation(summary = "Query All Space Users (Excluding Owner)")
    public ApiResult<List<SpaceUser>> listSpaceMember() {
        return ApiResult.success(spaceUserService.listSpaceMember());
    }

    @PostMapping("/transfer-space")
    @SpacePreAuth(module = "Space User Management", description = "Transfer space", requireSpaceId = true, key = "SpaceUserController_transferSpace_POST")
    @Operation(summary = "Transfer Space")
    @RateLimit(dimension = "USER", window = 1, limit = 1)
    public ApiResult transferSpace(@RequestParam("uid") String uid) {
        return spaceUserBizService.transferSpace(uid);
    }

    @GetMapping("/get-user-limit")
    @SpacePreAuth(module = "Space User Management", description = "Get user limit", requireSpaceId = true, key = "SpaceUserController_getUserLimit_GET")
    @Operation(summary = "Get User Limit")
    public ApiResult<UserLimitVO> getUserLimit() {
        return ApiResult.success(spaceUserBizService.getUserLimit());
    }
}
