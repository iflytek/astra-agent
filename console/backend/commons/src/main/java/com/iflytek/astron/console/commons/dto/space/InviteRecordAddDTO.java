package com.iflytek.astron.console.commons.dto.space;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import jakarta.validation.constraints.NotNull;

@Data
@Schema(name = "Invite user request parameters")
public class InviteRecordAddDTO {

    @Schema(description = "User UID")
    @NotNull(message = "User UID cannot be null")
    private String uid;

    @Schema(description = "Join role: 2 admin, 3 member")
    private Integer role;

}
