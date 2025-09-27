package com.iflytek.astron.console.hub.entity.notification;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("user_notifications")
@Schema(name = "UserNotification", description = "User Personal Message Association Table")
public class UserNotification {

    @TableId(type = IdType.AUTO)
    @Schema(description = "Auto-increment ID")
    private Long id;

    @Schema(description = "Associated notification ID")
    private Long notificationId;

    @Schema(description = "Receiver user ID")
    private String receiverUid;

    @Schema(description = "Is read (false=unread, true=read)")
    private Boolean isRead;

    @Schema(description = "Read time")
    private LocalDateTime readAt;

    @Schema(description = "Receive time")
    private LocalDateTime receivedAt;

    @Schema(description = "Extra data in JSON format for storing user-specific additional information")
    private String extra;
}
