package com.iflytek.astron.console.commons.dto.bot;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;
import com.fasterxml.jackson.annotation.JsonIgnore;

/**
 * 智能体列表查询请求DTO 对应老代码中的 BotMarketForm
 *
 * @author Omuigix
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(
        description = "智能体列表查询请求DTO",
        name = "BotListRequestDto",
        title = "智能体列表查询参数")
public class BotListRequestDto {

    /**
     * 页码 (从1开始)
     */
    @Schema(
            description = "页码，从1开始",
            example = "1",
            defaultValue = "1",
            minimum = "1",
            required = false)
    @Min(value = 1, message = "页码必须大于0")
    @Builder.Default
    private Integer page = 1;

    /**
     * 页大小
     */
    @Schema(
            description = "每页记录数，最大200",
            example = "10",
            defaultValue = "10",
            minimum = "1",
            maximum = "200",
            required = false)
    @Min(value = 1, message = "页大小必须大于0")
    @Max(value = 200, message = "页大小不能超过200")
    @Builder.Default
    private Integer size = 10;

    /**
     * 搜索关键词 (智能体名称)
     */
    @Schema(
            description = "搜索关键词，支持智能体名称、描述模糊匹配",
            example = "客服机器人",
            maxLength = 100,
            required = false)
    @Size(max = 100, message = "关键词长度不能超过100")
    private String keyword;


    /**
     * 发布状态筛选（逗号分隔）
     * <ul>
     * <li>0 = 下架</li>
     * <li>1 = 上架</li>
     * </ul>
     * 支持格式："0" 或 "1" 或 "0,1"
     */
    @Schema(
            description = "发布状态筛选，逗号分隔多个状态。例如：\"0,1\" 表示查询下架和上架的智能体。状态值：0=下架，1=上架",
            example = "0,1",
            required = false)
    private String publishStatus;

    /**
     * 版本筛选
     * <ul>
     * <li>1 = 指令式智能体版本</li>
     * <li>3 = 工作流智能体版本</li>
     * <li>null = 查询全部版本</li>
     * </ul>
     */
    @Schema(
            description = "版本筛选：1=指令式智能体版本，3=工作流智能体版本，不传则查询全部",
            example = "1",
            allowableValues = {"1", "3"},
            required = false)
    private Integer version;

    /**
     * 排序字段
     * <ul>
     * <li>createTime = 按创建时间排序</li>
     * <li>updateTime = 按更新时间排序</li>
     * </ul>
     */
    @Schema(
            description = "排序字段：createTime=按创建时间排序，updateTime=按更新时间排序",
            example = "createTime",
            defaultValue = "createTime",
            allowableValues = {"createTime", "updateTime"},
            required = false)
    @Builder.Default
    private String sortField = "createTime";

    /**
     * 排序方向
     * <ul>
     * <li>ASC = 升序排列</li>
     * <li>DESC = 降序排列</li>
     * </ul>
     */
    @Schema(
            description = "排序方向：ASC=升序排列，DESC=降序排列",
            example = "DESC",
            defaultValue = "DESC",
            allowableValues = {"ASC", "DESC"},
            required = false)
    @Builder.Default
    private String sortDirection = "DESC";

    /**
     * 解析发布状态字符串为整数列表
     *
     * 支持格式： - "1" -> [1] - "1,2" -> [1, 2] - "1,2,3" -> [1, 2, 3] - null或空字符串 -> []
     *
     * 注意：此方法仅用于内部逻辑，不会出现在API文档中
     *
     * @return 解析后的状态列表
     */
    @JsonIgnore
    public List<Integer> getPublishStatusList() {
        if (publishStatus == null || publishStatus.trim().isEmpty()) {
            return new ArrayList<>();
        }

        try {
            return Arrays.stream(publishStatus.split(","))
                    .map(String::trim)
                    .filter(s -> !s.isEmpty())
                    .map(Integer::parseInt)
                    .collect(Collectors.toList());
        } catch (NumberFormatException e) {
            // 解析失败时返回空列表，避免抛出异常
            return new ArrayList<>();
        }
    }

}
