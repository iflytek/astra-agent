package com.iflytek.astron.console.hub.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDateTime;
import lombok.Data;

@Data
@TableName("xingchen_official_prompt")
@Schema(name = "XingchenOfficialPrompt", description = "Xingchen Official Prompt Table")
public class XingchenOfficialPrompt {

    @TableId(type = IdType.AUTO)
    private Long id;

    @Schema(description = "MongoDB original _id")
    private String mongodbId;

    @Schema(description = "Prompt name")
    private String name;

    @Schema(description = "Prompt unique identifier key")
    private String promptKey;

    @Schema(description = "User ID")
    private String uid;

    @Schema(description = "Prompt type")
    private Integer type;

    @Schema(description = "Latest version number")
    private String latestVersion;

    @Schema(description = "Model configuration information (JSON format)")
    private String modelConfig;

    @Schema(description = "Prompt text content (JSON format)")
    private String promptText;

    @Schema(description = "Prompt input variable configuration (JSON format)")
    private String promptInput;

    @Schema(description = "Status: 0-normal, 1-disabled")
    private Integer status;

    @Schema(description = "Is deleted: 0-no, 1-yes")
    private Integer isDelete;

    @Schema(description = "Commit time")
    private LocalDateTime commitTime;

    @Schema(description = "Create time")
    private LocalDateTime createTime;

    @Schema(description = "Update time")
    private LocalDateTime updateTime;
}
