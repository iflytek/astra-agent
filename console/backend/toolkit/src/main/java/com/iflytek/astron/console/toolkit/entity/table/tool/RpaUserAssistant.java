package com.iflytek.astron.console.toolkit.entity.table.tool;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * Entity of the user's RPA assistant (main table).
 * <p>
 * Mapped to table {@code rpa_user_assistant}. This entity stores the basic metadata of an RPA
 * assistant created/owned by a user under a specific space/tenant.
 */
@TableName("rpa_user_assistant")
@Data
public class RpaUserAssistant {

    /**
     * Primary key (auto-increment).
     */
    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * Owner user ID (string form, consistent with the authentication system).
     */
    private String userId;

    /**
     * Platform ID that defines the RPA vendor/source this assistant belongs to.
     * <p>
     * References {@code rpa_info.id}.
     * </p>
     */
    private Long platformId;

    /**
     * Assistant display name defined by the user.
     */
    private String assistantName;

    /**
     * Assistant status (e.g., enabled/disabled).
     * <p>
     * Exact semantics depend on the service layer.
     * </p>
     */
    private Integer status;

    /**
     * Optional remarks or description for this assistant.
     */
    private String remarks;

    /**
     * Space/tenant identifier to which the assistant belongs.
     */
    private Long spaceId;

    /**
     * Assistant icon URL (if any).
     */
    private String icon;

    /**
     * Cached number of robots/workflows associated with this assistant.
     * <p>
     * Maintained by service calls to the RPA platform.
     * </p>
     */
    private Integer robotCount;

    /**
     * Record creation time.
     */
    private LocalDateTime createTime;

    /**
     * Last update time.
     */
    private LocalDateTime updateTime;
}
