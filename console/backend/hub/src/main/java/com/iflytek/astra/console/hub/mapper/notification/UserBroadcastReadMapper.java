package com.iflytek.astra.console.hub.mapper.notification;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.iflytek.astra.console.hub.entity.notification.UserBroadcastRead;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface UserBroadcastReadMapper extends BaseMapper<UserBroadcastRead> {

    /**
     * Query user's read broadcast message ID list
     */
    List<Long> selectReadBroadcastIds(@Param("receiverUid") String receiverUid,
                    @Param("notificationIds") List<Long> notificationIds);

    /**
     * Batch insert read records
     */
    int batchInsert(@Param("readRecords") List<UserBroadcastRead> readRecords);

    /**
     * Check if user has read specified broadcast message
     */
    boolean checkIfRead(@Param("receiverUid") String receiverUid,
                    @Param("notificationId") Long notificationId);

    /**
     * Count total broadcast messages read by user
     */
    long countUserReadBroadcastMessages(@Param("receiverUid") String receiverUid);
}
