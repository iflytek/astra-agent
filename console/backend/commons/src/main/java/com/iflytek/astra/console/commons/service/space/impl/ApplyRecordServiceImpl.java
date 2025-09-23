package com.iflytek.astra.console.commons.service.space.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.iflytek.astra.console.commons.dto.space.ApplyRecordParam;
import com.iflytek.astra.console.commons.entity.space.ApplyRecord;
import com.iflytek.astra.console.commons.mapper.space.ApplyRecordMapper;
import com.iflytek.astra.console.commons.service.space.ApplyRecordService;
import com.iflytek.astra.console.commons.util.space.SpaceInfoUtil;
import com.iflytek.astra.console.commons.dto.space.ApplyRecordVO;
import org.springframework.stereotype.Service;

/**
 * Application records for joining space/enterprise
 */
@Service
public class ApplyRecordServiceImpl extends ServiceImpl<ApplyRecordMapper, ApplyRecord> implements ApplyRecordService {


    @Override
    public Page<ApplyRecordVO> page(ApplyRecordParam param) {
        Page<ApplyRecord> page = new Page<>();
        page.setSize(param.getPageSize());
        page.setCurrent(param.getPageNum());
        Long spaceId = SpaceInfoUtil.getSpaceId();
        if (spaceId == null) {
            return Page.of(param.getPageNum(), param.getPageSize());
        }
        return this.baseMapper.selectVOPageByParam(page, spaceId, null, param.getNickname(), param.getStatus());
    }

    @Override
    public ApplyRecord getByUidAndSpaceId(String uid, Long spaceId) {
        return this.baseMapper.selectOne(Wrappers.<ApplyRecord>lambdaQuery()
                .eq(ApplyRecord::getApplyUid, uid)
                .eq(ApplyRecord::getSpaceId, spaceId)
                .eq(ApplyRecord::getStatus, ApplyRecord.Status.APPLYING.getCode()));
    }

    @Override
    public ApplyRecord getById(Long id) {
        return super.getById(id);
    }

    @Override
    public boolean updateById(ApplyRecord entity) {
        return super.updateById(entity);
    }

    @Override
    public boolean save(ApplyRecord entity) {
        return super.save(entity);
    }
}
