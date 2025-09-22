package com.iflytek.astra.console.hub.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDateTime;
import lombok.Data;

@Data
@TableName("workflow_template_group")
@Schema(name = "WorkflowTemplateGroup", description = "Astra workflow template group (general management control)")
public class WorkflowTemplateGroup {

    @TableId(type = IdType.AUTO)
    private Integer id;

    @Schema(description = "Publisher domain account")
    private String createUser;

    @Schema(description = "Group name")
    private String groupName;

    @Schema(description = "Sort index")
    private Integer sortIndex;

    @Schema(description = "Logical deletion flag: 0 not deleted, 1 deleted")
    private Integer isDelete;

    @Schema(description = "Creation time")
    private LocalDateTime createTime;

    @Schema(description = "Update time")
    private LocalDateTime updateTime;

    @Schema(description = "Group English name")
    private String groupNameEn;
}
