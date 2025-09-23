package com.iflytek.astron.console.commons.dto.space;

import com.fasterxml.jackson.annotation.JsonFormat;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import org.springframework.format.annotation.DateTimeFormat;

import java.time.LocalDateTime;

@Data
@Schema(name = "Space information")
public class SpaceVO {

    //
    @Schema(description = "Space ID")
    private Long id;
    // Space name
    @Schema(description = "Space name")
    private String name;
    // Description
    @Schema(description = "Space description")
    private String description;
    // Avatar URL
    @Schema(description = "Avatar URL")
    private String avatarUrl;
    // Creator ID
    @Schema(description = "Creator ID")
    private String uid;
    // Enterprise ID
    @Schema(description = "Enterprise ID")
    private Long enterpriseId;
    // Creation time
    @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    @Schema(description = "Creation time")
    private LocalDateTime createTime;
    // Update time
    @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    @Schema(description = "Update time")
    private LocalDateTime updateTime;

    // Owner name
    @Schema(description = "Owner name")
    private String ownerName;

    // Member count
    @Schema(description = "Member count")
    private Integer memberCount;

    // Current user role
    @Schema(description = "Current user role: 1 owner, 2 admin, 3 member")
    private Integer userRole;

    @Schema(description = "Join status: 1 joined, 2 not joined, 3 applying")
    private Integer applyStatus;

    // Last visit time
    @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private LocalDateTime lastVisitTime;
}
