package com.iflytek.astron.console.commons.service.space;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.iflytek.astron.console.commons.dto.space.ApplyRecordParam;
import com.iflytek.astron.console.commons.dto.space.ApplyRecordVO;
import com.iflytek.astron.console.commons.entity.space.ApplyRecord;

/**
 * Application records for joining space/enterprise
 */
public interface ApplyRecordService {


    Page<ApplyRecordVO> page(ApplyRecordParam param);

    ApplyRecord getByUidAndSpaceId(String uid, Long spaceId);

    boolean updateById(ApplyRecord applyRecord);

    boolean save(ApplyRecord applyRecord);

    ApplyRecord getById(Long id);

}
