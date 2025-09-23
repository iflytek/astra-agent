package com.iflytek.astron.console.toolkit.mapper.group;


import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.iflytek.astron.console.toolkit.entity.table.group.GroupUser;
import com.iflytek.astron.console.toolkit.entity.vo.group.GroupUserTagVO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * <p>
 * Mapper interface
 * </p>
 *
 * @author xxzhang23
 * @since 2024-01-08
 */

@Mapper

public interface GroupUserMapper extends BaseMapper<GroupUser> {
    List<GroupUserTagVO> listUserByTagId(@Param("uid") String uid, @Param("tagId") Long tagId, @Param("content") String content);

    void deleteByTagIdAndUidList(@Param("uid") String uid, @Param("tagId") Long tagId, @Param("uids") List<String> uids);

    void deleteByUidList(@Param("uid") String uid, @Param("uids") List<String> uids);

    void deleteExcludeTagIds(@Param("uid") String uid, @Param("userId") String userId, @Param("tagIds") List<Long> tagIds);
}
