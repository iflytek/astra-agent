package com.iflytek.astron.console.toolkit.mapper.users;


import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.iflytek.astron.console.toolkit.entity.table.users.SystemUser;
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


public interface SystemUserMapper extends BaseMapper<SystemUser> {
    List<SystemUser> getSystemUserByLoginNameOrNickName(@Param("username") String username);
}
