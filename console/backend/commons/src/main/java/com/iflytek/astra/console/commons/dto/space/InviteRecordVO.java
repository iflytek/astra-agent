package com.iflytek.astra.console.commons.dto.space;

import com.fasterxml.jackson.annotation.JsonFormat;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import org.springframework.format.annotation.DateTimeFormat;

import java.time.LocalDateTime;

/**
 * Invitation record
 */
@Data
@Schema(name = "Invitation record")
public class InviteRecordVO {

    //
    @Schema(description = "Invitation record ID")
    private Long id;
    // Invitation type: 1 space, 2 team
    @Schema(description = "Invitation type: 1 space, 2 team")
    private Integer type;
    // Space ID
    @Schema(description = "Space ID")
    private Long spaceId;
    // Enterprise ID
    @Schema(description = "Enterprise ID")
    private Long enterpriseId;
    // Invitee UID
    @Schema(description = "Invitee UID")
    private String inviteeUid;
    // Join role: 2 admin, 3 member
    @Schema(description = "Join role: 2 admin, 3 member")
    private Integer role;
    // Invitee nickname
    @Schema(description = "Invitee nickname")
    private String inviteeNickname;
    // Inviter UID
    @Schema(description = "Inviter UID")
    private String inviterUid;
    // Expiration time
    @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    @Schema(description = "Expiration time")
    private LocalDateTime expireTime;
    // Status: 1 initial, 2 refused, 3 joined, 4 withdrawn, 5 expired
    @Schema(description = "Status: 1 initial, 2 refused, 3 joined, 4 withdrawn, 5 expired")
    private Integer status;
    // Creation time
    @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private LocalDateTime createTime;
    // Update time
    @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private LocalDateTime updateTime;

    @Schema(description = "Inviter name")
    private String inviterName;

    @Schema(description = "Inviter avatar")
    private String inviterAvatar;

    @Schema(description = "Invitee avatar")
    private String inviteeAvatar;

    @Schema(description = "Owner name")
    private String ownerName;

    @Schema(description = "Owner avatar")
    private String ownerAvatar;

    @Schema(description = "Space name")
    private String spaceName;

    @Schema(description = "Space description")
    private String spaceDescription;

    @Schema(description = "Space avatar")
    private String spaceAvatar;

    @Schema(description = "Enterprise name")
    private String enterpriseName;

    @Schema(description = "Enterprise avatar")
    private String enterpriseAvatar;

    @Schema(description = "Whether the user is in the space/team")
    private Boolean isBelong;

}
