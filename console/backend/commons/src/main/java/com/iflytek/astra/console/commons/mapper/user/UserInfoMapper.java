package com.iflytek.astra.console.commons.mapper.user;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.iflytek.astra.console.commons.entity.user.UserInfo;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface UserInfoMapper extends BaseMapper<UserInfo> {
}
