package com.iflytek.astron.console.commons.entity.bot;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.Arrays;

@Data
@TableName("bot_type_list")
@Schema(name = "BotTypeList", description = "Bot Type Mapping Table")
public class BotTypeList {

    @TableId(type = IdType.AUTO)
    private Integer id;

    @Schema(description = "Bot type code")
    private Integer typeKey;

    @Schema(description = "Bot type name")
    private String typeName;

    @Schema(description = "Sort order number")
    private Integer orderNum;

    @Schema(description = "Recommended status: 1 Recommended, 0 Not recommended")
    private Integer showIndex;

    @Schema(description = "Enable status: 0 Disabled, 1 Enabled")
    private Integer isAct;

    @Schema(description = "Creation time")
    private LocalDateTime createTime;

    @Schema(description = "Update time")
    private LocalDateTime updateTime;

    @Schema(description = "Icon URL")
    private String icon;

    @Schema(description = "Bot type English name")
    private String typeNameEn;

    public static Integer getParentTypeKey(Integer botType) {
        if (botType == null) {
            return null;
        }
        if (Arrays.asList(10, 11, 37, 16, 18).contains(botType)) {
            return 10;
        }
        if (Arrays.asList(13, 12, 23, 21).contains(botType)) {
            return 13;
        }
        if (Arrays.asList(15, 19, 22, 20, 39).contains(botType)) {
            return 15;
        }
        return botType;
    }
}
