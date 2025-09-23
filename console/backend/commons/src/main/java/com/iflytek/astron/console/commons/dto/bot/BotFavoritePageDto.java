package com.iflytek.astron.console.commons.dto.bot;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.List;

/**
 * Bot favorites pagination result DTO
 */
@Data
@Schema(description = "Bot favorites pagination result")
public class BotFavoritePageDto {

    /** Total count */
    @Schema(description = "Total count")
    private Long total;

    /** Paginated list */
    @Schema(description = "Paginated list")
    private List<BotFavoriteItemDto> pageList;

    public BotFavoritePageDto() {}

    public BotFavoritePageDto(Long total, List<BotFavoriteItemDto> pageList) {
        this.total = total;
        this.pageList = pageList;
    }
}
