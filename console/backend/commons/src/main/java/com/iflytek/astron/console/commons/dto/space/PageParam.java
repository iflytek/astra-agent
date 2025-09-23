package com.iflytek.astron.console.commons.dto.space;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import jakarta.validation.constraints.NotNull;

@Data
public class PageParam {

    @Schema(description = "Page number")
    @NotNull(message = "Page number cannot be null")
    private Integer pageNum;

    @Schema(description = "Page size")
    @NotNull(message = "Page size cannot be null")
    private Integer pageSize;
}
