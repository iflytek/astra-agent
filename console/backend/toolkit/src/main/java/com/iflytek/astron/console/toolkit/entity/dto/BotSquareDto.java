package com.iflytek.astron.console.toolkit.entity.dto;

import lombok.Data;

/**
 * @author clliu19
 * @date 2024/05/24/10:09
 */
@Data
public class BotSquareDto {

    private Integer pageNo = 1;

    private Integer pageSize = 10;

    private String content;

    private Integer favoriteFlag;

    private Long tags;

    private Integer tagFlag;
}
