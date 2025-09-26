package com.iflytek.astron.console.commons.dto.bot;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import cn.hutool.core.util.StrUtil;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 智能体查询条件封装
 *
 * 最佳实践： 1. 使用强类型对象替代Map<String, Object> 2. 集中参数验证和业务逻辑 3. 提供类型安全和IDE支持 4. 便于单元测试和维护
 *
 * @author Omuigix
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BotQueryCondition {

    /**
     * 用户ID（必填）
     */
    private String uid;

    /**
     * 空间ID（可选）
     */
    private Long spaceId;

    /**
     * 搜索关键词（可选）
     */
    private String keyword;


    /**
     * 版本（可选）
     */
    private Integer version;

    /**
     * 发布状态列表（可选）
     */
    private List<Integer> publishStatus;

    /**
     * 排序字段（必填，有默认值）
     */
    private String sortField;

    /**
     * 排序方向（必填，有默认值）
     */
    private String sortDirection;

    // ==================== 业务逻辑方法 ====================

    /**
     * 支持的排序字段白名单
     */
    private static final Set<String> ALLOWED_SORT_FIELDS = Set.of(
            "createTime", "updateTime", "applyTime", "publishTime");

    /**
     * 支持的排序方向白名单
     */
    private static final Set<String> ALLOWED_SORT_DIRECTIONS = Set.of("ASC", "DESC");

    /**
     * 验证并获取安全的排序字段 防止SQL注入攻击 将驼峰命名转换为数据库下划线命名
     */
    public String getSafeSortField() {
        String field = sortField;
        if (field == null || !ALLOWED_SORT_FIELDS.contains(field)) {
            field = "createTime"; // 默认排序字段
        }

        // 将驼峰命名转换为下划线命名
        switch (field) {
            case "createTime":
                return "create_time";
            case "updateTime":
                return "update_time";
            case "applyTime":
                return "apply_time";
            case "publishTime":
                return "publish_time";
            default:
                return "create_time"; // 默认值
        }
    }

    /**
     * 验证并获取安全的排序方向
     */
    public String getSafeSortDirection() {
        if (sortDirection == null || !ALLOWED_SORT_DIRECTIONS.contains(sortDirection.toUpperCase())) {
            return "DESC"; // 默认排序方向
        }
        return sortDirection.toUpperCase();
    }

    /**
     * 检查是否有关键词搜索
     */
    public boolean hasKeyword() {
        return keyword != null && !keyword.trim().isEmpty();
    }

    /**
     * 检查是否有状态筛选
     */
    public boolean hasPublishStatus() {
        return publishStatus != null && !publishStatus.isEmpty();
    }

    /**
     * 获取发布状态列表（简化版本，只支持0=下架，1=上架）
     */
    public List<Integer> getPublishStatus() {
        return publishStatus;
    }

    /**
     * 验证必填参数
     */
    public void validate() {
        if (uid == null) {
            throw new IllegalArgumentException("用户ID不能为空");
        }
        // 其他验证逻辑...
    }

    /**
     * 转换为查询参数 Map
     *
     * @return 查询参数，用于 Mapper 查询
     */
    public Map<String, Object> toQueryParams() {
        Map<String, Object> params = new HashMap<>();

        // 基础参数
        params.put("uid", this.uid);
        params.put("spaceId", this.spaceId);

        // 搜索条件
        if (this.keyword != null && !this.keyword.trim().isEmpty()) {
            params.put("keyword", this.keyword.trim());
        }

        // 版本筛选
        if (this.version != null) {
            params.put("version", this.version);
        }

        // 发布状态处理（简化版本）
        if (this.publishStatus != null && !this.publishStatus.isEmpty()) {
            params.put("publishStatus", this.publishStatus);
        }

        // 排序处理
        if (this.sortField != null) {
            String dbField = getSafeSortField();
            String direction = this.sortDirection != null ? this.sortDirection.toUpperCase() : "DESC";

            if ("createTime".equals(dbField)) {
                params.put("sort", "a.create_time " + direction);
            } else if ("updateTime".equals(dbField)) {
                params.put("sort", "a.update_time " + direction);
            }
        }

        return params;
    }

    // ==================== 静态构建方法 ====================

    /**
     * 从请求DTO构建查询条件
     *
     * @param requestDto 请求DTO
     * @param currentUid 当前用户ID
     * @param spaceId 空间ID
     * @return 查询条件对象
     */
    public static BotQueryCondition from(BotListRequestDto requestDto, String currentUid, Long spaceId) {
        return BotQueryCondition.builder()
                .uid(currentUid)
                .spaceId(spaceId)
                .keyword(normalizeKeyword(requestDto.getKeyword()))
                .version(requestDto.getVersion())
                .publishStatus(requestDto.getPublishStatusList())
                .sortField(requestDto.getSortField())
                .sortDirection(requestDto.getSortDirection())
                .build();
    }

    /**
     * 标准化关键词 处理空白字符，避免无效查询
     */
    private static String normalizeKeyword(String keyword) {
        if (StrUtil.isBlank(keyword)) {
            return null;
        }
        return keyword.trim();
    }
}
