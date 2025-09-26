package com.iflytek.astron.console.hub.converter;

import com.iflytek.astron.console.commons.enums.PublishChannelEnum;
import com.iflytek.astron.console.hub.dto.publish.BotVersionVO;
import com.iflytek.astron.console.toolkit.entity.table.workflow.WorkflowVersion;
import org.mapstruct.*;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Date;
import java.util.List;

/**
 * Workflow Version Information Converter
 *
 * Uses MapStruct for efficient object mapping
 *
 * @author Omuigix
 */
@Mapper(
        componentModel = "spring",
        unmappedTargetPolicy = ReportingPolicy.WARN,
        nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
public interface WorkflowVersionConverter {

    /**
     * Convert to version VO
     */
    @Mapping(target = "isCurrent", source = "isVersion", qualifiedByName = "convertIsCurrent")
    @Mapping(target = "createdTime", source = "createdTime", qualifiedByName = "convertDateToLocalDateTime")
    @Mapping(target = "updatedTime", source = "updatedTime", qualifiedByName = "convertDateToLocalDateTime")
    @Mapping(target = "publishChannels", source = "publishChannel", qualifiedByName = "convertPublishChannel")
    @Mapping(target = "versionNum", source = "versionNum")
    @Mapping(target = "name", source = "name")
    @Mapping(target = "description", source = "description")
    @Mapping(target = "flowId", source = "flowId")
    @Mapping(target = "data", source = "data")
    @Mapping(target = "sysData", source = "sysData")
    BotVersionVO toVersionVO(WorkflowVersion workflowVersion);

    /**
     * Batch convert to version VO list
     */
    List<BotVersionVO> toVersionVOList(List<WorkflowVersion> workflowVersions);

    /**
     * Convert isCurrent field
     */
    @Named("convertIsCurrent")
    default Boolean convertIsCurrent(Long isVersion) {
        return isVersion != null && isVersion == 1;
    }

    /**
     * Convert publishChannel field
     */
    @Named("convertPublishChannel")
    default String convertPublishChannel(Long publishChannel) {
        if (publishChannel == null)
            return null;
        // Convert Long type publish channel to string
        // Map to specific channel names based on business requirements
        switch (publishChannel.intValue()) {
            case 1:
                return PublishChannelEnum.MARKET.getCode();
            case 2:
                return PublishChannelEnum.API.getCode();
            case 3:
                return PublishChannelEnum.WECHAT.getCode();
            case 4:
                return PublishChannelEnum.MCP.getCode();
            default:
                return publishChannel.toString();
        }
    }

    /**
     * Convert Date to LocalDateTime
     */
    @Named("convertDateToLocalDateTime")
    default LocalDateTime convertDateToLocalDateTime(Date date) {
        if (date == null)
            return null;
        return date.toInstant().atZone(ZoneId.systemDefault()).toLocalDateTime();
    }
}
