package com.iflytek.astra.console.commons.dto.space;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import jakarta.validation.constraints.NotEmpty;

@Data
@Schema(name = "Add space request parameters")
public class SpaceAddDTO {

    @Schema(description = "Space name")
    @NotEmpty(message = "Space name cannot be empty")
    private String name;

    @Schema(description = "Space description")
    private String description;

    @Schema(description = "Space avatar URL")
    private String avatarUrl;

}
