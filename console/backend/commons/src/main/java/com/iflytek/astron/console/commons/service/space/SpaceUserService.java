package com.iflytek.astron.console.commons.service.space;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.iflytek.astron.console.commons.dto.space.SpaceUserParam;
import com.iflytek.astron.console.commons.entity.space.SpaceUser;
import com.iflytek.astron.console.commons.enums.space.SpaceRoleEnum;
import com.iflytek.astron.console.commons.dto.space.SpaceUserVO;

import java.util.Collection;
import java.util.List;

/**
 * Space users
 */
public interface SpaceUserService {

    boolean addSpaceUser(Long spaceId, String uid, SpaceRoleEnum roleEnum);

    List<SpaceUser> listSpaceMember();

    SpaceUser getSpaceUserByUid(Long spaceId, String uid);

    Long countSpaceUserByUids(Long spaceId, List<String> uids);

    Long countBySpaceId(Long spaceId);

    boolean updateVisitTime(Long spaceId, String uid);

    boolean removeByUid(Collection<Long> spaceIds, String uid);

    List<SpaceUser> getAllSpaceUsers(Long spaceId);

    List<SpaceUser> getAllSpaceUsers(List<Long> spaceIds);

    Long countFreeSpaceUser(String uid);

    Long countProSpaceUser(String uid);

    SpaceUser getSpaceOwner(Long spaceId);

    Page<SpaceUserVO> page(SpaceUserParam param);

    boolean save(SpaceUser spaceUser);

    boolean updateById(SpaceUser spaceUser);

    boolean updateBatchById(Collection<SpaceUser> entityList);

    boolean removeById(SpaceUser spaceUser);

    SpaceRoleEnum getRole(Long spaceId, String uid);

}
