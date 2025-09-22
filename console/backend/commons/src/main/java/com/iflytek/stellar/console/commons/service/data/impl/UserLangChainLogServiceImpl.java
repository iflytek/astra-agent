package com.iflytek.astra.console.commons.service.data.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.iflytek.astra.console.commons.entity.bot.UserLangChainLog;
import com.iflytek.astra.console.commons.mapper.UserLangChainLogMapper;
import com.iflytek.astra.console.commons.service.data.UserLangChainLogService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@Service
public class UserLangChainLogServiceImpl implements UserLangChainLogService {

    @Autowired
    private UserLangChainLogMapper userLangChainLogMapper;

    public final static int LOG_MAX_SIZE = 20;

    @Override
    public UserLangChainLog insertUserLangChainLog(UserLangChainLog userLangChainLog) {
        Long botId = userLangChainLog.getBotId();
        // 先查询记录数是否超过20条,超过20条开始滚动替换
        List<UserLangChainLog> result = userLangChainLogMapper.selectList(
                        new LambdaQueryWrapper<UserLangChainLog>()
                                        .eq(UserLangChainLog::getBotId, botId)
                                        .orderByAsc(UserLangChainLog::getUpdateTime));
        // 如果历史版本超过20条记录，则进行滚动更新
        if (result != null && result.size() >= LOG_MAX_SIZE) {
            LocalDateTime updateTime = result.getFirst().getUpdateTime();
            updateOldRecord(userLangChainLog, updateTime);
        } else {
            userLangChainLog.setId(null);
            userLangChainLogMapper.insert(userLangChainLog);
        }
        return userLangChainLog;
    }

    private void updateOldRecord(UserLangChainLog userLangChainLog, LocalDateTime updateTime) {
        UpdateWrapper<UserLangChainLog> updateWrapper = new UpdateWrapper<>();
        updateWrapper.eq("update_time", updateTime);

        try {
            userLangChainLogMapper.update(userLangChainLog, updateWrapper);
        } catch (Exception e) {
            log.error("更新助手2.0结构到MySQL异常", e);
        }
    }
}
