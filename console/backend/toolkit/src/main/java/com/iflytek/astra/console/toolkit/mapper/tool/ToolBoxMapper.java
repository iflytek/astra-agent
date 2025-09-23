package com.iflytek.astra.console.toolkit.mapper.tool;


import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.iflytek.astra.console.toolkit.entity.table.tool.ToolBox;
import com.iflytek.astra.console.toolkit.entity.vo.BotUsedToolVo;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.*;

/**
 * <p>
 * Mapper interface
 * </p>
 *
 * @author xxzhang23
 * @since 2024-01-09
 */

@Mapper

public interface ToolBoxMapper extends BaseMapper<ToolBox> {

    int getModelListCountByCondition(@Param("userId") String userId,
            @Param("spaceId") Long spaceId,
            @Param("content") String content,
            @Param("status") Integer status);

    List<ToolBox> getModelListByCondition(@Param("userId") String userId,
            @Param("spaceId") Long spaceId,
            @Param("content") String content,
            @Param("start") Integer start,
            @Param("limit") Integer limit,
            @Param("status") Integer status);

    List<ToolBox> getModelListSquareByCondition(@Param("userId") String userId,
            @Param("content") String content,
            @Param("start") Integer start,
            @Param("limit") Integer limit,
            @Param("favorites") Set<String> favorites,
            @Param("orderFlag") Integer orderFlag,
            @Param("tagFlag") Integer tagFlag,
            @Param("tags") Long tags,
            @Param("adminUid") String adminUid,
            @Param("source") String source);

    @Deprecated
    List<ToolBox> selectPublicTool();

    Optional<ToolBox> findById(Long toolId);

    /**
     * Get user favorite tool list
     *
     * @param favorites
     * @return
     */
    List<ToolBox> getToolByIds(@Param("favorites") Set<Long> favorites);

    /**
     * Bot usage count
     *
     * @param toolId
     * @return
     */
    Integer getBotUsedCount(@Param("toolId") String toolId);

    List<BotUsedToolVo> getBatchBotUsedCount(@Param("ids") List<String> ids);

    /**
     * Get tool square total count
     *
     * @return
     */
    Integer getToolListCount(@Param("content") String content, @Param("tags") Long tags, @Param("adminUid") String uid);

    Long getMcpHeatValueByName(@Param("name") String name);

    List<ToolBox> getToolsLastVersion(@Param("toolIds") List<String> toolIds);
}
