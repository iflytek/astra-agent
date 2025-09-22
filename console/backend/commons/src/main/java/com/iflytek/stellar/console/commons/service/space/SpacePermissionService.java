package com.iflytek.astra.console.commons.service.space;

import com.iflytek.astra.console.commons.entity.space.SpacePermission;

import java.util.Collection;
import java.util.List;

/**
 * Space role permission configuration
 */
public interface SpacePermissionService {

    SpacePermission getSpacePermissionByKey(String key);

    List<String> listByKeys(Collection<String> keys);

    void insertBatch(List<SpacePermission> spacePermissions);

}
