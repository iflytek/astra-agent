package com.iflytek.astron.console.commons.entity.space;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import io.swagger.v3.oas.annotations.media.Schema;

import java.io.Serializable;
import java.time.LocalDateTime;
import lombok.Data;

@Data
@TableName("agent_space_user")
@Schema(name = "AgentSpaceUser", description = "Space user")
public class SpaceUser implements Serializable {

    @TableId(type = IdType.AUTO)
    private Long id;

    @Schema(description = "Space ID")
    private Long spaceId;

    @Schema(description = "User ID")
    private String uid;

    @Schema(description = "User nickname")
    private String nickname;

    @Schema(description = "Role: 1 owner, 2 admin, 3 member")
    private Integer role;

    @Schema(description = "Last visit time")
    private LocalDateTime lastVisitTime;

    @Schema(description = "Creation time")
    private LocalDateTime createTime;

    @Schema(description = "Update time")
    private LocalDateTime updateTime;
}
