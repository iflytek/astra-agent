package com.iflytek.astron.console.commons.entity.wechat;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * Bot WeChat Official Account Binding Information
 *
 * @author Omuigix
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
@TableName("bot_offiaccount")
@Schema(name = "BotOffiaccount", description = "Bot WeChat Official Account binding information")
public class BotOffiaccount {

    @TableId(value = "id", type = IdType.AUTO)
    @Schema(description = "Primary key ID")
    private Long id;

    @Schema(description = "User ID")
    private String uid;

    @Schema(description = "Bot ID")
    private Integer botId;

    @Schema(description = "WeChat Official Account AppID")
    private String appid;

    @Schema(description = "Binding status: 1=bound, 2=unbound")
    private Integer status;

    @Schema(description = "Create time")
    private LocalDateTime createTime;

    @Schema(description = "Update time")
    private LocalDateTime updateTime;
}
