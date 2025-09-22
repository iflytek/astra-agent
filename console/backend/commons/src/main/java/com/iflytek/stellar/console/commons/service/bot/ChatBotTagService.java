package com.iflytek.astra.console.commons.service.bot;

import com.baomidou.mybatisplus.extension.service.IService;
import com.iflytek.astra.console.commons.entity.bot.ChatBotTag;

import java.util.List;

public interface ChatBotTagService extends IService<ChatBotTag> {

    /**
     * 传入 botid 返回 botId 对应的数组
     *
     * @param botId
     * @return
     */
    List<String> getBotTagList(Long botId);

    /**
     * 助手送审的时候就会展示，跟新最新的标签
     */
    void updateTags(Long botId);

}
