package com.iflytek.astron.console.hub.dto.publish;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * WeChat Authorization URL Response DTO
 *
 * Corresponds to the return result of original interface: getAuthUrl
 *
 * @author Omuigix
 */
@Data
@Schema(name = "WechatAuthUrlResponseDto", description = "WeChat authorization URL response")
public class WechatAuthUrlResponseDto {

    @Schema(description = "WeChat authorization URL", example = "https://mp.weixin.qq.com/cgi-bin/componentloginpage?component_appid=xxx&pre_auth_code=xxx&redirect_uri=xxx&auth_type=1&biz_appid=xxx")
    private String authUrl;

    @Schema(description = "Pre-authorization code", example = "preauthcode@@@1234567890")
    private String preAuthCode;

    @Schema(description = "Authorization URL expiration time (seconds)", example = "1800")
    private Integer expiresIn;

    public static WechatAuthUrlResponseDto of(String authUrl) {
        WechatAuthUrlResponseDto response = new WechatAuthUrlResponseDto();
        response.setAuthUrl(authUrl);
        response.setExpiresIn(1800);
        return response;
    }
}
