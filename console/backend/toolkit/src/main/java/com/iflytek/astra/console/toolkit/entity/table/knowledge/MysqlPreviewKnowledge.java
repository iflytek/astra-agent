package com.iflytek.astra.console.toolkit.entity.table.knowledge;

import com.alibaba.fastjson2.JSONObject;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.iflytek.astra.console.toolkit.handler.MySqlJsonHandler;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;

import java.time.LocalDateTime;

@EqualsAndHashCode(callSuper = false)
@ToString(callSuper = true)
@Data
@TableName("preview_knowledge")
public class MysqlPreviewKnowledge {
    @TableId(type = IdType.ASSIGN_UUID)
    private String id;
    private String fileId;
    // Knowledge point
    @TableField(typeHandler = MySqlJsonHandler.class)
    private JSONObject content;
    private Long charCount;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
