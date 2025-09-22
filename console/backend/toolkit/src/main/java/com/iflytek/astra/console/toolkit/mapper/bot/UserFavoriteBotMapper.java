package com.iflytek.astra.console.toolkit.mapper.bot;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.iflytek.astra.console.toolkit.entity.table.bot.UserFavoriteBot;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.Optional;

@Mapper
public interface UserFavoriteBotMapper extends BaseMapper<UserFavoriteBot> {
    /**
     * Query whether it is already favorited by userid and toolid
     *
     * @param userId
     * @param botId
     * @return
     */
    Optional<UserFavoriteBot> findByUserIdAndToolId(@Param("userId") String userId, @Param("botId") Long botId);

    /**
     * Add favorite record
     *
     * @param userFavorite
     */
    void save(UserFavoriteBot userFavorite);

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
    void updateFavoriteStatus(UserFavoriteBot userFavorite);

}
