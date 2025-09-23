package com.iflytek.astron.console.hub.entity.notification;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("notifications")
@Schema(name = "Notification", description = "Notification Message Table")
public class Notification {

    @TableId(type = IdType.AUTO)
    @Schema(description = "Message ID")
    private Long id;

    @Schema(description = "Message type (personal, broadcast, system, promotion)")
    private String type;

    @Schema(description = "Message title")
    private String title;

    @Schema(description = "Message body")
    private String body;

    @Schema(description = "Template code for client-side special rendering")
    private String templateCode;

    @Schema(description = "Message payload in JSON format for carrying additional business data")
    private String payload;

    @Schema(description = "Creator ID, such as system administrator")
    private String creatorUid;

    @Schema(description = "Creation time")
    private LocalDateTime createdAt;

    @Schema(description = "Expiration time for automatic cleanup tasks")
    private LocalDateTime expireAt;

    @Schema(description = "Metadata in JSON format for storing additional information")
    private String meta;
}
