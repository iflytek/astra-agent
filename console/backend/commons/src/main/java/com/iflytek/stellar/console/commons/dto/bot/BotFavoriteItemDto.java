package com.iflytek.astra.console.commons.dto.bot;

import com.iflytek.astra.console.commons.entity.bot.ChatBotMarketPage;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * Bot favorites list item DTO
 */
@Data
@Schema(description = "Bot favorites list item")
public class BotFavoriteItemDto {

    /** Add status (0: not added, 1: added) */
    @Schema(description = "Add status (0: not added, 1: added)")
    private Integer addStatus;

    /** Creator name */
    @Schema(description = "Creator name")
    private String creator;

    /** Chat ID (optional, exists only when added) */
    @Schema(description = "Chat ID (optional, exists only when added)")
    private Long chatId;

    /** Enable status (0: disabled, 1: enabled) */
    @Schema(description = "Enable status (0: disabled, 1: enabled)")
    private Integer enableStatus;

    /** Bot information */
    @Schema(description = "Bot information")
    private ChatBotMarketPage bot;

    public BotFavoriteItemDto() {}

    public BotFavoriteItemDto(Integer addStatus, String creator, ChatBotMarketPage bot) {
        this.addStatus = addStatus;
        this.creator = creator;
        this.bot = bot;
    }
}
