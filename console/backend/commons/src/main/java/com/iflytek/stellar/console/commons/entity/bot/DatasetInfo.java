package com.iflytek.astra.console.commons.entity.bot;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("dataset_info")
@Schema(name = "DatasetInfo", description = "Private dataset information table")
public class DatasetInfo {

    @TableId(type = IdType.AUTO)
    @Schema(description = "Dataset ID")
    private Long id;

    @Schema(description = "User ID")
    private String uid;

    @Schema(description = "Dataset name")
    private String name;

    @Schema(description = "Dataset description")
    private String description;

    @Schema(description = "File count")
    private Integer fileNum;

    @Schema(description = "Status: -1 Deleted, 0 Not processed, 1 Processing, 2 Processed, 3 Processing failed")
    private Integer status;

    /**
     * 数据集类型 0-星火(默认)；1-maas
     */
    @TableField(exist = false)
    private int type;

    @Schema(description = "Create time")
    private LocalDateTime createTime;

    @Schema(description = "Update time")
    private LocalDateTime updateTime;
}
