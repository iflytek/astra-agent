package com.iflytek.astra.console.hub.mapper.notification;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.iflytek.astra.console.hub.dto.notification.NotificationDto;
import com.iflytek.astra.console.hub.entity.notification.UserNotification;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface UserNotificationMapper extends BaseMapper<UserNotification> {

    /**
     * Query user notification details using JOIN (solve N+1 problem)
     */
    List<NotificationDto> selectUserNotificationsWithDetails(@Param("receiverUid") String receiverUid,
                    @Param("offset") int offset,
                    @Param("limit") int limit);

    /**
     * Query user unread notification details using JOIN (solve N+1 problem)
     */
    List<NotificationDto> selectUserUnreadNotificationsWithDetails(@Param("receiverUid") String receiverUid,
                    @Param("offset") int offset,
                    @Param("limit") int limit);

    /**
     * Query user's unread message list
     */
    List<UserNotification> selectUnreadByUid(@Param("receiverUid") String receiverUid,
                    @Param("offset") int offset,
                    @Param("limit") int limit);

    /**
     * Query user's all message list
     */
    List<UserNotification> selectByUid(@Param("receiverUid") String receiverUid,
                    @Param("offset") int offset,
                    @Param("limit") int limit);

    /**
     * Count user's unread message count
     */
    int countUnreadByUid(@Param("receiverUid") String receiverUid);

    /**
     * Batch mark messages as read
     */
    int batchMarkAsRead(@Param("receiverUid") String receiverUid,
                    @Param("notificationIds") List<Long> notificationIds);

    /**
     * Mark all messages as read
     */
    int markAllAsRead(@Param("receiverUid") String receiverUid);

    /**
     * Batch insert user messages
     */
    int batchInsert(@Param("userNotifications") List<UserNotification> userNotifications);
}
