package com.iflytek.astron.console.hub.service.wechat.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.iflytek.astron.console.commons.entity.bot.ChatBotBase;
import com.iflytek.astron.console.commons.entity.wechat.BotOffiaccount;
import com.iflytek.astron.console.commons.enums.BotOffiaccountStatusEnum;
import com.iflytek.astron.console.commons.enums.PublishChannelEnum;
import com.iflytek.astron.console.commons.exception.BusinessException;
import com.iflytek.astron.console.commons.mapper.bot.ChatBotBaseMapper;
import com.iflytek.astron.console.commons.mapper.wechat.BotOffiaccountMapper;
import com.iflytek.astron.console.hub.event.PublishChannelUpdateEvent;
import com.iflytek.astron.console.hub.service.wechat.BotOffiaccountService;
import com.iflytek.astron.console.commons.constant.ResponseEnum;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;

/**
 * 智能体与微信公众号bind服务实现
 *
 * @author Omuigix
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class BotOffiaccountServiceImpl implements BotOffiaccountService {

    private final BotOffiaccountMapper botOffiaccountMapper;
    private final ChatBotBaseMapper chatBotBaseMapper;
    private final ApplicationEventPublisher eventPublisher;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void bind(Integer botId, String appid, String uid) {
        log.info("开始绑定智能体与微信公众号: botId={}, appid={}, uid={}", botId, appid, uid);

        // 1. validationbotpermission
        ChatBotBase botBase = chatBotBaseMapper.selectById(botId);
        if (botBase == null) {
            throw new BusinessException(ResponseEnum.BOT_NOT_EXISTS);
        }

        int hasPermission = chatBotBaseMapper.checkBotPermission(botId, uid, botBase.getSpaceId());
        if (hasPermission == 0) {
            throw new BusinessException(ResponseEnum.BOT_NOT_EXISTS);
        }

        // 2. 检查AppID是否已被其他botbind
        BotOffiaccount existingAppidBind = botOffiaccountMapper.selectOne(
                new LambdaQueryWrapper<BotOffiaccount>()
                        .eq(BotOffiaccount::getAppid, appid)
                        .eq(BotOffiaccount::getStatus, BotOffiaccountStatusEnum.BOUND.getStatus()));
        if (existingAppidBind != null && !Objects.equals(existingAppidBind.getBotId(), botId)) {
            // unbind旧的bot
            existingAppidBind.setStatus(BotOffiaccountStatusEnum.UNBOUND.getStatus());
            existingAppidBind.setUpdateTime(LocalDateTime.now());
            botOffiaccountMapper.updateById(existingAppidBind);
            log.info("微信公众号AppID已被其他智能体绑定，解绑旧智能体: appid={}, oldBotId={}",
                    appid, existingAppidBind.getBotId());
        }

        // 3. 处理当前bot的bind记录
        BotOffiaccount currentBotBind = botOffiaccountMapper.selectOne(
                new LambdaQueryWrapper<BotOffiaccount>()
                        .eq(BotOffiaccount::getBotId, botId)
                        .orderByDesc(BotOffiaccount::getUpdateTime)
                        .last("LIMIT 1"));
        if (currentBotBind == null) {
            // create新的bind记录
            BotOffiaccount newBind = BotOffiaccount.builder()
                    .uid(uid)
                    .botId(botId)
                    .appid(appid)
                    .status(BotOffiaccountStatusEnum.BOUND.getStatus())
                    .createTime(LocalDateTime.now())
                    .updateTime(LocalDateTime.now())
                    .build();
            botOffiaccountMapper.insert(newBind);
            log.info("创建新的微信绑定记录: botId={}, appid={}", botId, appid);
        } else {
            // update现有记录
            currentBotBind.setUid(uid);
            currentBotBind.setAppid(appid);
            currentBotBind.setStatus(BotOffiaccountStatusEnum.BOUND.getStatus());
            currentBotBind.setUpdateTime(LocalDateTime.now());
            botOffiaccountMapper.updateById(currentBotBind);
            log.info("更新现有微信绑定记录: botId={}, appid={}", botId, appid);
        }

        // 4. publishchannelupdate事件
        eventPublisher.publishEvent(new PublishChannelUpdateEvent(
                this, botId, uid, botBase.getSpaceId(), PublishChannelEnum.WECHAT, true));

        log.info("智能体与微信公众号绑定成功: botId={}, appid={}", botId, appid);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void unbind(String appid) {
        log.info("开始解绑微信公众号: appid={}", appid);

        BotOffiaccount botOffiaccount = botOffiaccountMapper.selectOne(
                new LambdaQueryWrapper<BotOffiaccount>()
                        .eq(BotOffiaccount::getAppid, appid)
                        .eq(BotOffiaccount::getStatus, BotOffiaccountStatusEnum.BOUND.getStatus()));
        if (botOffiaccount != null) {
            ChatBotBase botBase = chatBotBaseMapper.selectById(botOffiaccount.getBotId());

            botOffiaccount.setStatus(BotOffiaccountStatusEnum.UNBOUND.getStatus());
            botOffiaccount.setUpdateTime(LocalDateTime.now());
            botOffiaccountMapper.updateById(botOffiaccount);

            eventPublisher.publishEvent(new PublishChannelUpdateEvent(
                    this, botOffiaccount.getBotId(), botOffiaccount.getUid(),
                    botBase != null ? botBase.getSpaceId() : null, PublishChannelEnum.WECHAT, false));

            log.info("微信公众号解绑成功: botId={}, appid={}", botOffiaccount.getBotId(), appid);
        } else {
            log.warn("未找到需要解绑的微信公众号记录: appid={}", appid);
        }
    }

    @Override
    public List<BotOffiaccount> getAccountList(String uid) {
        return botOffiaccountMapper.selectList(
                new LambdaQueryWrapper<BotOffiaccount>()
                        .eq(BotOffiaccount::getUid, uid)
                        .eq(BotOffiaccount::getStatus, BotOffiaccountStatusEnum.BOUND.getStatus())
                        .orderByDesc(BotOffiaccount::getUpdateTime));
    }

    @Override
    public BotOffiaccount getByAppid(String appid) {
        return botOffiaccountMapper.selectOne(
                new LambdaQueryWrapper<BotOffiaccount>()
                        .eq(BotOffiaccount::getAppid, appid)
                        .eq(BotOffiaccount::getStatus, BotOffiaccountStatusEnum.BOUND.getStatus()));
    }

    @Override
    public BotOffiaccount getByBotId(Integer botId) {
        return botOffiaccountMapper.selectOne(
                new LambdaQueryWrapper<BotOffiaccount>()
                        .eq(BotOffiaccount::getBotId, botId)
                        .orderByDesc(BotOffiaccount::getUpdateTime)
                        .last("LIMIT 1"));
    }

}
