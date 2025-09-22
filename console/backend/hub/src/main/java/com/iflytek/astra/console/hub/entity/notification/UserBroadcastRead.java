package com.iflytek.astra.console.hub.entity.notification;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("user_broadcast_read")
@Schema(name = "UserBroadcastRead", description = "User Broadcast Message Read Status Table")
public class UserBroadcastRead {
    @TableId(type = IdType.AUTO)
    @Schema(description = "ID")
    private Long id;

    @Schema(description = "User ID")
    private String receiverUid;

    @Schema(description = "Associated broadcast notification ID")
    private Long notificationId;

    @Schema(description = "Read time")
    private LocalDateTime readAt;
}
