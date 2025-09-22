package com.iflytek.astra.console.toolkit.mapper.repo;


import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.iflytek.astra.console.toolkit.entity.table.repo.TagInfoV2;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * <p>
 * Mapper interface
 * </p>
 *
 * @author xxzhang23
 * @since 2023-12-09
 */

@Mapper

public interface TagInfoV2Mapper extends BaseMapper<TagInfoV2> {
    List<TagInfoV2> selectTagListByType(@Param("uid") String uid, @Param("type") Integer type, @Param("list") List<Long> repoIds);
}
