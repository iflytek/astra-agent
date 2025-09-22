package com.iflytek.astra.console.commons.entity.user;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("user_info")
@Schema(name = "UserInfo", description = "User information table")
public class UserInfo {

    @TableId(type = IdType.AUTO)
    @Schema(description = "Non-business primary key")
    private Long id;

    @Schema(description = "UID")
    private String uid;

    @Schema(description = "Username")
    private String username;

    @Schema(description = "Avatar")
    private String avatar;

    @Schema(description = "Nickname")
    private String nickname;

    @Schema(description = "Mobile number")
    private String mobile;

    @Schema(description = "Account status: 0 inactive, 1 active, 2 frozen")
    private Integer accountStatus;

    @Schema(description = "User agreement consent: 0 not agreed, 1 agreed")
    private Integer userAgreement;

    @Schema(description = "Creation time")
    private LocalDateTime createTime;

    @Schema(description = "Update time")
    private LocalDateTime updateTime;

    @TableLogic(value = "0", delval = "1")
    @Schema(description = "Logical delete flag: 0 not deleted, 1 deleted")
    private Integer deleted;
}
