package com.iflytek.astra.console.toolkit.entity.biz.modelconfig;

import lombok.Data;

/**
 * @Author clliu19
 * @Date: 2025/4/14 15:56
 */
@Data
public class ModelDto {
    // 0 All 1 Public model 2 Personal model
    private Integer type;
    // 0 All; 1 Custom model; 2 Fine-tuned model
    private Integer filter;
    private String name;
    private Integer page;
    private Integer pageSize;
    private String uid;
    private Long spaceId;
}
