package com.iflytek.astron.console.commons.service.space.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.iflytek.astron.console.commons.entity.space.EnterprisePermission;
import com.iflytek.astron.console.commons.mapper.space.EnterprisePermissionMapper;
import com.iflytek.astron.console.commons.service.space.EnterprisePermissionService;
import org.springframework.stereotype.Service;

import java.util.Collection;
import java.util.List;


/**
 * Enterprise team role permission configuration
 */
@Service
public class EnterprisePermissionServiceImpl extends ServiceImpl<EnterprisePermissionMapper, EnterprisePermission> implements EnterprisePermissionService {
    @Override
    public EnterprisePermission getEnterprisePermissionByKey(String key) {
        return this.getOne(Wrappers.<EnterprisePermission>lambdaQuery()
                .eq(EnterprisePermission::getPermissionKey, key));
    }

    @Override
    public List<String> listByKeys(Collection<String> keys) {
        return this.listObjs(Wrappers.<EnterprisePermission>lambdaQuery()
                .select(EnterprisePermission::getPermissionKey)
                .in(EnterprisePermission::getPermissionKey, keys));
    }

    @Override
    public void insertBatch(List<EnterprisePermission> enterprisePermissions) {
        this.saveBatch(enterprisePermissions);
    }
}
