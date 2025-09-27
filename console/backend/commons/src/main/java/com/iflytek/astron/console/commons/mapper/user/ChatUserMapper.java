package com.iflytek.astron.console.commons.mapper.user;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.iflytek.astron.console.commons.entity.user.ChatUser;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.Map;

public interface ChatUserMapper extends BaseMapper<ChatUser> {

    void updateIsAbleByUid(@Param(value = "uid") Long uid,
            @Param(value = "isAble") Integer isAble);

    List<ChatUser> selectUserByUidSet(Map<String, Object> param);
}
