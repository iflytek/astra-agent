package com.iflytek.astra.console.hub.dto.share;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * @author yingpeng
 */
@Data
public class CardAddBody {

    @Min(value = 0, message = "关联类型不能为空")
    private int relateType;

    @NotNull(message = "关联ID不能为空")
    private Long relateId;

}
