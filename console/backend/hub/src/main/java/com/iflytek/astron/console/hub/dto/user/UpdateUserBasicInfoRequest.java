package com.iflytek.astron.console.hub.dto.user;

import jakarta.validation.constraints.Size;

/**
 * Update user basic information request DTO
 */
public record UpdateUserBasicInfoRequest(
    @Size(max = 50, message = "{user.nickname.max.length}")
    String nickname,

    @Size(max = 500, message = "{user.avatar.max.length}")
    String avatar
){
}
