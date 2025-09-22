package com.iflytek.astra.console.hub.dto.share;

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
@Schema(name = "ShareKey", description = "分享密钥响应")
public class ShareKey {

    @Schema(description = "分享智能体密钥")
    private String shareAgentKey;
}