package com.iflytek.astra.console.hub.service.space.impl;

import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.iflytek.astra.console.commons.constant.ResponseEnum;
import com.iflytek.astra.console.commons.exception.BusinessException;
import com.iflytek.astra.console.commons.response.ApiResult;
import com.iflytek.astra.console.commons.util.RequestContextUtil;
import com.iflytek.astra.console.commons.dto.space.EnterpriseAddDTO;
import com.iflytek.astra.console.commons.entity.space.Enterprise;
import com.iflytek.astra.console.commons.entity.space.EnterpriseUser;
import com.iflytek.astra.console.commons.enums.space.EnterpriseRoleEnum;
import com.iflytek.astra.console.commons.mapper.space.EnterpriseMapper;
import com.iflytek.astra.console.commons.service.space.EnterpriseService;
import com.iflytek.astra.console.commons.service.space.EnterpriseUserService;
import com.iflytek.astra.console.hub.service.space.EnterpriseBizService;
import com.iflytek.astra.console.commons.util.space.EnterpriseInfoUtil;
import com.iflytek.astra.console.commons.util.space.OrderInfoUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Slf4j
public class EnterpriseBizServiceImpl implements EnterpriseBizService {

    @Autowired
    private EnterpriseMapper enterpriseMapper;
    @Autowired
    private EnterpriseUserService enterpriseUserService;
    @Autowired
    private EnterpriseService enterpriseService;

    @Override
    public ApiResult<Boolean> visitEnterprise(Long enterpriseId) {
        String uid = RequestContextUtil.getUID();
        if (enterpriseId == null || enterpriseId <= 0L) {
            return ApiResult.success(enterpriseService.setLastVisitEnterpriseId(null));
        }
        Enterprise enterprise = enterpriseService.getEnterpriseById(enterpriseId);
        if (enterprise == null) {
            return ApiResult.error(ResponseEnum.ENTERPRISE_NOT_EXISTS);
        }
        EnterpriseUser enterpriseUser = enterpriseUserService.getEnterpriseUserByUid(enterpriseId, uid);
        if (enterpriseUser == null) {
            return ApiResult.error(ResponseEnum.ENTERPRISE_USER_NOT_IN_ENTERPRISE);
        }
        return ApiResult.success(enterpriseService.setLastVisitEnterpriseId(enterpriseId));
    }

    /**
     * Create enterprise team
     *
     * @param enterpriseAddDTO
     * @return
     */
    @Override
    @Transactional
    public ApiResult<Long> create(EnterpriseAddDTO enterpriseAddDTO) {
        String uid = RequestContextUtil.getUID();
        // Get user purchase plan information
        OrderInfoUtil.EnterpriseResult enterpriseResult = OrderInfoUtil.getEnterpriseResult(uid);
        if (enterpriseResult == null) {
            return ApiResult.error(ResponseEnum.ENTERPRISE_PLEASE_BUY_PLAN_FIRST);
        }
        if (enterpriseService.checkExistByName(enterpriseAddDTO.getName(), null)) {
            return ApiResult.error(ResponseEnum.ENTERPRISE_NAME_EXISTS);
        }
        if (enterpriseService.checkExistByUid(uid)) {
            return ApiResult.error(ResponseEnum.ENTERPRISE_USER_ALREADY_CREATED_ENTERPRISE);
        }
        // Save team data
        Enterprise enterprise = new Enterprise();
        enterprise.setName(enterpriseAddDTO.getName());
        enterprise.setAvatarUrl(enterpriseAddDTO.getAvatarUrl());
        enterprise.setUid(uid);
        enterprise.setOrgId(IdWorker.getId());
        enterprise.setServiceType(enterpriseResult.getServiceType().getCode());
        enterprise.setExpireTime(enterpriseResult.getEndTime());
        if (enterpriseService.save(enterprise)) {
            // Creator becomes enterprise super admin by default
            if (!enterpriseUserService.addEnterpriseUser(enterprise.getId(), enterprise.getUid(), EnterpriseRoleEnum.OFFICER)) {
                throw new BusinessException(ResponseEnum.INVITE_ADD_TEAM_USER_FAILED);
            }
            return ApiResult.success(enterprise.getId());
        }
        return ApiResult.error(ResponseEnum.ENTERPRISE_CREATE_FAILED);
    }

    /**
     * Update team name
     *
     * @param name
     * @return
     */
    @Override
    @Transactional
    public ApiResult<String> updateName(String name) {
        Long enterpriseId = EnterpriseInfoUtil.getEnterpriseId();
        if (enterpriseService.checkExistByName(name, enterpriseId)) {
            return ApiResult.error(ResponseEnum.ENTERPRISE_NAME_EXISTS);
        }
        Enterprise enterprise = enterpriseService.getById(enterpriseId);
        if (enterprise == null) {
            return ApiResult.error(ResponseEnum.ENTERPRISE_NOT_EXISTS);
        }
        enterprise.setName(name);
        if (enterpriseService.updateById(enterprise)) {
            return ApiResult.of(ResponseEnum.ENTERPRISE_UPDATE_SUCCESS, null);
        } else {
            return ApiResult.error(ResponseEnum.ENTERPRISE_UPDATE_FAILED);
        }
    }

    /**
     * Update team logo
     *
     * @param logoUrl
     * @return
     */
    @Override
    @Transactional
    public ApiResult<String> updateLogo(String logoUrl) {
        Long enterpriseId = EnterpriseInfoUtil.getEnterpriseId();
        Enterprise enterprise = enterpriseService.getById(enterpriseId);
        if (enterprise == null) {
            return ApiResult.error(ResponseEnum.ENTERPRISE_NOT_EXISTS);
        }
        enterprise.setLogoUrl(logoUrl);
        if (enterpriseService.updateById(enterprise)) {
            return ApiResult.of(ResponseEnum.ENTERPRISE_UPDATE_SUCCESS, null);
        } else {
            return ApiResult.error(ResponseEnum.ENTERPRISE_UPDATE_FAILED);
        }
    }

    /**
     * Update team avatar
     *
     * @param avatarUrl
     * @return
     */
    @Override
    @Transactional
    public ApiResult<String> updateAvatar(String avatarUrl) {
        Long enterpriseId = EnterpriseInfoUtil.getEnterpriseId();
        Enterprise enterprise = enterpriseService.getById(enterpriseId);
        if (enterprise == null) {
            return ApiResult.error(ResponseEnum.ENTERPRISE_NOT_EXISTS);
        }
        enterprise.setAvatarUrl(avatarUrl);
        if (enterpriseService.updateById(enterprise)) {
            return ApiResult.of(ResponseEnum.ENTERPRISE_UPDATE_SUCCESS, null);
        } else {
            return ApiResult.error(ResponseEnum.ENTERPRISE_UPDATE_FAILED);
        }
    }
}
