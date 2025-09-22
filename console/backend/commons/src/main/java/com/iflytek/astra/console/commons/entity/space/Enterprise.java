package com.iflytek.astra.console.commons.entity.space;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDateTime;
import lombok.Data;

@Data
@TableName("agent_enterprise")
@Schema(name = "AgentEnterprise", description = "Enterprise team")
public class Enterprise {

    @TableId(type = IdType.AUTO)
    private Long id;

    @Schema(description = "Creator ID")
    private String uid;

    @Schema(description = "Team name")
    private String name;

    @Schema(description = "logoURL")
    private String logoUrl;

    @Schema(description = "Avatar URL")
    private String avatarUrl;

    @Schema(description = "Organization ID")
    private Long orgId;

    @Schema(description = "Package type: 1 team, 2 enterprise")
    private Integer serviceType;

    @Schema(description = "Creation time")
    private LocalDateTime createTime;

    @Schema(description = "Expiration time")
    private LocalDateTime expireTime;

    @Schema(description = "Update time")
    private LocalDateTime updateTime;

    @Schema(description = "Deleted: 0 no, 1 yes")
    private Integer deleted;
}
