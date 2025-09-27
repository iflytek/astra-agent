package com.iflytek.astron.console.toolkit.entity.vo;

import lombok.*;

import java.util.List;

/**
 * @Author clliu19
 * @Date: 2025/8/18 18:06
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class CategoryTreeVO {
    private Long id;
    private String key;
    private String name;
    private Integer sortOrder;
    private List<CategoryTreeVO> children;
    /**
     * SYSTEM / CUSTOM, used by frontend to identify source
     */
    private String source;
}
