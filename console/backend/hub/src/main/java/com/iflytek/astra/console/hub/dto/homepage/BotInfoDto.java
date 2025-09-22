package com.iflytek.astra.console.hub.dto.homepage;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * @author yun-zhi-ztl
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Schema(name = "BotInfoDto", description = "Homepage bot display information")
public class BotInfoDto {

    @Schema(description = "Bot ID")
    private Integer botId;

    @Schema(description = "Historical chat ID associated with user UID")
    private Long chatId;

    @Schema(description = "Bot name")
    private String botName;

    @Schema(description = "Bot type")
    private Integer botType;

    @Schema(description = "Bot cover URL")
    private String botCoverUrl;

    @Schema(description = "Bot prompt")
    private String prompt;

    @Schema(description = "Bot description")
    private String botDesc;

    @Schema(description = "Whether favorited")
    private Boolean isFavorite;

    @Schema(description = "Bot creator")
    private String creator;

}
