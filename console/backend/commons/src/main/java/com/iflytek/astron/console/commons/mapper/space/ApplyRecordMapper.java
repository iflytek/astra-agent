package com.iflytek.astron.console.commons.mapper.space;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.iflytek.astron.console.commons.dto.space.ApplyRecordVO;
import com.iflytek.astron.console.commons.entity.space.ApplyRecord;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface ApplyRecordMapper extends BaseMapper<ApplyRecord> {
    Page<ApplyRecordVO> selectVOPageByParam(Page<ApplyRecord> page,
            @Param("spaceId") Long spaceId,
            @Param("enterpriseId") Long enterpriseId,
            @Param("nickname") String nickname,
            @Param("status") Integer status);
}
