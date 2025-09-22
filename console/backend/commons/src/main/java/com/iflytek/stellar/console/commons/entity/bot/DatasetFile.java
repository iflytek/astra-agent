package com.iflytek.astra.console.commons.entity.bot;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDateTime;
import lombok.Data;

@Data
@TableName("dataset_file")
@Schema(name = "DatasetFile", description = "Private dataset file table")
public class DatasetFile {

    @TableId(type = IdType.AUTO)
    private Long id;

    @Schema(description = "Dataset ID")
    private Long datasetId;

    @Schema(description = "Dataset index")
    private String datasetIndex;

    @Schema(description = "File name")
    private String name;

    @Schema(description = "File type")
    private String docType;

    @Schema(description = "File URL")
    private String docUrl;

    @Schema(description = "S3 file URL")
    private String s3Url;

    @Schema(description = "Number of paragraphs")
    private Integer paraCount;

    @Schema(description = "Number of characters")
    private Integer charCount;

    @Schema(description = "Status: -1 deleted, 0 unprocessed, 1 processing, 2 completed, 3 failed")
    private Integer status;

    @Schema(description = "Creation time")
    private LocalDateTime createTime;

    @Schema(description = "Update time")
    private LocalDateTime updateTime;
}
