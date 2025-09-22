package com.iflytek.astra.console.commons.dto.space;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = false)
@Schema(name = "Query application records request parameters")
public class ApplyRecordParam extends PageParam {

    @Schema(description = "Application status: 1 pending, 2 approved, 3 rejected, 0 all")
    private Integer status;

    @Schema(description = "Nickname")
    private String nickname;
}
