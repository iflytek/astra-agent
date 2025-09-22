package com.iflytek.astra.console.hub.controller.space;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.iflytek.astra.console.commons.annotation.RateLimit;
import com.iflytek.astra.console.commons.response.ApiResult;
import com.iflytek.astra.console.commons.annotation.space.EnterprisePreAuth;
import com.iflytek.astra.console.commons.annotation.space.SpacePreAuth;
import com.iflytek.astra.console.commons.dto.space.InviteRecordAddDTO;
import com.iflytek.astra.console.commons.dto.space.InviteRecordParam;
import com.iflytek.astra.console.commons.enums.space.InviteRecordTypeEnum;
import com.iflytek.astra.console.commons.dto.space.BatchChatUserVO;
import com.iflytek.astra.console.commons.dto.space.ChatUserVO;
import com.iflytek.astra.console.commons.dto.space.InviteRecordVO;
import com.iflytek.astra.console.commons.service.space.InviteRecordService;
import com.iflytek.astra.console.hub.service.space.InviteRecordBizService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.extern.slf4j.Slf4j;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import java.util.List;

/**
 * Invitation records
 */
@Slf4j
@RestController
@RequestMapping("/invite-record")
@Tag(name = "Invitation Records")
@Validated
public class InviteRecordController {
    @Resource
    private InviteRecordService inviteRecordService;
    @Resource
    private InviteRecordBizService inviteRecordBizService;

    @GetMapping("/get-invite-by-param")
    @Operation(summary = "Get invitation record by parameter")
    public ApiResult<InviteRecordVO> getInviteByParam(@RequestParam("param") String param) {
        try {
            return ApiResult.success(inviteRecordBizService.getRecordByParam(param));
        } catch (RuntimeException e) {
            return ApiResult.error(-1, e.getMessage());
        }
    }

    @GetMapping("/space-search-user")
    @SpacePreAuth(module = "Invitation Management", description = "Space invitation search user", requireSpaceId = true, key = "InviteRecordController_spaceSearchUser_GET")
    @Operation(summary = "Space invitation search user")
    public ApiResult<List<ChatUserVO>> spaceSearchUser(@RequestParam("mobile") String mobile) {
        return ApiResult.success(inviteRecordBizService.searchUser(mobile, InviteRecordTypeEnum.SPACE));
    }

    @GetMapping("/space-search-username")
    @SpacePreAuth(module = "Invitation Management", description = "Space invitation search username", requireSpaceId = true, key = "InviteRecordController_spaceSearchUsername_GET")
    @Operation(summary = "Space invitation search username")
    public ApiResult<List<ChatUserVO>> spaceSearchUsername(@RequestParam("username") @NotEmpty String username) {
        return ApiResult.success(inviteRecordBizService.searchUsername(username, InviteRecordTypeEnum.SPACE));
    }

    @PostMapping("/space-invite")
    @SpacePreAuth(module = "Invitation Management", description = "Invite to join space", requireSpaceId = true, key = "InviteRecordController_spaceInvite_POST")
    @Operation(summary = "Invite to join space")
    @RateLimit(dimension = "USER", window = 1, limit = 1)
    public ApiResult<String> spaceInvite(@RequestBody @Valid @NotEmpty List<InviteRecordAddDTO> dtos) {
        return inviteRecordBizService.spaceInvite(dtos);
    }

    @PostMapping("/space-invite-list")
    @SpacePreAuth(module = "Invitation Management", description = "Space invitation list", requireSpaceId = true, key = "InviteRecordController_spaceInviteList_POST")
    @Operation(summary = "Space invitation list")
    public ApiResult<Page<InviteRecordVO>> spaceInviteList(@RequestBody @Valid InviteRecordParam param) {
        return ApiResult.success(inviteRecordService.inviteList(param, InviteRecordTypeEnum.SPACE));
    }

    @GetMapping("/enterprise-search-user")
    @EnterprisePreAuth(module = "Invitation Management", description = "Enterprise invitation search user", key = "InviteRecordController_enterpriseSearchUser_GET")
    @Operation(summary = "Enterprise invitation search user")
    public ApiResult<List<ChatUserVO>> enterpriseSearchUser(@RequestParam("mobile") String mobile) {
        return ApiResult.success(inviteRecordBizService.searchUser(mobile, InviteRecordTypeEnum.ENTERPRISE));
    }

    @PostMapping("/enterprise-batch-search-user")
    @EnterprisePreAuth(module = "Invitation Management", description = "Enterprise invitation batch search user", key = "InviteRecordController_enterpriseBatchSearchUser_POST")
    @Operation(summary = "Enterprise invitation batch search user")
    public ApiResult<BatchChatUserVO> enterpriseBatchSearchUser(@RequestParam MultipartFile file) {
        return inviteRecordBizService.searchUserBatch(file);
    }

    @PostMapping("/enterprise-invite")
    @EnterprisePreAuth(module = "Invitation Management", description = "Invite to join enterprise team", key = "InviteRecordController_enterpriseInvite_POST")
    @Operation(summary = "Invite to join enterprise team")
    @RateLimit(dimension = "USER", window = 1, limit = 1)
    public ApiResult<String> enterpriseInvite(@RequestBody @Valid @NotEmpty List<InviteRecordAddDTO> dtos) {
        return inviteRecordBizService.enterpriseInvite(dtos);
    }

    @PostMapping("/enterprise-invite-list")
    @EnterprisePreAuth(module = "Invitation Management", description = "Enterprise team invitation list", key = "InviteRecordController_enterpriseInviteList_POST")
    @Operation(summary = "Enterprise team invitation list")
    public ApiResult<Page<InviteRecordVO>> enterpriseInviteList(@RequestBody @Valid InviteRecordParam param) {
        return ApiResult.success(inviteRecordService.inviteList(param, InviteRecordTypeEnum.ENTERPRISE));
    }

    @PostMapping("/accept-invite")
    @Operation(summary = "Accept invitation")
    @RateLimit(dimension = "USER", window = 1, limit = 1)
    public ApiResult<String> acceptInvite(@RequestParam("inviteId") Long inviteId) {
        return inviteRecordBizService.acceptInvite(inviteId);
    }

    @PostMapping("/refuse-invite")
    @Operation(summary = "Reject invitation")
    @RateLimit(dimension = "USER", window = 1, limit = 1)
    public ApiResult<String> refuseInvite(@RequestParam("inviteId") Long inviteId) {
        return inviteRecordBizService.refuseInvite(inviteId);
    }

    @PostMapping("/revoke-enterprise-invite")
    @EnterprisePreAuth(module = "Invitation Management", description = "Revoke enterprise invitation", key = "InviteRecordController_revokeEnterpriseInvite_POST")
    @Operation(summary = "Revoke enterprise invitation")
    @RateLimit(dimension = "USER", window = 1, limit = 1)
    public ApiResult<String> revokeEnterpriseInvite(@RequestParam("inviteId") Long inviteId) {
        return inviteRecordBizService.revokeEnterpriseInvite(inviteId);
    }

    @PostMapping("/revoke-space-invite")
    @SpacePreAuth(module = "Invitation Management", description = "Revoke space invitation", requireSpaceId = true, key = "InviteRecordController_revokeSpaceInvite_POST")
    @Operation(summary = "Revoke space invitation")
    @RateLimit(dimension = "USER", window = 1, limit = 1)
    public ApiResult<String> revokeSpaceInvite(@RequestParam("inviteId") Long inviteId) {
        return inviteRecordBizService.revokeSpaceInvite(inviteId);
    }

}
