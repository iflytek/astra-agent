package com.iflytek.astra.console.toolkit.mapper.group;


import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.iflytek.astra.console.toolkit.entity.table.group.GroupTag;
import com.iflytek.astra.console.toolkit.entity.vo.group.GroupTagVO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper

public interface GroupTagMapper extends BaseMapper<GroupTag> {
    List<GroupTagVO> listGroupTagVOByUid(@Param("uid") String uid);
}
