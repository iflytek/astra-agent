package com.iflytek.astron.console.hub.converter;

import com.iflytek.astron.console.hub.dto.publish.BotPublishInfoDto;
import com.iflytek.astron.console.hub.dto.publish.BotDetailResponseDto;
import com.iflytek.astron.console.commons.entity.bot.BotPublishQueryResult;
import org.mapstruct.*;

import java.util.ArrayList;
import java.util.List;

/**
 * Bot Publish Information Converter
 *
 * Uses MapStruct for high-quality object mapping: 1. Compile-time code generation with excellent
 * performance 2. Type-safe with compile-time checking 3. Customizable mapping rules 4. Easy to test
 * and debug
 *
 * @author Omuigix
 */
@Mapper(
        componentModel = "spring",
        unmappedTargetPolicy = ReportingPolicy.WARN,
        nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE,
        imports = {ArrayList.class})
public interface BotPublishConverter {

    /**
     * Convert query result entity to DTO using type-safe MapStruct object mapping
     *
     * @param queryResult Query result entity
     * @return BotPublishInfoDto
     */
    @Mapping(target = "publishStatus", source = "botStatus")
    @Mapping(target = "publishChannels", source = "publishChannels", qualifiedByName = "parsePublishChannels")
    @Mapping(target = "avatar", ignore = true)
    @Mapping(target = "botType", ignore = true)
    BotPublishInfoDto queryResultToDto(BotPublishQueryResult queryResult);

    /**
     * Batch convert query results to DTO list using MapStruct batch conversion
     *
     * @param queryResults Query results list
     * @return DTO list
     */
    List<BotPublishInfoDto> queryResultsToDtoList(List<BotPublishQueryResult> queryResults);

    /**
     * Convert query result entity to detail DTO for bot detail interface
     *
     * @param queryResult Query result entity
     * @return BotDetailResponseDto
     */
    @Mapping(target = "publishStatus", source = "botStatus")
    @Mapping(target = "publishChannels", source = "publishChannels", qualifiedByName = "parsePublishChannels")
    @Mapping(target = "wechatRelease", constant = "0")
    @Mapping(target = "wechatAppid", ignore = true)
    BotDetailResponseDto queryResultToDetailDto(BotPublishQueryResult queryResult);

    /**
     * Parse publish channel string, converting comma-separated string from database to List
     *
     * @param publishChannels Publish channel string (comma-separated: MARKET,API,WECHAT,MCP)
     * @return Publish channels list
     */
    @Named("parsePublishChannels")
    default List<String> parsePublishChannels(String publishChannels) {
        List<String> channels = new ArrayList<>();

        if (publishChannels != null && !publishChannels.trim().isEmpty()) {
            String[] channelArray = publishChannels.split(",");
            for (String channel : channelArray) {
                String trimmedChannel = channel.trim();
                if (!trimmedChannel.isEmpty()) {
                    channels.add(trimmedChannel);
                }
            }
        }

        return channels;
    }

}
