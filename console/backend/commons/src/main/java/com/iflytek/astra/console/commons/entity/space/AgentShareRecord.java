package com.iflytek.astra.console.commons.entity.space;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDateTime;
import lombok.Data;

@Data
@TableName("agent_share_record")
@Schema(name = "AgentShareRecord", description = "Agent sharing record table")
public class AgentShareRecord {

    @TableId(type = IdType.AUTO)
    private Long id;

    @Schema(description = "User ID")
    private String uid;

    @Schema(description = "Primary key ID of the shared entity")
    private Long baseId;

    @Schema(description = "Unique identifier of the share")
    private String shareKey;

    @Schema(description = "Category: 0 share assistant")
    private Integer shareType;

    @Schema(description = "Effective: 0 invalid, 1 valid")
    private Integer isAct;

    @Schema(description = "Creation time")
    private LocalDateTime createTime;

    @Schema(description = "Update time")
    private LocalDateTime updateTime;
}
