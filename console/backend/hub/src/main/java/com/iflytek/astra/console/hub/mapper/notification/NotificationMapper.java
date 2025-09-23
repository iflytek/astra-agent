package com.iflytek.astra.console.hub.mapper.notification;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.iflytek.astra.console.hub.entity.notification.Notification;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;
import java.util.List;

@Mapper
public interface NotificationMapper extends BaseMapper<Notification> {

    /**
     * Query message list by type
     */
    List<Notification> selectByType(@Param("type") String type, @Param("offset") int offset, @Param("limit") int limit);

    /**
     * Query broadcast messages within specified time range
     */
    List<Notification> selectBroadcastMessages(@Param("startTime") LocalDateTime startTime,
            @Param("endTime") LocalDateTime endTime,
            @Param("offset") int offset,
            @Param("limit") int limit);

    /**
     * Count broadcast messages created after specified time
     */
    long countBroadcastMessagesAfter(@Param("afterTime") LocalDateTime afterTime);

    /**
     * Clean expired messages
     */
    int deleteExpiredMessages(@Param("expireTime") LocalDateTime expireTime);
}
