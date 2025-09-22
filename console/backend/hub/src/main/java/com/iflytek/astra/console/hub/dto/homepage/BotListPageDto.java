package com.iflytek.astra.console.hub.dto.homepage;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * @author yun-zhi-ztl
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Schema(name = "BotListPageDto", description = "Paginated bot list information DTO")
public class BotListPageDto {

    @Schema(description = "List storing bot information")
    private List<BotInfoDto> pageData;

    @Schema(description = "Total number of records (returned as string)")
    private Integer totalCount;

    @Schema(description = "Number of items per page (returned as string)")
    private Integer pageSize;

    @Schema(description = "Current page number (returned as string)")
    private Integer page;

    @Schema(description = "Total number of pages (returned as string)")
    private Integer totalPages;

}
