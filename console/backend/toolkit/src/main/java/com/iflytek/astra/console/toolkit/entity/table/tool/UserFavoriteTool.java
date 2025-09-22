package com.iflytek.astra.console.toolkit.entity.table.tool;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;
import org.springframework.format.annotation.DateTimeFormat;

import java.io.Serializable;
import java.sql.Timestamp;

/**
 * @author clliu19
 * @date 2024/05/23/10:01
 */
@Data
public class UserFavoriteTool implements Serializable {
    private static final long serialVersionUID = 1L;
    @TableId(type = IdType.AUTO)
    private Long id;
    private String userId;
    private Long toolId;

    private String mcpToolId;
    private String pluginToolId;
    /**
     * Usage flag: 1-favorite, 2-usage
     */
    private Integer useFlag;
    /**
     * Whether deleted: 1-deleted, 0-not deleted
     */
    private Boolean deleted;


    /**
     * Creation time
     */
    @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private Timestamp createdTime;
}
