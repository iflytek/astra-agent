package com.iflytek.astron.console.hub.service.space.impl;

import cn.hutool.core.collection.CollectionUtil;
import com.iflytek.astron.console.commons.constant.ResponseEnum;
import com.iflytek.astron.console.commons.response.ApiResult;
import com.iflytek.astron.console.commons.service.space.*;
import com.iflytek.astron.console.commons.util.RequestContextUtil;
import com.iflytek.astron.console.commons.entity.space.Enterprise;
import com.iflytek.astron.console.commons.entity.space.EnterpriseUser;
import com.iflytek.astron.console.commons.enums.space.EnterpriseRoleEnum;
import com.iflytek.astron.console.commons.enums.space.EnterpriseServiceTypeEnum;
import com.iflytek.astron.console.commons.enums.space.SpaceRoleEnum;
import com.iflytek.astron.console.hub.properties.SpaceLimitProperties;
import com.iflytek.astron.console.hub.service.space.EnterpriseUserBizService;
import com.iflytek.astron.console.commons.util.space.EnterpriseInfoUtil;
import com.iflytek.astron.console.commons.dto.space.SpaceVO;
import com.iflytek.astron.console.commons.dto.space.UserLimitVO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

@Service
@Slf4j
public class EnterpriseUserBizServiceImpl implements EnterpriseUserBizService {
    @Autowired
    private SpaceUserService spaceUserService;
    @Autowired
    private SpaceService spaceService;
    @Autowired
    private EnterpriseService enterpriseService;
    @Autowired
    private SpaceLimitProperties spaceLimitProperties;
    @Autowired
    private InviteRecordService inviteRecordService;
    @Autowired
    private EnterpriseSpaceService enterpriseSpaceService;
    @Autowired
    private EnterpriseUserService enterpriseUserService;

    /**
     * Remove user
     *
     * @param uid
     * @return
     */
    @Override
    @Transactional
    public ApiResult<String> remove(String uid) {
        Long enterpriseId = EnterpriseInfoUtil.getEnterpriseId();
        EnterpriseUser enterpriseUser = enterpriseUserService.getEnterpriseUserByUid(enterpriseId, uid);
        if (enterpriseUser == null) {
            return ApiResult.error(ResponseEnum.ENTERPRISE_TEAM_USER_NOT_IN_TEAM);
        }
        if (Objects.equals(enterpriseUser.getRole(), EnterpriseRoleEnum.OFFICER.getCode())) {
            return ApiResult.error(ResponseEnum.ENTERPRISE_TEAM_SUPER_ADMIN_CANNOT_BE_REMOVED);
        }
        // Remove user unified operation
        if (!removeEnterpriseUser(enterpriseUser)) {
            return ApiResult.error(ResponseEnum.ENTERPRISE_TEAM_REMOVE_USER_FAILED);
        }
        enterpriseSpaceService.clearEnterpriseUserCache(enterpriseId, uid);
        return ApiResult.of(ResponseEnum.ENTERPRISE_TEAM_REMOVE_USER_SUCCESS, null);
    }

    /**
     * Modify user role
     *
     * @param uid
     * @param role
     * @return
     */
    @Override
    @Transactional
    public ApiResult<String> updateRole(String uid, Integer role) {
        Long enterpriseId = EnterpriseInfoUtil.getEnterpriseId();
        if (enterpriseId == null) {
            return ApiResult.error(ResponseEnum.SPACE_APPLICATION_PLEASE_JOIN_ENTERPRISE_FIRST);
        }
        EnterpriseUser enterpriseUser = enterpriseUserService.getEnterpriseUserByUid(enterpriseId, uid);
        if (enterpriseUser == null) {
            return ApiResult.error(ResponseEnum.ENTERPRISE_TEAM_USER_NOT_IN_TEAM);
        }
        EnterpriseRoleEnum roleEnum = EnterpriseRoleEnum.getByCode(role);
        if (roleEnum == null) {
            return ApiResult.error(ResponseEnum.ENTERPRISE_TEAM_ROLE_TYPE_INCORRECT);
        }
        enterpriseUser.setRole(role);
        if (!enterpriseUserService.updateById(enterpriseUser)) {
            // Clear cache
            enterpriseSpaceService.clearEnterpriseUserCache(enterpriseId, uid);
            return ApiResult.error(ResponseEnum.ENTERPRISE_TEAM_UPDATE_ROLE_FAILED);
        }
        return ApiResult.of(ResponseEnum.ENTERPRISE_TEAM_UPDATE_ROLE_SUCCESS, null);
    }

    /**
     * Leave team
     *
     * @return
     */
    @Override
    @Transactional
    public ApiResult<String> quitEnterprise() {
        Long enterpriseId = EnterpriseInfoUtil.getEnterpriseId();
        String uid = RequestContextUtil.getUID();
        EnterpriseUser enterpriseUser = enterpriseUserService.getEnterpriseUserByUid(enterpriseId, uid);
        if (Objects.equals(enterpriseUser.getRole(), EnterpriseRoleEnum.OFFICER.getCode())) {
            return ApiResult.error(ResponseEnum.ENTERPRISE_TEAM_SUPER_ADMIN_CANNOT_LEAVE_TEAM);
        }
        // Remove user unified operation
        if (!removeEnterpriseUser(enterpriseUser)) {
            // Clear cache
            enterpriseSpaceService.clearEnterpriseUserCache(enterpriseId, uid);
            return ApiResult.error(ResponseEnum.ENTERPRISE_TEAM_LEAVE_FAILED);
        }
        return ApiResult.of(ResponseEnum.ENTERPRISE_TEAM_LEAVE_SUCCESS, null);
    }

    /**
     * Get user limits
     *
     * @param enterpriseId
     * @return
     */
    @Override
    public UserLimitVO getUserLimit(Long enterpriseId) {
        Enterprise enterprise = enterpriseService.getEnterpriseById(enterpriseId);
        // Get user limits
        Integer userCount = 0;
        if (Objects.equals(enterprise.getServiceType(), EnterpriseServiceTypeEnum.ENTERPRISE.getCode())) {
            userCount = spaceLimitProperties.getEnterprise().getUserCount();
        } else if (Objects.equals(enterprise.getServiceType(), EnterpriseServiceTypeEnum.TEAM.getCode())) {
            userCount = spaceLimitProperties.getTeam().getUserCount();
        }
        UserLimitVO vo = new UserLimitVO();
        vo.setTotal(userCount);
        // Used = team user count + inviting user count
        long used = enterpriseUserService.countByEnterpriseId(enterpriseId)
                + inviteRecordService.countJoiningByEnterpriseId(enterpriseId);
        vo.setUsed(Long.valueOf(used).intValue());
        vo.setRemain(vo.getTotal() - vo.getUsed());
        return vo;
    }

    /**
     * Remove user unified operation
     *
     * @param enterpriseUser
     * @return
     */
    private boolean removeEnterpriseUser(EnterpriseUser enterpriseUser) {
        // Get user's spaces
        List<SpaceVO> spaceVOS = spaceService.listByEnterpriseIdAndUid(enterpriseUser.getEnterpriseId(),
                enterpriseUser.getUid());

        String uid = enterpriseService.getUidByEnterpriseId(enterpriseUser.getEnterpriseId());
        if (CollectionUtil.isNotEmpty(spaceVOS)) {
            // If user is space owner, set super admin as space owner
            for (SpaceVO spaceVO : spaceVOS) {
                if (Objects.equals(spaceVO.getUserRole(), SpaceRoleEnum.OWNER.getCode())) {
                    spaceUserService.addSpaceUser(spaceVO.getId(), uid, SpaceRoleEnum.OWNER);
                }
            }
            // Remove all space users
            spaceUserService.removeByUid(spaceVOS.stream()
                    .map(SpaceVO::getId)
                    .collect(Collectors.toSet()), enterpriseUser.getUid());
        }
        // Delete team user
        return enterpriseUserService.removeById(enterpriseUser);
    }
}
