package com.iflytek.astra.console.commons.dto.space;

import com.fasterxml.jackson.annotation.JsonFormat;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import org.springframework.format.annotation.DateTimeFormat;

import java.time.LocalDateTime;

/**
 * Space user
 */
@Data
@Schema(name = "Space user")
public class SpaceUserVO {

    //
    private Long id;
    // Space ID
    @Schema(description = "Space ID")
    private Long spaceId;
    // User UID
    @Schema(description = "User UID")
    private String uid;
    @Schema(description = "User nickname")
    private String nickname;
    // Role: 1 owner, 2 admin, 3 member
    @Schema(description = "Role: 1 owner, 2 admin, 3 member")
    private Integer role;
    // Last visit time
    @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private LocalDateTime lastVisitTime;
    // Creation time
    @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private LocalDateTime createTime;
    // Update time
    @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private LocalDateTime updateTime;
}
