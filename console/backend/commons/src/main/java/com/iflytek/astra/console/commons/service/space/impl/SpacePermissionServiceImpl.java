package com.iflytek.astra.console.commons.service.space.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.iflytek.astra.console.commons.entity.space.SpacePermission;
import com.iflytek.astra.console.commons.mapper.space.SpacePermissionMapper;
import com.iflytek.astra.console.commons.service.space.SpacePermissionService;
import org.springframework.stereotype.Service;

import java.util.Collection;
import java.util.List;

/**
 * Space role permission configuration
 */
@Service
public class SpacePermissionServiceImpl extends ServiceImpl<SpacePermissionMapper, SpacePermission> implements SpacePermissionService {
    @Override
    public SpacePermission getSpacePermissionByKey(String key) {
        return this.getOne(Wrappers.<SpacePermission>lambdaQuery()
                .eq(SpacePermission::getPermissionKey, key));
    }

    @Override
    public List<String> listByKeys(Collection<String> keys) {
        return this.listObjs(Wrappers.<SpacePermission>lambdaQuery()
                .select(SpacePermission::getPermissionKey)
                .in(SpacePermission::getPermissionKey, keys));
    }

    @Override
    public void insertBatch(List<SpacePermission> spacePermissions) {
        this.saveBatch(spacePermissions);
    }
}
