package com.iflytek.astron.console.commons.entity.bot;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDateTime;
import lombok.Data;

@Data
@TableName("bot_dataset")
@Schema(name = "BotDataset", description = "Bot associated dataset index table")
public class BotDataset {

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
