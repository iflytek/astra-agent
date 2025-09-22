package com.iflytek.astra.console.toolkit.entity.table.tool;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.springframework.format.annotation.DateTimeFormat;

import java.io.Serializable;
import java.sql.Timestamp;


/**
 * <p>
 *
 * </p>
 *
 * @author xxzhang23
 * @since 2024-01-09
 */
@Data
@EqualsAndHashCode(callSuper = false)
public class ToolBoxFeedback implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * Primary key
     */
    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * Core system tool identifier
     */
    private String toolId;

    /**
     * Tool name
     */
    @TableField("`name`")
    private String name;

    /**
     * Feedback content
     */
    private String remark;

    /**
     * User ID
     */
    private String userId;

    /**
     * Creation time
     */
    @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private Timestamp createTime;

    /**
     * Modification time
     */
    @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private Timestamp updateTime;

}
