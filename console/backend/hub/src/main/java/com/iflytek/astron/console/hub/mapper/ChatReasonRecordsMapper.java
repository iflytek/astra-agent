package com.iflytek.astron.console.hub.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.iflytek.astron.console.commons.entity.chat.ChatReasonRecords;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface ChatReasonRecordsMapper extends BaseMapper<ChatReasonRecords> {

    /**
     * Query inference records by request ID
     */
    List<ChatReasonRecords> selectByReqId(@Param("reqId") Long reqId);

    /**
     * Query inference records by chat ID
     */
    List<ChatReasonRecords> selectByChatId(@Param("chatId") Long chatId);

    /**
     * Query inference records by user ID and chat ID
     */
    List<ChatReasonRecords> selectByUidAndChatId(@Param("uid") String uid, @Param("chatId") Long chatId);

    /**
     * Query inference records by inference type
     */
    List<ChatReasonRecords> selectByType(@Param("type") String type);

    /**
     * Delete inference records before specified days
     */
    int deleteByCreateTimeBefore(@Param("days") int days);
}
