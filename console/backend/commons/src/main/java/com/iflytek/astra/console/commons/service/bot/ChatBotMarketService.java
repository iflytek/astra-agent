package com.iflytek.astra.console.commons.service.bot;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.iflytek.astra.console.commons.entity.bot.ChatBotMarket;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * @author yun-zhi-ztl
 */
public interface ChatBotMarketService {
    Page<ChatBotMarket> getBotPage(Integer type, String search, Integer pageSize, Integer page);


    @Transactional(propagation = Propagation.REQUIRED)
    void updateBotMarketStatus(String uid, Integer botId);
}
