package com.iflytek.astra.console.hub.service.space.impl;

import com.iflytek.astra.console.commons.constant.ResponseEnum;
import com.iflytek.astra.console.commons.response.ApiResult;
import com.iflytek.astra.console.commons.service.space.*;
import com.iflytek.astra.console.commons.util.RequestContextUtil;
import com.iflytek.astra.console.commons.entity.space.Enterprise;
import com.iflytek.astra.console.commons.entity.space.EnterpriseUser;
import com.iflytek.astra.console.commons.entity.space.Space;
import com.iflytek.astra.console.commons.entity.space.SpaceUser;
import com.iflytek.astra.console.commons.enums.space.EnterpriseServiceTypeEnum;
import com.iflytek.astra.console.commons.enums.space.SpaceRoleEnum;
import com.iflytek.astra.console.commons.enums.space.SpaceTypeEnum;
import com.iflytek.astra.console.hub.properties.SpaceLimitProperties;
import com.iflytek.astra.console.hub.service.space.EnterpriseUserBizService;
import com.iflytek.astra.console.hub.service.space.SpaceUserBizService;
import com.iflytek.astra.console.commons.util.space.OrderInfoUtil;
import com.iflytek.astra.console.commons.util.space.SpaceInfoUtil;
import com.iflytek.astra.console.commons.dto.space.UserLimitVO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Arrays;
import java.util.Objects;

@Service
@Slf4j
public class SpaceUserBizServiceImpl implements SpaceUserBizService {

    @Autowired
    private EnterpriseUserService enterpriseUserService;
    @Autowired
    private SpaceService spaceService;
    @Autowired
    private SpaceLimitProperties spaceLimitProperties;
    @Autowired
    private InviteRecordService inviteRecordService;
    @Autowired
    private EnterpriseSpaceService enterpriseSpaceService;
    @Autowired
    private SpaceUserService spaceUserService;
    @Autowired
    private EnterpriseUserBizService enterpriseUserBizService;
    @Autowired
    private EnterpriseService enterpriseService;

    /**
     * Enterprise add space member (user is already enterprise user) -- Not used in page
     *
     * @param uid
     * @param role
     * @return
     */
    @Override
    @Transactional
    public ApiResult enterpriseAdd(String uid, Integer role) {
        SpaceRoleEnum roleEnum = SpaceRoleEnum.getByCode(role);
        if (roleEnum == null) {
            return ApiResult.error(ResponseEnum.SPACE_USER_UNSUPPORTED_ROLE_TYPE);
        }
        if (roleEnum == SpaceRoleEnum.OWNER) {
            return ApiResult.error(ResponseEnum.SPACE_USER_UNSUPPORTED_ROLE_TYPE);
        }
        Long spaceId = SpaceInfoUtil.getSpaceId();
        Space space = spaceService.getSpaceById(spaceId);
        if (space == null) {
            return ApiResult.error(ResponseEnum.SPACE_NOT_EXISTS);
        }
        if (space.getEnterpriseId() == null) {
            return ApiResult.error(ResponseEnum.SPACE_USER_SPACE_NOT_BELONG_TO_ENTERPRISE);
        }
        EnterpriseUser enterpriseUser = enterpriseUserService.getEnterpriseUserByUid(space.getEnterpriseId(), uid);
        if (enterpriseUser == null) {
            return ApiResult.error(ResponseEnum.SPACE_USER_NOT_IN_ENTERPRISE_TEAM);
        }
        SpaceUser spaceUser = spaceUserService.getSpaceUserByUid(spaceId, uid);
        if (spaceUser != null) {
            return ApiResult.error(ResponseEnum.SPACE_USER_ALREADY_EXISTS);
        }
        spaceUser = new SpaceUser();
        spaceUser.setSpaceId(spaceId);
        spaceUser.setUid(uid);
        spaceUser.setRole(role);
        if (spaceUserService.save(spaceUser)) {
            return ApiResult.success();
        } else {
            return ApiResult.error(ResponseEnum.SPACE_USER_ADD_FAILED);
        }
    }

    /**
     * Remove space member
     *
     * @param uid
     * @return
     */
    @Override
    @Transactional
    public ApiResult remove(String uid) {
        Long spaceId = SpaceInfoUtil.getSpaceId();
        if (spaceId == null) {
            return ApiResult.error(ResponseEnum.SPACE_NOT_EXISTS);
        }
        SpaceUser spaceUser = spaceUserService.getSpaceUserByUid(spaceId, uid);
        if (spaceUser == null) {
            return ApiResult.error(ResponseEnum.SPACE_USER_NOT_EXISTS);
        }
        if (SpaceRoleEnum.getByCode(spaceUser.getRole()) == SpaceRoleEnum.OWNER) {
            return ApiResult.error(ResponseEnum.SPACE_USER_CANNOT_REMOVE_OWNER);
        }
        if (spaceUserService.removeById(spaceUser)) {
            enterpriseSpaceService.clearSpaceUserCache(spaceId, uid);
            return ApiResult.success();
        } else {
            return ApiResult.error(ResponseEnum.SPACE_USER_REMOVE_FAILED);
        }
    }

    /**
     * Update space member role
     *
     * @param uid
     * @param role
     * @return
     */
    @Override
    @Transactional
    public ApiResult updateRole(String uid, Integer role) {
        SpaceRoleEnum roleEnum = SpaceRoleEnum.getByCode(role);
        if (roleEnum == null) {
            return ApiResult.error(ResponseEnum.SPACE_USER_UNSUPPORTED_ROLE_TYPE);
        }
        if (roleEnum == SpaceRoleEnum.OWNER) {
            return ApiResult.error(ResponseEnum.SPACE_USER_UNSUPPORTED_ROLE_TYPE);
        }
        Long spaceId = SpaceInfoUtil.getSpaceId();
        if (spaceId == null) {
            return ApiResult.error(ResponseEnum.SPACE_NOT_EXISTS);
        }
        SpaceUser spaceUser = spaceUserService.getSpaceUserByUid(spaceId, uid);
        if (spaceUser == null) {
            return ApiResult.error(ResponseEnum.SPACE_USER_NOT_EXISTS);
        }
        if (SpaceRoleEnum.getByCode(spaceUser.getRole()) == SpaceRoleEnum.OWNER) {
            return ApiResult.error(ResponseEnum.SPACE_USER_OWNER_ROLE_CANNOT_CHANGE);
        }
        spaceUser.setRole(role);
        if (spaceUserService.updateById(spaceUser)) {
            enterpriseSpaceService.clearSpaceUserCache(spaceId, uid);
            return ApiResult.success();
        } else {
            return ApiResult.error(ResponseEnum.ENTERPRISE_UPDATE_FAILED);
        }
    }

    /**
     * Exit space
     *
     * @return
     */
    @Override
    @Transactional
    public ApiResult quitSpace() {
        Long spaceId = SpaceInfoUtil.getSpaceId();
        String uid = RequestContextUtil.getUID();
        SpaceUser spaceUser = spaceUserService.getSpaceUserByUid(spaceId, uid);
        if (Objects.equals(spaceUser.getRole(), SpaceRoleEnum.OWNER.getCode())) {
            return ApiResult.error(ResponseEnum.SPACE_USER_OWNER_CANNOT_LEAVE);
        }
        if (spaceUserService.removeById(spaceUser)) {
            return ApiResult.success();
        } else {
            return ApiResult.error(ResponseEnum.SPACE_USER_REMOVE_FAILED);
        }
    }

    /**
     * Transfer space -- Only available for enterprise spaces
     *
     * @param uid
     * @return
     */
    @Override
    @Transactional
    public ApiResult transferSpace(String uid) {
        Long spaceId = SpaceInfoUtil.getSpaceId();
        Space space = spaceService.getSpaceById(spaceId);
        if (space.getEnterpriseId() == null) {
            return ApiResult.error(ResponseEnum.SPACE_USER_PERSONAL_SPACE_CANNOT_TRANSFER);
        }
        String ownerUid = RequestContextUtil.getUID();
        SpaceUser spaceOwner = spaceUserService.getSpaceOwner(spaceId);
        if (!spaceOwner.getUid().equals(ownerUid)) {
            return ApiResult.error(ResponseEnum.SPACE_USER_NON_OWNER_CANNOT_TRANSFER);
        }
        SpaceUser spaceUser = spaceUserService.getSpaceUserByUid(spaceId, uid);
        if (spaceUser == null) {
            return ApiResult.error(ResponseEnum.SPACE_USER_NOT_MEMBER);
        }
        spaceOwner.setRole(SpaceRoleEnum.ADMIN.getCode());
        spaceUser.setRole(SpaceRoleEnum.OWNER.getCode());
        if (spaceUserService.updateBatchById(Arrays.asList(spaceOwner, spaceUser))) {
            return ApiResult.success();
        } else {
            return ApiResult.error(ResponseEnum.SPACE_USER_TRANSFER_FAILED);
        }
    }


    /**
     * Get user restrictions
     *
     * @return
     */
    @Override
    public UserLimitVO getUserLimit() {
        Long spaceId = SpaceInfoUtil.getSpaceId();
        Space space = spaceService.getSpaceById(spaceId);
        if (space.getEnterpriseId() == null) {
            return getUserLimitVO(space.getType(), space.getUid());
        } else {
            Enterprise enterprise = enterpriseService.getEnterpriseById(space.getEnterpriseId());
            Integer total = 0;
            if (Objects.equals(enterprise.getServiceType(), EnterpriseServiceTypeEnum.ENTERPRISE.getCode())) {
                total = spaceLimitProperties.getEnterprise().getUserCount();
            } else if (Objects.equals(enterprise.getServiceType(), EnterpriseServiceTypeEnum.TEAM.getCode())) {
                total = spaceLimitProperties.getTeam().getUserCount();
            }
            UserLimitVO vo = new UserLimitVO();
            vo.setTotal(total);
            long used = spaceUserService.countBySpaceId(spaceId)
                            + inviteRecordService.countJoiningBySpaceId(spaceId);
            vo.setUsed((int) used);
            vo.setRemain(vo.getTotal() - vo.getUsed());
            return vo;
        }
    }

    @Override
    public UserLimitVO getUserLimit(String uid) {
        if (OrderInfoUtil.existValidProOrder(uid)) {
            return getUserLimitVO(SpaceTypeEnum.PRO.getCode(), uid);
        } else {
            return getUserLimitVO(SpaceTypeEnum.FREE.getCode(), uid);
        }
    }

    /**
     * Get user restrictions
     *
     * @return
     */
    @Override
    public UserLimitVO getUserLimitVO(Integer type, String uid) {
        UserLimitVO vo = new UserLimitVO();
        if (Objects.equals(type, SpaceTypeEnum.FREE.getCode())) {
            vo.setTotal(spaceLimitProperties.getFree().getUserCount());
            long used = spaceUserService.countFreeSpaceUser(uid)
                            + inviteRecordService.countJoiningByUid(uid, SpaceTypeEnum.FREE);
            vo.setUsed((int) used);
            vo.setRemain(vo.getTotal() - vo.getUsed());
        } else {
            vo.setTotal(spaceLimitProperties.getPro().getUserCount());
            long used = spaceUserService.countProSpaceUser(uid)
                            + inviteRecordService.countJoiningByUid(uid, SpaceTypeEnum.PRO);
            vo.setUsed((int) used);
            vo.setRemain(vo.getTotal() - vo.getUsed());
        }
        return vo;
    }


}
