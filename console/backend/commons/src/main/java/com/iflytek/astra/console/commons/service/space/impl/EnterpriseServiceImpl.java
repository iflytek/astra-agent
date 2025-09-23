package com.iflytek.astra.console.commons.service.space.impl;

import com.baomidou.mybatisplus.core.toolkit.StringUtils;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.iflytek.astra.console.commons.data.UserInfoDataService;
import com.iflytek.astra.console.commons.entity.user.UserInfo;
import com.iflytek.astra.console.commons.util.RequestContextUtil;
import com.iflytek.astra.console.commons.entity.space.Enterprise;
import com.iflytek.astra.console.commons.entity.space.EnterpriseUser;
import com.iflytek.astra.console.commons.mapper.space.EnterpriseMapper;
import com.iflytek.astra.console.commons.service.space.EnterpriseService;
import com.iflytek.astra.console.commons.service.space.EnterpriseUserService;
import com.iflytek.astra.console.commons.util.space.EnterpriseInfoUtil;
import com.iflytek.astra.console.commons.dto.space.EnterpriseVO;
import org.redisson.api.RedissonClient;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Enterprise team service implementation
 */
@Service
public class EnterpriseServiceImpl extends ServiceImpl<EnterpriseMapper, Enterprise> implements EnterpriseService {
    private static final String USER_LAST_VISIT_ENTERPRISE_ID = "USER_LAST_VISIT_ENTERPRISE_ID:";
    @Autowired
    private UserInfoDataService userInfoDataService;
    @Autowired
    private EnterpriseUserService enterpriseUserService;
    @Autowired
    private RedissonClient redissonClient;

    @Override
    public boolean setLastVisitEnterpriseId(Long enterpriseId) {
        String uid = RequestContextUtil.getUID();
        String key = USER_LAST_VISIT_ENTERPRISE_ID + uid;
        if (enterpriseId == null) {
            return redissonClient.getBucket(key).delete();
        } else {
            redissonClient.getBucket(key).set(Long.toString(enterpriseId));
            return true;
        }
    }

    @Override
    public Long getLastVisitEnterpriseId() {
        String uid = RequestContextUtil.getUID();
        String key = USER_LAST_VISIT_ENTERPRISE_ID + uid;
        Object idObj = redissonClient.getBucket(key).get();
        if (idObj == null) {
            return null;
        }
        String idStr = idObj.toString();
        if (StringUtils.isNotBlank(idStr)) {
            return Long.valueOf(idStr);
        }
        return null;
    }

    @Override
    public Integer checkNeedCreateTeam() {
        // TODO refactor: never need to create team for now.
        return 0;
        // String uid = RequestContextUtil.getUID();
        // Enterprise enterprise = getEnterpriseByUid(uid);
        // if (enterprise != null) {
        // return 0;
        // }
        // OrderInfoUtil.EnterpriseResult result = OrderInfoUtil.getEnterpriseResult(uid);
        // if (result.getServiceType() == null) {
        // return 0;
        // }
        // return result.getServiceType().getCode();
    }

    @Override
    @Transactional
    public void orderChangeNotify(String uid, LocalDateTime endTime) {
        Enterprise enterprise = this.baseMapper.selectOne(Wrappers.<Enterprise>lambdaQuery()
                .eq(Enterprise::getUid, uid));
        if (enterprise != null) {
            enterprise.setExpireTime(endTime);
            this.updateById(enterprise);
        }
    }

    /**
     * Check whether the user has a valid enterprise edition service.
     *
     * @implNote This will be implemented in the commercial edition.
     */
    @Override
    public boolean checkCertification() {
        // The order sub-system check logic has been removed
        throw new UnsupportedOperationException();
    }

    @Override
    public EnterpriseVO detail() {
        String uid = RequestContextUtil.getUID();
        Long enterpriseId = EnterpriseInfoUtil.getEnterpriseId();
        if (enterpriseId == null) {
            return null;
        }
        Enterprise enterprise = this.getById(enterpriseId);
        if (enterprise == null) {
            return null;
        }
        EnterpriseVO vo = new EnterpriseVO();
        BeanUtils.copyProperties(enterprise, vo);
        UserInfo userInfo = userInfoDataService.findByUid(vo.getUid()).orElseThrow();
        vo.setOfficerName(userInfo.getNickname());
        EnterpriseUser enterpriseUser = enterpriseUserService.getEnterpriseUserByUid(enterpriseId, uid);
        vo.setRole(enterpriseUser.getRole());
        return vo;
    }

    @Override
    public List<EnterpriseVO> joinList() {
        String uid = RequestContextUtil.getUID();
        return this.baseMapper.selectByJoinUid(uid);
    }

    @Override
    public boolean checkExistByName(String name, Long id) {
        if (id != null) {
            return this.count(Wrappers.<Enterprise>lambdaQuery()
                    .eq(Enterprise::getName, name)
                    .ne(Enterprise::getId, id)) > 0;
        } else {
            return this.count(Wrappers.<Enterprise>lambdaQuery()
                    .eq(Enterprise::getName, name)) > 0;
        }
    }

    @Override
    public boolean checkExistByUid(String uid) {
        return this.count(Wrappers.<Enterprise>lambdaQuery()
                .eq(Enterprise::getUid, uid)) > 0;
    }

    @Override
    public Enterprise getEnterpriseById(Long id) {
        return this.getById(id);
    }

    @Override
    public Enterprise getEnterpriseByUid(String uid) {
        return this.baseMapper.selectOne(Wrappers.<Enterprise>lambdaQuery()
                .eq(Enterprise::getUid, uid));
    }

    @Override
    public String getUidByEnterpriseId(Long enterpriseId) {
        return getEnterpriseById(enterpriseId).getUid();
    }

    @Override
    public int updateExpireTime(Enterprise enterprise) {
        return this.baseMapper.update(Wrappers.<Enterprise>lambdaUpdate()
                .set(Enterprise::getExpireTime, enterprise.getExpireTime())
                .eq(Enterprise::getId, enterprise.getId()));
    }

    @Override
    public boolean save(Enterprise entity) {
        return super.save(entity);
    }

    @Override
    public boolean updateById(Enterprise entity) {
        return super.updateById(entity);
    }

    @Override
    public Enterprise getById(Long id) {
        return super.getById(id);
    }
}
