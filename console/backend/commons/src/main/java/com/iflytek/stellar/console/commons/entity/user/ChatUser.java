package com.iflytek.astra.console.commons.entity.user;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDateTime;
import lombok.Data;

@Data
@TableName("chat_user")
@Schema(name = "ChatUser", description = "GPT user authorization information table")
public class ChatUser {

    @TableId(type = IdType.AUTO)
    @Schema(description = "Non-business primary key")
    private Long id;

    @Schema(description = "If user is not logged in or not registered, this is empty")
    private String uid;

    @Schema(description = "User name")
    private String name;

    @Schema(description = "Avatar")
    private String avatar;

    @Schema(description = "User nickname")
    private String nickname;

    @Schema(description = "Phone number, no validation for authenticity, only checks for duplicates")
    private String mobile;

    @Schema(description = "Activation status: 0 Activated, 1 Not activated, 2 Frozen")
    private Integer isAble;

    @Schema(description = "Create time")
    private LocalDateTime createTime;

    @Schema(description = "Update time")
    private LocalDateTime updateTime;

    @Schema(description = "User agreement consent: 0 Not agreed, 1 Agreed")
    private Integer userAgreement;

    @Schema(description = "cmp_core.BigdataServicesMonitorDaily")
    private Integer dateStamp;
}
