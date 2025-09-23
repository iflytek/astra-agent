package com.iflytek.astron.console.commons.dto.space;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Data
@Schema(name = "User Information")
public class ChatUserVO {

    @Schema(description = "Mobile number")
    private String mobile;

    @Schema(description = "username")
    private String username;

    @Schema(description = "Nickname")
    private String nickname;

    @Schema(description = "User UID")
    private String uid;

    @Schema(description = "Avatar")
    private String avatar;

    @Schema(description = "Join status, 0: Not joined, 1: Joined, 2: Pending confirmation")
    private Integer status;

}
