package com.iflytek.astron.console.toolkit.entity.table.knowledge;

import com.alibaba.fastjson2.JSONObject;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.iflytek.astron.console.toolkit.handler.MySqlJsonHandler;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;

import java.time.LocalDateTime;

@EqualsAndHashCode(callSuper = false)
@ToString(callSuper = true)
@Data
@TableName("knowledge")
public class MysqlKnowledge {
    @TableId(type = IdType.ASSIGN_UUID)
    private String id;
    private String fileId;
    // Knowledge point
    @TableField(typeHandler = MySqlJsonHandler.class)
    private JSONObject content;
    private Long charCount;
    // Enable status 1: Enabled 0: Disabled
    private Integer enabled;
    // Source 0: Default from file parsing 1: Manually added
    private Integer source;
    // Test hit count
    private Long testHitCount;
    // Dialog hit count
    private Long dialogHitCount;
    // Core repo name
    private String coreRepoName;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
