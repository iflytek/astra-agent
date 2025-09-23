package com.iflytek.astron.console.hub.util;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.iflytek.astron.console.commons.constant.ResponseEnum;
import com.iflytek.astron.console.commons.exception.BusinessException;
import com.iflytek.astron.console.commons.mapper.bot.ChatBotBaseMapper;
import com.iflytek.astron.console.commons.util.RequestContextUtil;
import com.iflytek.astron.console.commons.util.space.SpaceInfoUtil;
import com.iflytek.astron.console.commons.entity.bot.ChatBotBase;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;


/**
 * @author yun-zhi-ztl
 * @description Check bot ownership by uid or spaceId
 */
@Slf4j
@Component
public class BotPermissionUtil {

    @Autowired
    private ChatBotBaseMapper chatBotBaseMapper;

    public void checkBot(Integer botId) {
        Long spaceId = SpaceInfoUtil.getSpaceId();
        String uid = RequestContextUtil.getUID();

        LambdaQueryWrapper<ChatBotBase> query = new LambdaQueryWrapper<>();
        query.eq(ChatBotBase::getId, botId);


        if (spaceId == null) {
            // spaceId is null, belongs to individual
            query.eq(ChatBotBase::getUid, uid).isNull(ChatBotBase::getSpaceId);
        } else {
            // spaceId is not null, belongs to space
            query.eq(ChatBotBase::getSpaceId, spaceId);
        }

        if (!chatBotBaseMapper.exists(query)) {
            throw new BusinessException(spaceId == null ? ResponseEnum.PERMISSION_BOT_NOT_BELONG_USER : ResponseEnum.PERMISSION_BOT_NOT_BELONG_SPACE);
        }
    }

}
