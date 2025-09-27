package com.iflytek.astron.console.commons.mapper.chat;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.iflytek.astron.console.commons.entity.chat.ChatBotListDto;
import com.iflytek.astron.console.commons.entity.chat.ChatList;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface ChatListMapper extends BaseMapper<ChatList> {

    @Select("""
            SELECT cl.title,
                   cl.bot_id             as botId,
                   cl.id,
                   cl.`enable`,
                   cl.`sticky`,
                   cl.create_time,
                   cl.update_time,
                   cl.enabled_plugin_ids as enabledPluginIds,
                   cbl.bot_desc          as botDesc,
                   cbl.bot_desc_en       as botDescEn,
                   cbl.bot_name          as botTitle,
                   cbl.bot_name_en       as botTitleEn,
                   cbl.`bot_type`        as botType,
                   cbl.uid,
                   cbl.support_context   as supportContext,
                   cbl.avatar            as botAvatar,
                   cbl.version           as version,
                   cbm.bot_status        as botStatus,
                   cbm.uid               as marketBotUid,
                   cbm.hot_num           as hotNum,
                   cbl.client_hide       as clientHide,
                   cbl.virtual_agent_id  as virtualAgentId
            FROM chat_list cl
                     LEFT JOIN chat_bot_base cbl
                               ON cl.bot_id = cbl.id
                     LEFT JOIN chat_bot_market cbm on cl.bot_id = cbm.bot_id
            WHERE cl.uid = #{uid}
              and cl.is_delete = 0
              and cl.is_botweb = 0
              AND cl.bot_id > 0
              and cl.root_flag = 1
            ORDER BY cl.update_time desc
            """)
    List<ChatBotListDto> getBotChatList(@Param("uid") String uid);
}
