package com.iflytek.astra.console.commons.mapper.chat;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.iflytek.astra.console.commons.entity.chat.ChatTreeIndex;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface ChatTreeIndexMapper extends BaseMapper<ChatTreeIndex> {

    /**
     * Get all related chat tree indices by child chat ID
     *
     * @param childChatId Child chat ID
     * @param uid User ID
     * @return Chat tree index list
     */
    @Select("""
            select root_chat_id,
                   parent_chat_id,
                   child_chat_id,
                   uid
            from chat_tree_index
            where root_chat_id = (select root_chat_id
                                  from chat_tree_index cti
                                  where child_chat_id = #{childChatId}
                                    and uid = #{uid})
            """)
    List<ChatTreeIndex> getAllListByChildChatId(@Param("childChatId") Long childChatId, @Param("uid") String uid);
}
