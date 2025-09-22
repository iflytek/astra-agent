package com.iflytek.astra.console.toolkit.entity.table.eval;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

/**
 * User thread pool configuration table entity class
 */
@Data
@TableName("user_thread_pool_config")
public class UserThreadPoolConfig {
    /**
     * Primary key ID
     */
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /**
     * User ID
     */
    private String uid;

    /**
     * Thread pool size
     */
    private Integer size;
}
