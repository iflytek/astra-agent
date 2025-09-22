package com.iflytek.astra.console.toolkit.entity.common;

import lombok.*;

import java.util.List;

/**
 * @author: tctan
 * @date: 2023/2/24 18:22
 * @description:
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class PagedList<T> {
    private Pagination pagination;
    private List<T> list;
}
