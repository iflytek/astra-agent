package com.iflytek.astron.console.hub.dto.share;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * @author yingpeng
 */
@Data
public class CardAddBody {

    @Min(value = 0, message = "Relation type cannot be empty")
    private int relateType;

    @NotNull(message = "Relation ID cannot be empty")
    private Long relateId;

}
