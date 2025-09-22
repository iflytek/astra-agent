package com.iflytek.astra.console.commons.dto.space;

import com.fasterxml.jackson.annotation.JsonFormat;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import org.springframework.format.annotation.DateTimeFormat;

import java.time.LocalDateTime;

@Data
@Schema(name = "Enterprise information")
public class EnterpriseVO {

    @Schema(description = "Enterprise ID")
    private Long id;
    // Creator ID
    @Schema(description = "Creator ID")
    private String uid;
    // Team name
    @Schema(description = "Team name")
    private String name;
    // Avatar URL
    @Schema(description = "Avatar URL")
    private String avatarUrl;
    // logo URL
    @Schema(description = "logoURL")
    private String logoUrl;
    // Organization ID
    @Schema(description = "Organization ID")
    private Long orgId;
    // Package type, 1: team, 2: enterprise
    @Schema(description = "Package type, 1: team, 2: enterprise")
    private Integer serviceType;
    // Creation time
    @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    @Schema(description = "Creation time")
    private LocalDateTime createTime;
    // Expiration time
    @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    @Schema(description = "Expiration time")
    private LocalDateTime expireTime;
    // Update time
    @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    @Schema(description = "Update time")
    private LocalDateTime updateTime;

    // Enterprise super admin name
    @Schema(description = "Enterprise super admin name")
    private String officerName;
    // Current user role: 1 super admin, 2 admin, 3 member
    @Schema(description = "Current user role: 1 super admin, 2 admin, 3 member")
    private Integer role;

}
