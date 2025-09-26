package com.iflytek.astron.console.hub.dto.publish;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/**
 * WeChat Authorization URL Request DTO
 *
 * Corresponds to original interface: getAuthUrl
 *
 * @author Omuigix
 */
@Data
@Schema(name = "WechatAuthUrlRequestDto", description = "WeChat authorization URL request")
public class WechatAuthUrlRequestDto {

    @NotNull(message = "Bot ID cannot be null")
    @Schema(description = "Bot ID", required = true, example = "4011451")
    private Integer botId;

    @NotBlank(message = "WeChat official account AppID cannot be empty")
    @Schema(description = "WeChat official account AppID", required = true, example = "wx[16 characters]")
    private String appid;

    @NotBlank(message = "Callback URL cannot be empty")
    @Schema(description = "Callback URL after successful authorization", required = true, example = "https://agent.xfyun.cn/work_flow/4011451/overview")
    private String redirectUrl;
}
