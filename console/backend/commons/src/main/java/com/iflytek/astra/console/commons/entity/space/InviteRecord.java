package com.iflytek.astra.console.commons.entity.space;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDateTime;
import lombok.Data;

@Data
@TableName("agent_invite_record")
@Schema(name = "AgentInviteRecord", description = "Invitation record")
public class InviteRecord {

    @TableId(type = IdType.AUTO)
    private Long id;

    @Schema(description = "Invitation type: 1 space, 2 team")
    private Integer type;

    @Schema(description = "Space ID")
    private Long spaceId;

    @Schema(description = "Enterprise ID")
    private Long enterpriseId;

    @Schema(description = "Invitee UID")
    private String inviteeUid;

    @Schema(description = "Join role: 1 admin, 2 member")
    private Integer role;

    @Schema(description = "Invitee nickname")
    private String inviteeNickname;

    @Schema(description = "Inviter UID")
    private String inviterUid;

    @Schema(description = "Expiration time")
    private LocalDateTime expireTime;

    @Schema(description = "Status: 1 initial, 2 refused, 3 joined, 4 withdrawn, 5 expired")
    private Integer status;

    @Schema(description = "Creation time")
    private LocalDateTime createTime;

    @Schema(description = "Update time")
    private LocalDateTime updateTime;
}
