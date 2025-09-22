package com.iflytek.astra.console.commons.entity.bot;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * Workflow configuration table entity class
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName(value = "user_lang_chain_info", autoResultMap = true)
public class UserLangChainInfo {

    /**
     * Non-business primary key
     */
    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * Agent ID
     */
    private Integer botId;

    /**
     * LangChain name
     */
    private String name;

    /**
     * Agent description
     */
    @TableField("`desc`")
    private String desc;

    /**
     * Open configuration information, including nodes and edges
     */
    private String open;

    /**
     * GCY configuration information, including virtual nodes and edges
     */
    private String gcy;

    /**
     * User ID
     */
    private String uid;

    /**
     * Flow ID
     */
    private String flowId;

    /**
     * Group ID
     */
    private Long maasId;

    /**
     * Agent name
     */
    private String botName;

    /**
     * Extra input items
     */
    private String extraInputs;

    /**
     * Multi-file parameters
     */
    private String extraInputsConfig;

    private Long spaceId;

    /**
     * Creation time
     */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createTime;

    /**
     * Update time
     */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime updateTime;
}
