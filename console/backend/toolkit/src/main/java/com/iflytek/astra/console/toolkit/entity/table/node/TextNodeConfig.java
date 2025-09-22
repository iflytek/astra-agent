package com.iflytek.astra.console.toolkit.entity.table.node;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.util.Date;

/**
 * @Author clliu19
 * @Date: 2025/3/7 09:46
 */
@Data
public class TextNodeConfig {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String uid;
    /**
     * Separator
     */
    @TableField("`separator`")
    private String separator;
    /**
     * Comment
     */
    @TableField("`comment`")
    private String comment;

    private Boolean deleted;

    private Date createTime;

    private Date updateTime;
}
