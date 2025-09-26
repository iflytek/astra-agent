package com.iflytek.astron.console.hub.dto.wechat;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 微信授权callback数据DTO
 *
 * @author Omuigix
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "微信授权回调数据DTO")
public class WechatAuthCallbackDto {

    @Schema(description = "第三方平台AppID")
    private String appId;

    @Schema(description = "信息类型：authorized/updateauthorized/unauthorized")
    private String infoType;

    @Schema(description = "授权方AppID")
    private String authorizerAppid;

    @Schema(description = "授权码")
    private String authorizationCode;

    @Schema(description = "授权码过期时间")
    private String authorizationCodeExpiredTime;

    @Schema(description = "预授权码")
    private String preAuthCode;

    @Schema(description = "创建时间")
    private String createTime;

    @Schema(description = "组件验证票据")
    private String componentVerifyTicket;
}
