package com.iflytek.astra.console.toolkit.mapper.tool;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.iflytek.astra.console.toolkit.entity.dto.ToolFavoriteToolDto;
import com.iflytek.astra.console.toolkit.entity.table.tool.UserFavoriteTool;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.Optional;

/**
 * @author clliu19
 * @date 2024/05/23/14:55
 */
@Mapper
public interface UserFavoriteToolMapper extends BaseMapper<UserFavoriteTool> {
    /**
     * Query whether it is already favorited by userid and toolid
     *
     * @param userId
     * @param toolId
     * @return
     */
    Optional<UserFavoriteTool> findByUserIdAndToolId(@Param("userId") String userId, @Param("toolId") String toolId);

    /**
     * Query whether it is already favorited by userid and toolid
     *
     * @param userId
     * @param toolId
     * @return
     */
    Optional<UserFavoriteTool> findByUserIdAndMcpToolId(@Param("userId") String userId, @Param("toolId") String toolId);

    /**
     * Add favorite record
     *
     * @param userFavorite
     */
    void save(UserFavoriteTool userFavorite);

    /**
     * Get personal favorite tool list
     *
     * @param userId
     * @return
     */
    List<Long> findToolIdsByUserId(String userId);

    /**
     * Update favorite status
     *
     * @param userFavorite
     */
    void updateFavoriteStatus(UserFavoriteTool userFavorite);

    List<UserFavoriteTool> selectAllList();

    List<ToolFavoriteToolDto> findAllTooIdByUserId(@Param("userId") String userId);
}
