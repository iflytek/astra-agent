package com.iflytek.astron.console.toolkit.entity.table.tool;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * Entity of assistant field configuration.
 * <p>
 * Represents a single key-value pair of an assistant's credential or parameter, associated with
 * {@code rpa_user_assistant}.
 */
@Data
@TableName("rpa_user_assistant_field")
public class RpaUserAssistantField {

    /**
     * Primary key (auto-increment).
     */
    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * Related assistant ID.
     * <p>
     * Foreign key referencing {@code rpa_user_assistant.id}.
     */
    private Long assistantId;

    /**
     * Field key (technical identifier, e.g., apiKey).
     */
    private String fieldKey;

    /**
     * Field name (display name for UI or business).
     */
    private String fieldName;

    /**
     * Field value (stored as plaintext).
     */
    private String fieldValue;

    /**
     * Record creation time.
     */
    private LocalDateTime createTime;

    /**
     * Last update time.
     */
    private LocalDateTime updateTime;
}
