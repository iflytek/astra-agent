package com.iflytek.astra.console.commons.mapper.space;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.iflytek.astra.console.commons.dto.space.EnterpriseVO;
import com.iflytek.astra.console.commons.entity.space.Enterprise;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

@Mapper
public interface EnterpriseMapper extends BaseMapper<Enterprise> {
    List<EnterpriseVO> selectByJoinUid(String joinUid);
}
