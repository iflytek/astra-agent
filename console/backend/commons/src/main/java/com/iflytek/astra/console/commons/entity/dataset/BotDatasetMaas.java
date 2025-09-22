package com.iflytek.astra.console.commons.entity.dataset;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonFormat;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import org.springframework.format.annotation.DateTimeFormat;

import java.time.LocalDateTime;

@Data
@TableName("bot_dataset_maas")
@Schema(name = "BotDatasetMaas", description = "Bot associated MAAS dataset index table")
public class BotDatasetMaas {

    @TableId(type = IdType.AUTO)
    private Long id;

    @Schema(description = "Corresponding primary key ID from chat_bot_base table")
    private Long botId;

    @Schema(description = "Primary key ID from dataset_info table")
    private Long datasetId;

    @Schema(description = "Dataset ID from knowledge database")
    private String datasetIndex;

    @Schema(description = "Active status: 0 inactive, 1 active, 2 under review after market update")
    private Integer isAct;

    @Schema(description = "Creation time")
    private LocalDateTime createTime;

    @Schema(description = "Update time")
    private LocalDateTime updateTime;

    @Schema(description = "User ID")
    private String uid;
}
