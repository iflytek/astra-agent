package com.iflytek.astron.console.hub.dto.share;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * @author yingpeng
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Schema(name = "ShareKey", description = "Share key response")
public class ShareKey {

    @Schema(description = "Shared agent key")
    private String shareAgentKey;
}
