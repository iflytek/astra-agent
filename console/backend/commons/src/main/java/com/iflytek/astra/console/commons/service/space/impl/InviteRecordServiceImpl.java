package com.iflytek.astra.console.commons.service.space.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.iflytek.astra.console.commons.dto.space.InviteRecordParam;
import com.iflytek.astra.console.commons.entity.space.InviteRecord;
import com.iflytek.astra.console.commons.enums.space.InviteRecordStatusEnum;
import com.iflytek.astra.console.commons.enums.space.InviteRecordTypeEnum;
import com.iflytek.astra.console.commons.enums.space.SpaceTypeEnum;
import com.iflytek.astra.console.commons.mapper.space.InviteRecordMapper;
import com.iflytek.astra.console.commons.service.space.InviteRecordService;
import com.iflytek.astra.console.commons.util.space.EnterpriseInfoUtil;
import com.iflytek.astra.console.commons.util.space.SpaceInfoUtil;
import com.iflytek.astra.console.commons.dto.space.InviteRecordVO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Invitation records
 */
@Service
@Slf4j
public class InviteRecordServiceImpl extends ServiceImpl<InviteRecordMapper, InviteRecord> implements InviteRecordService {

    @Override
    public Page<InviteRecordVO> inviteList(InviteRecordParam param, InviteRecordTypeEnum type) {
        Page<InviteRecord> page = new Page<>();
        page.setSize(param.getPageSize());
        page.setCurrent(param.getPageNum());
        Long spaceId = null;
        Integer recordType = type.getCode();
        if (type == InviteRecordTypeEnum.SPACE) {
            spaceId = SpaceInfoUtil.getSpaceId();
        }
        Long enterpriseId = null;
        if (type == InviteRecordTypeEnum.ENTERPRISE) {
            enterpriseId = EnterpriseInfoUtil.getEnterpriseId();
            recordType = null;
        }
        if (spaceId == null && enterpriseId == null) {
            return Page.of(param.getPageNum(), param.getPageSize());
        }
        return this.baseMapper.selectVOPageByParam(page,
                        recordType, spaceId, enterpriseId,
                        param.getNickname(), param.getStatus());
    }

    @Override
    public Long countBySpaceIdAndUids(Long spaceId, List<String> uids) {
        return this.baseMapper.selectCount(Wrappers.<InviteRecord>lambdaQuery()
                        .in(InviteRecord::getInviteeUid, uids)
                        .gt(InviteRecord::getExpireTime, LocalDateTime.now())
                        .eq(InviteRecord::getType, InviteRecordTypeEnum.SPACE.getCode())
                        .eq(InviteRecord::getStatus, InviteRecordStatusEnum.INIT.getCode())
                        .eq(InviteRecord::getSpaceId, spaceId));
    }

    @Override
    public Long countByEnterpriseIdAndUids(Long enterpriseId, List<String> uids) {
        return this.baseMapper.selectCount(Wrappers.<InviteRecord>lambdaQuery()
                        .in(InviteRecord::getInviteeUid, uids)
                        .gt(InviteRecord::getExpireTime, LocalDateTime.now())
                        .eq(InviteRecord::getType, InviteRecordTypeEnum.ENTERPRISE.getCode())
                        .eq(InviteRecord::getStatus, InviteRecordStatusEnum.INIT.getCode())
                        .eq(InviteRecord::getEnterpriseId, enterpriseId));
    }

    @Override
    public Long countJoiningByEnterpriseId(Long enterpriseId) {
        return this.baseMapper.countJoiningByEnterpriseId(enterpriseId);
    }

    @Override
    public Long countJoiningBySpaceId(Long spaceId) {
        return this.baseMapper.countJoiningBySpaceId(spaceId);
    }

    @Override
    public Long countJoiningByUid(String uid, SpaceTypeEnum spaceTypeEnum) {
        return this.baseMapper.countJoiningByUid(uid, spaceTypeEnum.getCode());
    }

    @Override
    public boolean saveBatch(Collection<InviteRecord> entityList) {
        return super.saveBatch(entityList);
    }

    @Override
    public InviteRecord getById(Long id) {
        return super.getById(id);
    }

    @Override
    public boolean updateById(InviteRecord entity) {
        return super.updateById(entity);
    }

    @Override
    public InviteRecordVO selectVOById(Long id) {
        return this.baseMapper.selectVOById(id);
    }

    @Override
    @Scheduled(cron = "0 0 0 * * ?")
    public int updateExpireRecord() {
        log.info("Start updating expired invitation records");
        int updated = this.baseMapper.update(Wrappers.<InviteRecord>lambdaUpdate()
                        .set(InviteRecord::getStatus, InviteRecordStatusEnum.EXPIRED.getCode())
                        .eq(InviteRecord::getStatus, InviteRecordStatusEnum.INIT.getCode())
                        .lt(InviteRecord::getExpireTime, LocalDateTime.now()));
        log.info("Finished updating expired invitation records, updated {} rows", updated);
        return updated;
    }

    @Override
    public Set<String> getInvitingUids(InviteRecordTypeEnum type) {
        LambdaQueryWrapper<InviteRecord> wrapper = Wrappers.<InviteRecord>lambdaQuery()
                        .eq(InviteRecord::getStatus, InviteRecordStatusEnum.INIT.getCode())
                        .gt(InviteRecord::getExpireTime, LocalDateTime.now());
        if (type == InviteRecordTypeEnum.SPACE) {
            Long spaceId = SpaceInfoUtil.getSpaceId();
            wrapper.eq(InviteRecord::getSpaceId, spaceId).eq(InviteRecord::getType, type.getCode());
        } else {
            Long enterpriseId = EnterpriseInfoUtil.getEnterpriseId();
            wrapper.eq(InviteRecord::getEnterpriseId, enterpriseId).eq(InviteRecord::getType, type.getCode());
        }
        List<InviteRecord> inviteRecords = this.baseMapper.selectList(wrapper);
        return inviteRecords.stream().map(InviteRecord::getInviteeUid).collect(Collectors.toSet());
    }


}
