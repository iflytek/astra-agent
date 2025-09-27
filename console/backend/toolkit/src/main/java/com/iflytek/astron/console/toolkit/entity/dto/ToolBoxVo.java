package com.iflytek.astron.console.toolkit.entity.dto;

import com.iflytek.astron.console.toolkit.entity.table.tool.ToolBox;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.List;

@EqualsAndHashCode(callSuper = true)
@Data
public class ToolBoxVo extends ToolBox {
    String address;

    List<SparkBotVO> bots;

    Boolean isFavorite;

    Integer botUsedCount;

    String creator;

    List<String> tags;

    Long heatValue;

    Boolean isMcp = false;

    String mcpTooId;

    Boolean authorized;
}
