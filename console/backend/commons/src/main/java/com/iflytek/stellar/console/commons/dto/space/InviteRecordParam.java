package com.iflytek.astra.console.commons.dto.space;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = false)
@Schema(name = "Query invite records request parameters")
public class InviteRecordParam extends PageParam {

    @Schema(description = "Status filter: 0 all / 3 joined / 1 pending / 2 refused")
    private Integer status;

    @Schema(description = "Nickname")
    private String nickname;
}
