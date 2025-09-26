package com.iflytek.astron.console.hub.converter;

import com.iflytek.astron.console.hub.dto.publish.mcp.McpContentResponseDto;
import com.iflytek.astron.console.commons.entity.model.McpData;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.NullValuePropertyMappingStrategy;
import org.mapstruct.ReportingPolicy;

/**
 * MCP Data Converter
 *
 * Uses MapStruct for efficient object mapping
 *
 * @author Omuigix
 */
@Mapper(
        componentModel = "spring",
        unmappedTargetPolicy = ReportingPolicy.WARN,
        nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
public interface McpDataConverter {

    /**
     * Convert entity to response DTO
     *
     * @param mcpData MCP data entity
     * @return MCP content response DTO
     */
    @Mapping(target = "released", expression = "java(mcpData.getReleased() != null && mcpData.getReleased() == 1 ? \"1\" : \"0\")")
    @Mapping(target = "args", source = "args")
    McpContentResponseDto toResponseDto(McpData mcpData);

}
