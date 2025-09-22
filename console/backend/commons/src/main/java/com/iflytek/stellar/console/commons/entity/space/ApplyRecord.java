package com.iflytek.astra.console.commons.entity.space;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDateTime;
import lombok.Data;

@Data
@TableName("agent_apply_record")
@Schema(name = "AgentApplyRecord", description = "Application record for joining space/enterprise")
public class ApplyRecord {

    @TableId(type = IdType.AUTO)
    private Long id;

    @Schema(description = "Enterprise team ID")
    private Long enterpriseId;

    @Schema(description = "Space ID")
    private Long spaceId;

    @Schema(description = "Applicant UID")
    private String applyUid;

    @Schema(description = "Applicant nickname")
    private String applyNickname;

    @Schema(description = "Application time")
    private LocalDateTime applyTime;

    @Schema(description = "Application status: 1 pending, 2 approved, 3 rejected")
    private Integer status;

    @Schema(description = "Review time")
    private LocalDateTime auditTime;

    @Schema(description = "Reviewer UID")
    private String auditUid;

    @Schema(description = "Creation time")
    private LocalDateTime createTime;

    @Schema(description = "Update time")
    private LocalDateTime updateTime;

    public enum Status {

        APPLYING(1, "pending"),
        APPROVED(2, "approved"),
        REJECTED(3, "rejected");

        private Integer code;

        private String desc;


        Status(Integer code, String desc) {
            this.code = code;
            this.desc = desc;
        }

        public Integer getCode() {
            return code;
        }

        public String getDesc() {
            return desc;
        }
    }

}
