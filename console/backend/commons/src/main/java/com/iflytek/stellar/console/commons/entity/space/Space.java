package com.iflytek.astra.console.commons.entity.space;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDateTime;
import lombok.Data;

@Data
@TableName("agent_space")
@Schema(name = "AgentSpace", description = "Space")
public class Space {

    @TableId(type = IdType.AUTO)
    private Long id;

    @Schema(description = "Space name")
    private String name;

    @Schema(description = "Description")
    private String description;

    @Schema(description = "Avatar URL")
    private String avatarUrl;

    @Schema(description = "Creator ID")
    private String uid;

    @Schema(description = "Enterprise ID")
    private Long enterpriseId;

    @Schema(description = "Type: 1 Free, 2 Pro, 3 Team, 4 Enterprise")
    private Integer type;

    @Schema(description = "Creation time")
    private LocalDateTime createTime;

    @Schema(description = "Update time")
    private LocalDateTime updateTime;

    @Schema(description = "Deleted: 0 no, 1 yes")
    private Integer deleted;
}
