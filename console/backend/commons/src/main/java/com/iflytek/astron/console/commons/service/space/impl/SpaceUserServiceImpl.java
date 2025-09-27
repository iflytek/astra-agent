package com.iflytek.astron.console.commons.service.space.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.iflytek.astron.console.commons.data.UserInfoDataService;
import com.iflytek.astron.console.commons.dto.space.SpaceUserParam;
import com.iflytek.astron.console.commons.entity.space.SpaceUser;
import com.iflytek.astron.console.commons.entity.user.UserInfo;
import com.iflytek.astron.console.commons.enums.space.SpaceRoleEnum;
import com.iflytek.astron.console.commons.enums.space.SpaceTypeEnum;
import com.iflytek.astron.console.commons.mapper.space.SpaceUserMapper;
import com.iflytek.astron.console.commons.service.space.SpaceUserService;
import com.iflytek.astron.console.commons.util.space.SpaceInfoUtil;
import com.iflytek.astron.console.commons.dto.space.SpaceUserVO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.Date;
import java.util.List;

/**
 * Space users
 */
@Service
public class SpaceUserServiceImpl extends ServiceImpl<SpaceUserMapper, SpaceUser> implements SpaceUserService {

    @Autowired
    private UserInfoDataService userInfoDataService;

    @Override
    @Transactional
    public boolean addSpaceUser(Long spaceId, String uid, SpaceRoleEnum roleEnum) {
        // Check whether the user already exists
        SpaceUser spaceUser1 = this.getSpaceUserByUid(spaceId, uid);
        if (spaceUser1 != null) {
            if (!spaceUser1.getRole().equals(roleEnum.getCode())) {
                spaceUser1.setRole(roleEnum.getCode());
                return this.updateById(spaceUser1);
            }
            return true;
        }
        SpaceUser spaceUser = new SpaceUser();
        spaceUser.setSpaceId(spaceId);
        UserInfo userInfo = userInfoDataService.findByUid(uid).orElseThrow();
        spaceUser.setNickname(userInfo.getNickname());
        spaceUser.setUid(uid);
        spaceUser.setRole(roleEnum.getCode());
        return this.save(spaceUser);
    }

    @Override
    public List<SpaceUser> listSpaceMember() {
        Long spaceId = SpaceInfoUtil.getSpaceId();
        List<SpaceUser> list = this.list(Wrappers.<SpaceUser>lambdaQuery()
                .ne(SpaceUser::getRole, SpaceRoleEnum.OWNER.getCode())
                .eq(SpaceUser::getSpaceId, spaceId));
        return list;
    }

    @Override
    public SpaceUser getSpaceUserByUid(Long spaceId, String uid) {
        return baseMapper.getByUidAndSpaceId(uid, spaceId);
    }

    @Override
    public Long countSpaceUserByUids(Long spaceId, List<String> uids) {
        return baseMapper.selectCount(Wrappers.<SpaceUser>lambdaQuery()
                .eq(SpaceUser::getSpaceId, spaceId)
                .in(SpaceUser::getUid, uids));
    }

    @Override
    public Long countBySpaceId(Long spaceId) {
        return baseMapper.selectCount(Wrappers.<SpaceUser>lambdaQuery()
                .eq(SpaceUser::getSpaceId, spaceId));
    }

    @Override
    public boolean updateVisitTime(Long spaceId, String uid) {
        return this.update(Wrappers.<SpaceUser>lambdaUpdate()
                .set(SpaceUser::getLastVisitTime, new Date())
                .eq(SpaceUser::getSpaceId, spaceId)
                .eq(SpaceUser::getUid, uid));
    }

    @Override
    public boolean removeByUid(Collection<Long> spaceIds, String uid) {
        return this.remove(Wrappers.<SpaceUser>lambdaUpdate()
                .eq(SpaceUser::getUid, uid)
                .in(SpaceUser::getSpaceId, spaceIds));
    }

    @Override
    public List<SpaceUser> getAllSpaceUsers(Long spaceId) {
        return this.list(Wrappers.<SpaceUser>lambdaQuery()
                .eq(SpaceUser::getSpaceId, spaceId));
    }

    @Override
    public List<SpaceUser> getAllSpaceUsers(List<Long> spaceIds) {
        return this.list(Wrappers.<SpaceUser>lambdaQuery()
                .in(SpaceUser::getSpaceId, spaceIds));
    }


    @Override
    public Long countFreeSpaceUser(String uid) {
        return this.baseMapper.countPersonalSpaceUser(uid, SpaceRoleEnum.OWNER.getCode(), SpaceTypeEnum.FREE.getCode());
    }

    @Override
    public Long countProSpaceUser(String uid) {
        return this.baseMapper.countPersonalSpaceUser(uid, SpaceRoleEnum.OWNER.getCode(), SpaceTypeEnum.PRO.getCode());
    }

    @Override
    public SpaceUser getSpaceOwner(Long spaceId) {
        return this.getOne(Wrappers.<SpaceUser>lambdaQuery()
                .eq(SpaceUser::getSpaceId, spaceId)
                .eq(SpaceUser::getRole, SpaceRoleEnum.OWNER.getCode()));
    }

    @Override
    public Page<SpaceUserVO> page(SpaceUserParam param) {
        Page<SpaceUser> page = new Page<>();
        page.setSize(param.getPageSize());
        page.setCurrent(param.getPageNum());
        Long spaceId = SpaceInfoUtil.getSpaceId();
        if (spaceId == null) {
            return Page.of(param.getPageNum(), param.getPageSize());
        }
        return this.baseMapper.selectVOPageByParam(page, spaceId, param.getNickname(), param.getRole());
    }

    @Override
    public boolean save(SpaceUser entity) {
        return super.save(entity);
    }

    @Override
    public boolean updateById(SpaceUser entity) {
        return super.updateById(entity);
    }

    @Override
    public boolean updateBatchById(Collection<SpaceUser> entityList) {
        return super.updateBatchById(entityList);
    }

    @Override
    public boolean removeById(SpaceUser spaceUser) {
        return super.removeById(spaceUser);
    }

    /**
     * Get the space user's role
     *
     * @param spaceId space id
     * @param uid user uid
     * @return null if not exists
     */
    @Override
    public SpaceRoleEnum getRole(Long spaceId, String uid) {
        SpaceUser spaceUser = this.getSpaceUserByUid(spaceId, uid);
        if (spaceUser == null) {
            return null;
        }
        return SpaceRoleEnum.getByCode(spaceUser.getRole());
    }
}
