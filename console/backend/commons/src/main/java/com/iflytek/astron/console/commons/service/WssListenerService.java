package com.iflytek.astron.console.commons.service;

import lombok.Getter;
import org.redisson.api.RedissonClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
@Getter
public class WssListenerService {

    @Autowired
    private ChatRecordModelService chatRecordModelService;

    @Autowired
    private RedissonClient redissonClient;

    public ChatRecordModelService getChatRecordModelService() {
        return chatRecordModelService;
    }

    public RedissonClient getRedissonClient() {
        return redissonClient;
    }

}
