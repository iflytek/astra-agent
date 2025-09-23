package com.iflytek.astron.console.commons.entity.space;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import io.swagger.v3.oas.annotations.media.Schema;

import java.io.Serializable;
import java.time.LocalDateTime;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@TableName("agent_enterprise_permission")
@Schema(name = "AgentEnterprisePermission", description = "Enterprise team role permission configuration")
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EnterprisePermission implements Serializable {

    @TableId(type = IdType.AUTO)
    private Long id;

    @Schema(description = "Permission module")
    private String module;

    @Schema(description = "Description")
    private String description;

    @Schema(description = "Permission unique key")
    private String permissionKey;

    @Schema(description = "Super admin has permission: 1 yes, 0 no")
    private Boolean officer;

    @Schema(description = "Admin has permission: 1 yes, 0 no")
    private Boolean governor;

    @Schema(description = "Member has permission: 1 yes, 0 no")
    private Boolean staff;

    @Schema(description = "Available when expired: 1 yes, 0 no")
    private Boolean availableExpired;

    @Schema(description = "Creation time")
    private LocalDateTime createTime;

    @Schema(description = "Update time")
    private LocalDateTime updateTime;
}
