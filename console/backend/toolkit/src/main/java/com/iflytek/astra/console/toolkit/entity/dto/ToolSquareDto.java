package com.iflytek.astra.console.toolkit.entity.dto;

import lombok.Data;

/**
 * @author clliu19
 * @date 2024/05/24/10:09
 */
@Data
public class ToolSquareDto {

    private Integer page = 1;

    private Integer pageSize = 10;

    private String content;

    private Integer favoriteFlag;

    private Integer orderFlag;

    private Integer tagFlag;

    private Long tags;

    private Boolean authorized;
}
