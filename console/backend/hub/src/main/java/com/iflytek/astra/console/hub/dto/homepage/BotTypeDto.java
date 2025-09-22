package com.iflytek.astra.console.hub.dto.homepage;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * @author yun-zhi-ztl Bot type response DTO
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Schema(name = "BotTypeDto", description = "Bot type response DTO")
public class BotTypeDto {

    @Schema(description = "Bot type code")
    private Integer typeKey;

    @Schema(description = "Bot type name")
    private String typeName;

    @Schema(description = "Bot type icon URL")
    private String icon;

    @Schema(description = "Bot type English name")
    private String typeNameEn;

}
