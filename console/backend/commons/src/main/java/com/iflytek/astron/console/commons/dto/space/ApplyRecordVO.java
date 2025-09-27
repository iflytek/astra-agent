package com.iflytek.astron.console.commons.dto.space;

import com.fasterxml.jackson.annotation.JsonFormat;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import org.springframework.format.annotation.DateTimeFormat;

import java.time.LocalDateTime;

/**
 * Application record for joining space/enterprise
 */
@Data
@Schema(name = "Application record for joining space/enterprise")
public class ApplyRecordVO {

    //
    @Schema(description = "Application ID")
    private Long id;
    // Enterprise team ID
    @Schema(description = "Enterprise team ID")
    private Long enterpriseId;
    // Space ID
    @Schema(description = "Space ID")
    private Long spaceId;
    // Applicant UID
    @Schema(description = "Applicant UID")
    private String applyUid;
    // Applicant nickname
    @Schema(description = "Applicant nickname")
    private String applyNickname;
    // Application time
    @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    @Schema(description = "Application time")
    private LocalDateTime applyTime;
    // Application status: 1 pending, 2 approved, 3 rejected
    @Schema(description = "Application status: 1 pending, 2 approved, 3 rejected")
    private Integer status;
    // Review time
    @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    @Schema(description = "Review time")
    private LocalDateTime auditTime;
    // Reviewer UID
    @Schema(description = "Reviewer UID")
    private String auditUid;
    // Creation time
    @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private LocalDateTime createTime;
    // Update time
    @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private LocalDateTime updateTime;
}
