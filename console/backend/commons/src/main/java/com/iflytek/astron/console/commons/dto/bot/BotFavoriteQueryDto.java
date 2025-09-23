package com.iflytek.astron.console.commons.dto.bot;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * Bot favorite query DTO
 */
@Data
@Schema(description = "Bot favorite query parameters")
public class BotFavoriteQueryDto {

    /** User ID */
    @Schema(description = "User ID")
    private String uid;

    /** Page offset */
    @Schema(description = "Page offset")
    private Integer offset;

    /** Page size */
    @Schema(description = "Page size")
    private Integer pageSize;

    public BotFavoriteQueryDto() {}

    public BotFavoriteQueryDto(String uid, Integer offset, Integer pageSize) {
        this.uid = uid;
        this.offset = offset;
        this.pageSize = pageSize;
    }
}
