package com.iflytek.astra.console.commons.service.space.impl;

import com.iflytek.astra.console.commons.entity.space.*;
import com.iflytek.astra.console.commons.service.space.*;
import com.iflytek.astra.console.commons.enums.space.SpaceTypeEnum;
import com.iflytek.astra.console.commons.util.space.OrderInfoUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Objects;

@Service
public class EnterpriseSpaceServiceImpl implements EnterpriseSpaceService {
    @Autowired
    private SpaceUserService spaceUserService;
    @Autowired
    private EnterpriseService enterpriseService;
    @Autowired
    private SpaceService spaceService;
    @Autowired
    private SpacePermissionService spacePermissionService;
    @Autowired
    private EnterprisePermissionService enterprisePermissionService;
    @Autowired
    private EnterpriseUserService enterpriseUserService;

    @Override
    @Transactional
    @Cacheable(value = "space:space_payer", key = "#spaceId", cacheManager = "cacheManager10s")
    public String getUidByCurrentSpaceId(Long spaceId) {
        if (spaceId == null) {
            return null;
        }
        Space space = spaceService.getSpaceById(spaceId);
        if (space == null) {
            return null;
        }
        if (space.getEnterpriseId() == null) {
            SpaceUser owner = spaceUserService.getSpaceOwner(spaceId);
            return owner == null ? null : owner.getUid().toString();
        }
        Enterprise enterprise = enterpriseService.getEnterpriseById(space.getEnterpriseId());
        return enterprise == null ? null : enterprise.getUid().toString();
    }

    @Override
    @Cacheable(value = "space:space_user", key = "#spaceId + '_' + #uid", cacheManager = "cacheManager10s")
    public SpaceUser checkUserBelongSpace(Long spaceId, String uid) {
        return spaceUserService.getSpaceUserByUid(spaceId, uid);
    }

    @Override
    @CacheEvict(value = "space:space_user", key = "#spaceId + '_' + #uid", cacheManager = "cacheManager10s")
    public void clearSpaceUserCache(Long spaceId, String uid) {

    }

    @Override
    @Cacheable(value = "space:enterprise_user", key = "#enterpriseId + '_' + #uid", cacheManager = "cacheManager10s")
    public EnterpriseUser checkUserBelongEnterprise(Long enterpriseId, String uid) {
        return enterpriseUserService.getEnterpriseUserByUid(enterpriseId, uid);
    }

    @Override
    @CacheEvict(value = "space:enterprise_user", key = "#enterpriseId + '_' + #uid", cacheManager = "cacheManager10s")
    public void clearEnterpriseUserCache(Long enterpriseId, String uid) {

    }

    @Override
    @Cacheable(value = "space:space_permission", key = "#key", cacheManager = "cacheManager10s")
    public SpacePermission getSpacePermissionByKey(String key) {
        return spacePermissionService.getSpacePermissionByKey(key);
    }

    @Override
    @Cacheable(value = "space:enterprise_permission", key = "#key", cacheManager = "cacheManager10s")
    public EnterprisePermission getEnterprisePermissionByKey(String key) {
        return enterprisePermissionService.getEnterprisePermissionByKey(key);
    }

    @Override
    @Cacheable(value = "space:enterprise_expired", key = "#enterpriseId", cacheManager = "cacheManager10s")
    @Transactional
    public boolean checkEnterpriseExpired(Long enterpriseId) {
        Enterprise enterprise = enterpriseService.getEnterpriseById(enterpriseId);
        if (enterprise == null) {
            return true;
        }
        LocalDateTime expireTime = enterprise.getExpireTime();
        return expireTime.isBefore(LocalDateTime.now());
    }

    @Override
    @Cacheable(value = "space:space_expired", key = "#spaceId", cacheManager = "cacheManager10s")
    public boolean checkSpaceExpired(Long spaceId) {
        Space space = spaceService.getSpaceById(spaceId);
        if (space == null) {
            return true;
        }
        // For enterprise team spaces, determine whether the enterprise team has expired
        if (space.getEnterpriseId() != null) {
            return this.checkEnterpriseExpired(space.getEnterpriseId());
        }
        if (Objects.equals(space.getType(), SpaceTypeEnum.PRO.getCode())) {
            return !OrderInfoUtil.existValidProOrder(space.getUid());
        }
        // Personal free spaces do not expire
        if (Objects.equals(space.getType(), SpaceTypeEnum.FREE.getCode())) {
            return false;
        }
        return false;
    }
}
