package com.iflytek.astron.console.hub.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 通用分页响应DTO 对应老代码中的分页返回结构
 *
 * @author Omuigix
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class PageResponse<T> {

    /**
     * 当前页码
     */
    private Integer page;

    /**
     * 页大小
     */
    private Integer size;

    /**
     * 总记录数
     */
    private Long total;

    /**
     * 总页数
     */
    private Integer totalPages;

    /**
     * 数据列表
     */
    private List<T> records;

    /**
     * 是否有下一页
     */
    private Boolean hasNext;

    /**
     * 是否有上一页
     */
    private Boolean hasPrevious;

    /**
     * 构造分页响应
     */
    public static <T> PageResponse<T> of(Integer page, Integer size, Long total, List<T> records) {
        PageResponse<T> response = new PageResponse<>();
        response.setPage(page);
        response.setSize(size);
        response.setTotal(total);
        response.setRecords(records);

        // 计算总页数
        int totalPages = (int) Math.ceil((double) total / size);
        response.setTotalPages(totalPages);

        // 计算是否有上下页
        response.setHasNext(page < totalPages);
        response.setHasPrevious(page > 1);

        return response;
    }

    /**
     * 空结果
     */
    public static <T> PageResponse<T> empty(Integer page, Integer size) {
        return of(page, size, 0L, List.of());
    }
}
