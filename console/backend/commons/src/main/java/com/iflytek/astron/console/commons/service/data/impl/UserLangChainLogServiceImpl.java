package com.iflytek.astron.console.commons.service.data.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.iflytek.astron.console.commons.entity.bot.UserLangChainLog;
import com.iflytek.astron.console.commons.mapper.UserLangChainLogMapper;
import com.iflytek.astron.console.commons.service.data.UserLangChainLogService;
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
        // First check if record count exceeds 20, start rolling replacement if over 20
        List<UserLangChainLog> result = userLangChainLogMapper.selectList(
                new LambdaQueryWrapper<UserLangChainLog>()
                        .eq(UserLangChainLog::getBotId, botId)
                        .orderByAsc(UserLangChainLog::getUpdateTime));
        // If historical versions exceed 20 records, perform rolling update
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
            log.error("Exception updating assistant 2.0 structure to MySQL", e);
        }
    }
}
