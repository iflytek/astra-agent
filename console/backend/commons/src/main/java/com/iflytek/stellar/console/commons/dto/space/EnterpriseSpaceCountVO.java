package com.iflytek.astra.console.commons.dto.space;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Data
@Schema(name = "Enterprise space count")
public class EnterpriseSpaceCountVO {

    @Schema(description = "Total")
    private Long total;

    @Schema(description = "Joined")
    private Long joined;
}
