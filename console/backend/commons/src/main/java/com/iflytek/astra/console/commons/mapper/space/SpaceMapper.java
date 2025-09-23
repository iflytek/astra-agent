package com.iflytek.astra.console.commons.mapper.space;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.iflytek.astra.console.commons.dto.space.EnterpriseSpaceCountVO;
import com.iflytek.astra.console.commons.dto.space.SpaceVO;
import com.iflytek.astra.console.commons.entity.space.Space;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface SpaceMapper extends BaseMapper<Space> {

    List<SpaceVO> recentVisitList(@Param("uid") String uid, @Param("enterpriseId") Long enterpriseId);

    List<SpaceVO> joinList(@Param("uid") String uid, @Param("enterpriseId") Long enterpriseId,
            @Param("name") String name);

    List<SpaceVO> selfList(@Param("uid") String uid, @Param("role") Integer role,
            @Param("enterpriseId") Long enterpriseId, @Param("name") String name);

    List<SpaceVO> corporateList(@Param("uid") String uid, @Param("enterpriseId") Long enterpriseId,
            @Param("name") String name);

    SpaceVO getByUidAndId(@Param("uid") String uid, @Param("spaceId") Long spaceId);

    EnterpriseSpaceCountVO corporateCount(@Param("uid") String uid, @Param("enterpriseId") Long enterpriseId);

}
