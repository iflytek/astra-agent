package com.iflytek.astron.console.toolkit.entity.table.model;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.util.Date;

/**
 * @Author clliu19
 * @Date: 2025/8/18 17:17
 */
@Data
public class ModelCategory {
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    @TableField(value = "pid")
    private Long pid;

    @TableField(value = "key")
    private String key;

    @TableField(value = "`name`")
    private String name;

    @TableField(value = "is_delete")
    private Byte isDelete;

    @TableField(value = "create_time")
    private Date createTime;

    @TableField(value = "update_time")
    private Date updateTime;

    /**
     * Sort order
     */
    @TableField(value = "sort_order")
    private Integer sortOrder;
    /**
     * SYSTEM / CUSTOM, used by frontend to identify source
     */
    @TableField(exist = false)
    private String source;

    public static final String COL_ID = "id";

    public static final String COL_PID = "pid";

    public static final String COL_NAME = "name";

    public static final String COL_IS_DELETE = "is_delete";

    public static final String COL_CREATE_TIME = "create_time";

    public static final String COL_UPDATE_TIME = "update_time";

    public static final String COL_SORT_ORDER = "sort_order";
}
