package com.iflytek.astra.console.toolkit.entity.table.model;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.util.Date;

/**
 * @Author clliu19
 * @Date: 2025/8/18 17:17
 */
@Data
public class ModelCustomCategory {
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    @TableField(value = "pid")
    private Long pid;
    @TableField(value = "owner_uid")
    private String ownerUid;

    @TableField("`key`")
    private String key;
    @TableField(value = "normalized")
    private String normalized;

    @TableField(value = "`name`")
    private String name;

    @TableField(value = "is_delete")
    private Byte isDelete;

    @TableField(value = "create_time")
    private Date createTime;

    @TableField(value = "update_time")
    private Date updateTime;

}
