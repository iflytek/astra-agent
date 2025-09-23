package com.iflytek.astron.console.hub.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("ai_prompt_template")
public class AiPromptTemplate {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String promptKey;

    private String languageCode;

    private String promptContent;

    private Integer isActive;

    private LocalDateTime createdTime;

    private LocalDateTime updatedTime;
}
