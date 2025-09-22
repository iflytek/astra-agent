package com.iflytek.astra.console.commons.dto.space;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = false)
@Schema(name = "Query enterprise team users request parameters")
public class EnterpriseUserParam extends PageParam {

    @Schema(description = "Role: 1 super admin, 2 admin, 3 member, 0 all")
    private Integer role;

    @Schema(description = "Nickname")
    private String nickname;
}
