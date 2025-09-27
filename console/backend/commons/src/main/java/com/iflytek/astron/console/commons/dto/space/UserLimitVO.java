package com.iflytek.astron.console.commons.dto.space;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Data
@Schema(name = "User limit")
public class UserLimitVO {

    @Schema(description = "Total")
    private Integer total;

    @Schema(description = "Used")
    private Integer used;

    @Schema(description = "Remaining")
    private Integer remain;
}
