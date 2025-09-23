package com.iflytek.astron.console.commons.dto.space;

import com.fasterxml.jackson.annotation.JsonFormat;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import org.springframework.format.annotation.DateTimeFormat;

import java.time.LocalDateTime;

/**
 * Enterprise team user
 */
@Data
@Schema(name = "Enterprise team user")
public class EnterpriseUserVO {

    //
    @Schema(description = "ID")
    private Long id;
    // Enterprise ID
    @Schema(description = "Enterprise ID")
    private Long enterpriseId;
    // User ID
    @Schema(description = "User ID")
    private String uid;
    // User nickname
    @Schema(description = "User nickname")
    private String nickname;
    // Role: 1 super admin, 2 admin, 3 member
    @Schema(description = "Role: 1 super admin, 2 admin, 3 member")
    private Integer role;
    // Creation time
    @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private LocalDateTime createTime;
    // Update time
    @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private LocalDateTime updateTime;
}
