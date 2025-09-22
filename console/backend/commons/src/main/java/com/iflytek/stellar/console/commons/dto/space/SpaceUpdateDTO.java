package com.iflytek.astra.console.commons.dto.space;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

@Data
@Schema(name = "Update space request parameters")
public class SpaceUpdateDTO {

    @Schema(description = "Space ID")
    @NotNull(message = "Space ID cannot be null")
    private Long id;

    @Schema(description = "Space name")
    @NotEmpty(message = "Space name cannot be empty")
    private String name;

    @Schema(description = "Space description")
    private String description;

    @Schema(description = "Space avatar URL")
    private String avatarUrl;

}
