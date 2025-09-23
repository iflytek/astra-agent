package com.iflytek.astron.console.hub.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDateTime;
import lombok.Data;

@Data
@TableName("xingchen_prompt_version")
@Schema(name = "XingchenPromptVersion", description = "Xingchen Prompt Version Management Table")
public class XingchenPromptVersion {

    @TableId(type = IdType.AUTO)
    private Long id;

    @Schema(description = "MongoDB original _id")
    private String mongodbId;

    @Schema(description = "Associated Prompt ID")
    private String promptId;

    @Schema(description = "User ID")
    private String uid;

    @Schema(description = "Version number")
    private String version;

    @Schema(description = "Version description")
    private String versionDesc;

    @Schema(description = "Commit time")
    private LocalDateTime commitTime;

    @Schema(description = "Commit user ID")
    private Long commitUser;

    @Schema(description = "Model configuration information (JSON format)")
    private String modelConfig;

    @Schema(description = "Prompt text content (JSON format)")
    private String promptText;

    @Schema(description = "Prompt input variable configuration (JSON format)")
    private String promptInput;

    @Schema(description = "Is deleted: 0-no, 1-yes")
    private Integer isDelete;

    @Schema(description = "Create time")
    private LocalDateTime createTime;

    @Schema(description = "Update time")
    private LocalDateTime updateTime;
}
