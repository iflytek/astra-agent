package com.iflytek.astron.console.toolkit.entity.dto;

import com.baomidou.mybatisplus.annotation.TableField;
import lombok.Data;

import java.io.Serializable;

@Data
public class ToolBoxFeedbackReq implements Serializable {

    private static final long serialVersionUID = 1L;

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
}
