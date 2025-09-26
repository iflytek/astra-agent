package com.iflytek.astron.console.hub.dto.publish;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

/**
 * Publish Status Update Request DTO
 *
 * @author Omuigix
 * @since 2024-12-16
 */
@Data
@Schema(description = "Publish status update request")
public class PublishStatusUpdateDto {

    @Schema(description = "Action type", example = "PUBLISH", allowableValues = {"PUBLISH", "OFFLINE"})
    @NotBlank(message = "Action type cannot be empty")
    @Pattern(regexp = "^(PUBLISH|OFFLINE)$", message = "Action type can only be PUBLISH or OFFLINE")
    private String action;

    @Schema(description = "Action reason", example = "Publish to market")
    private String reason;
}
