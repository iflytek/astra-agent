package com.iflytek.astra.console.hub.dto.user;

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
@Schema(name = "MyBotPageDTO", description = "My bot paginated list")
public class MyBotPageDTO {
    @Schema(description = "List storing bot information")
    private List<MyBotResponseDTO> pageData;

    @Schema(description = "Total number of records (returned as string)")
    private Integer totalCount;

    @Schema(description = "Number of items per page (returned as string)")
    private Integer pageSize;

    @Schema(description = "Current page number (returned as string)")
    private Integer page;

    @Schema(description = "Total number of pages (returned as string)")
    private Integer totalPages;
}
