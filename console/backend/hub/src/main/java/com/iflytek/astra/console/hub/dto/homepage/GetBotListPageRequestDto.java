package com.iflytek.astra.console.hub.dto.homepage;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * @author yun-zhi-ztl
 */
@Data
@Schema(name = "GetBotListPageRequestDto", description = "Get bot list request DTO")
public class GetBotListPageRequestDto {

    @Schema(description = "Search keyword")
    private String searchValue = "";

    @Schema(description = "Bot type")
    private Integer botType;

    @Schema(description = "Current page number")
    private int pageIndex = 1;

    @Schema(description = "Number of data rows")
    private int pageSize = 15;
}
