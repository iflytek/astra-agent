package com.iflytek.astron.console.commons.mapper.space;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.iflytek.astron.console.commons.dto.space.SpaceUserVO;
import com.iflytek.astron.console.commons.entity.space.SpaceUser;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface SpaceUserMapper extends BaseMapper<SpaceUser> {

    Long countPersonalSpaceUser(@Param("uid") String uid, @Param("role") Integer role, @Param("type") Integer type);

    SpaceUser getByUidAndSpaceId(@Param("uid") String uid, @Param("spaceId") Long spaceId);

    Page<SpaceUserVO> selectVOPageByParam(Page<SpaceUser> page,
            @Param("spaceId") Long spaceId,
            @Param("nickname") String nickname,
            @Param("role") Integer role);
}
