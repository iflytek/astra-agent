package com.iflytek.astron.console.toolkit.entity.common;

import lombok.Data;

import java.util.List;
import java.util.Map;

@Data
public class PageData<T> {
    private Integer page;
    private Integer pageSize;
    private Long totalCount;
    private Long totalPages;
    private List<T> pageData;
    private Map<String, Object> extMap;
    private Map<String, Long> fileSliceCount;
}
