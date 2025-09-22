package com.iflytek.astra.console.commons.service.space.impl;

import cn.hutool.core.collection.CollectionUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.StringUtils;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.iflytek.astra.console.commons.data.UserInfoDataService;
import com.iflytek.astra.console.commons.entity.space.Space;
import com.iflytek.astra.console.commons.entity.space.SpaceUser;
import com.iflytek.astra.console.commons.entity.user.UserInfo;
import com.iflytek.astra.console.commons.mapper.space.SpaceMapper;
import com.iflytek.astra.console.commons.util.RequestContextUtil;
import com.iflytek.astra.console.commons.enums.space.SpaceRoleEnum;
import com.iflytek.astra.console.commons.enums.space.SpaceTypeEnum;
import com.iflytek.astra.console.commons.service.space.EnterpriseService;
import com.iflytek.astra.console.commons.service.space.SpaceService;
import com.iflytek.astra.console.commons.service.space.SpaceUserService;
import com.iflytek.astra.console.commons.util.space.EnterpriseInfoUtil;
import com.iflytek.astra.console.commons.util.space.SpaceInfoUtil;
import com.iflytek.astra.console.commons.dto.space.EnterpriseSpaceCountVO;
import com.iflytek.astra.console.commons.dto.space.SpaceVO;
import org.redisson.api.RedissonClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * Space service implementation
 */
@Service
public class SpaceServiceImpl extends ServiceImpl<SpaceMapper, Space> implements SpaceService {
    private static final String USER_LAST_VISIT_PERSONAL_SPACE_TIME = "USER_LAST_VISIT_PERSONAL_SPACE_TIME:";

    @Autowired
    private SpaceUserService spaceUserService;
    @Autowired
    private UserInfoDataService userInfoDataService;
    @Autowired
    private RedissonClient redissonClient;
    @Autowired
    private EnterpriseService enterpriseService;

    @Override
    public List<SpaceVO> recentVisitList() {
        String uid = RequestContextUtil.getUID();
        return this.baseMapper.recentVisitList(
                        uid,
                        EnterpriseInfoUtil.getEnterpriseId());
    }

    @Override
    public List<SpaceVO> personalList(String name) {
        String uid = RequestContextUtil.getUID();
        List<SpaceVO> spaceVOS = this.baseMapper.joinList(
                        uid,
                        EnterpriseInfoUtil.getEnterpriseId(), name);
        setSpaceVOExtraInfo(spaceVOS);
        return spaceVOS;
    }

    private void setSpaceVOExtraInfo(List<SpaceVO> spaceVOS) {
        if (CollectionUtil.isNotEmpty(spaceVOS)) {
            List<SpaceUser> allSpaceUsers = spaceUserService.getAllSpaceUsers(spaceVOS.stream().map(SpaceVO::getId).collect(Collectors.toList()));
            Map<Long, List<SpaceUser>> collect = allSpaceUsers.stream().collect(Collectors.groupingBy(SpaceUser::getSpaceId, Collectors.toList()));
            for (SpaceVO spaceVO : spaceVOS) {
                List<SpaceUser> spaceUsers = collect.get(spaceVO.getId());
                if (spaceUsers != null) {
                    spaceVO.setMemberCount(spaceUsers.size());
                    SpaceUser spaceUser = spaceUsers.stream()
                                    .filter(user -> Objects.equals(user.getRole(), SpaceRoleEnum.OWNER.getCode()))
                                    .findFirst()
                                    .orElse(null);
                    if (spaceUser != null) {
                        UserInfo userInfo = userInfoDataService.findByUid(spaceUser.getUid()).orElseThrow();
                        spaceVO.setOwnerName(userInfo.getNickname());
                    }
                }
            }
        }
    }

    @Override
    public List<SpaceVO> personalSelfList(String name) {
        List<SpaceVO> spaceVOS = this.baseMapper.selfList(
                        RequestContextUtil.getUID(),
                        SpaceRoleEnum.OWNER.getCode(),
                        EnterpriseInfoUtil.getEnterpriseId(), name);
        setSpaceVOExtraInfo(spaceVOS);
        return spaceVOS;
    }

    @Override
    public List<SpaceVO> corporateJoinList(String name) {
        List<SpaceVO> spaceVOS = this.baseMapper.joinList(
                        RequestContextUtil.getUID(),
                        EnterpriseInfoUtil.getEnterpriseId(), name);
        setSpaceVOExtraInfo(spaceVOS);
        return spaceVOS;
    }


    @Override
    public List<SpaceVO> corporateList(String name) {
        List<SpaceVO> spaceVOS = this.baseMapper.corporateList(
                        RequestContextUtil.getUID(),
                        EnterpriseInfoUtil.getEnterpriseId(), name);
        setSpaceVOExtraInfo(spaceVOS);
        return spaceVOS;
    }

    @Override
    public EnterpriseSpaceCountVO corporateCount() {
        Long enterpriseId = EnterpriseInfoUtil.getEnterpriseId();
        String uid = RequestContextUtil.getUID();
        return this.baseMapper.corporateCount(uid, enterpriseId);
    }

    @Override
    public SpaceVO getSpaceVO() {
        SpaceVO spaceVO = this.baseMapper.getByUidAndId(RequestContextUtil.getUID(), SpaceInfoUtil.getSpaceId());
        if (spaceVO == null) {
            return null;
        }
        List<SpaceUser> allSpaceUsers = spaceUserService.getAllSpaceUsers(spaceVO.getId());
        spaceVO.setMemberCount(allSpaceUsers.size());
        SpaceUser spaceUser = allSpaceUsers.stream()
                        .filter(user -> Objects.equals(user.getRole(), SpaceRoleEnum.OWNER.getCode()))
                        .findFirst()
                        .orElse(null);
        if (spaceUser != null) {
            UserInfo userInfo = userInfoDataService.findByUid(spaceUser.getUid()).orElseThrow();
            spaceVO.setOwnerName(userInfo.getNickname());
        }
        return spaceVO;
    }

    @Override
    public void setLastVisitPersonalSpaceTime() {
        redissonClient.getBucket(USER_LAST_VISIT_PERSONAL_SPACE_TIME + RequestContextUtil.getUID())
                        .set(Long.toString(System.currentTimeMillis()));
    }

    @Override
    public SpaceVO getLastVisitSpace() {
        String uid = RequestContextUtil.getUID();
        Long enterpriseId = EnterpriseInfoUtil.getEnterpriseId();
        // If user does not provide enterpriseId, get the last visited enterprise id
        if (enterpriseId == null) {
            enterpriseId = enterpriseService.getLastVisitEnterpriseId();
        }
        List<SpaceVO> spaceVOS = this.baseMapper.recentVisitList(uid, enterpriseId);
        if (CollectionUtil.isEmpty(spaceVOS)) {
            // If enterpriseId is not null, return a space object containing only enterpriseId
            if (enterpriseId != null) {
                SpaceVO spaceVO = new SpaceVO();
                spaceVO.setEnterpriseId(enterpriseId);
                return spaceVO;
            }
            return null;
        }
        String timestamp = redissonClient.getBucket(USER_LAST_VISIT_PERSONAL_SPACE_TIME + uid).get().toString();
        if (StringUtils.isBlank(timestamp)) {
            return this.baseMapper.getByUidAndId(uid, spaceVOS.get(0).getId());
        } else {
            LocalDateTime dateTime = Instant.ofEpochMilli(Long.parseLong(timestamp)).atZone(ZoneId.systemDefault()).toLocalDateTime();
            if (dateTime.isAfter(spaceVOS.get(0).getLastVisitTime())) {
                return null;
            } else {
                return this.baseMapper.getByUidAndId(uid, spaceVOS.get(0).getId());
            }
        }
    }


    @Override
    public Long countByEnterpriseId(Long enterpriseId) {
        return this.count(Wrappers.<Space>lambdaQuery()
                        .eq(Space::getEnterpriseId, enterpriseId));
    }

    @Override
    public Long countByUid(String uid) {
        return this.count(Wrappers.<Space>lambdaQuery()
                        .eq(Space::getUid, uid)
                        .isNull(Space::getEnterpriseId));
    }

    @Override
    public Space getSpaceById(Long id) {
        return this.getById(id);
    }

    @Override
    public List<SpaceVO> listByEnterpriseIdAndUid(Long enterpriseId, String uid) {
        return this.baseMapper.joinList(uid, enterpriseId, null);
    }

    @Override
    public boolean checkExistByName(String name, Long id) {
        LambdaQueryWrapper<Space> queryWrapper = Wrappers.<Space>lambdaQuery()
                        .eq(Space::getName, name);
        Long enterpriseId = EnterpriseInfoUtil.getEnterpriseId();
        if (enterpriseId != null) {
            queryWrapper = queryWrapper.eq(Space::getEnterpriseId, enterpriseId);
        } else {
            String uid = RequestContextUtil.getUID();
            queryWrapper = queryWrapper.eq(Space::getUid, uid)
                            .isNull(Space::getEnterpriseId);
        }
        if (id != null) {
            queryWrapper = queryWrapper.ne(Space::getId, id);
            return this.count(queryWrapper) > 0;
        } else {
            return this.count(queryWrapper) > 0;
        }
    }

    @Override
    public SpaceTypeEnum getSpaceType(Long spaceId) {
        if (spaceId == null) {
            return SpaceTypeEnum.FREE;
        }
        Space space = this.getById(spaceId);
        if (space != null) {
            return SpaceTypeEnum.getByCode(space.getType());
        }
        return null;
    }


    @Override
    public boolean save(Space entity) {
        return super.save(entity);
    }

    @Override
    public Space getById(Long id) {
        return super.getById(id);
    }

    @Override
    public boolean removeById(Long id) {
        return super.removeById(id);
    }

    @Override
    public boolean updateById(Space entity) {
        return super.updateById(entity);
    }
}
