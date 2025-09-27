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
@TableName("agent_enterprise_user")
@Schema(name = "AgentEnterpriseUser", description = "Enterprise team user")
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EnterpriseUser implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    private Long id;

    @Schema(description = "Enterprise ID")
    private Long enterpriseId;

    @Schema(description = "User ID")
    private String uid;

    @Schema(description = "User nickname")
    private String nickname;

    @Schema(description = "Role: 1 super admin, 2 admin, 3 member")
    private Integer role;

    @Schema(description = "Creation time")
    private LocalDateTime createTime;

    @Schema(description = "Update time")
    private LocalDateTime updateTime;
}
