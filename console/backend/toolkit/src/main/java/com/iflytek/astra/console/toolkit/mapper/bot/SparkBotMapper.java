package com.iflytek.astra.console.toolkit.mapper.bot;


import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.github.pagehelper.Page;
import com.iflytek.astra.console.toolkit.entity.dto.SparkBotVO;
import com.iflytek.astra.console.toolkit.entity.table.bot.SparkBot;
import com.iflytek.astra.console.toolkit.entity.vo.bot.SparkBotSquaerVo;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.*;


@Mapper

public interface SparkBotMapper extends BaseMapper<SparkBot> {
    int updateBotFloatedStatus(@Param("uid") String uid, @Param("excludeId") Long excludeId);

    List<SparkBotVO> listSparkBotByRepoId(@Param("repoId") Long repoId, @Param("uid") String uid);

    List<SparkBot> listSparkBotByToolId(@Param("toolId") String toolId, @Param("uid") String uid);

    List<SparkBotSquaerVo> listSparkBotSquareByToolId();

    Page<SparkBotVO> listSparkBotByCondition(@Param("content") String content, @Param("uid") String uid);

    List<SparkBotVO> botSquareByCondition(
                    @Param("content") String content,
                    @Param("uid") String uid,
                    @Param("favorites") Set<Long> favorites,
                    @Param("start") Integer start,
                    @Param("limit") Integer limit,
                    @Param("tagFlag") Integer tagFlag,
                    @Param("tags") Long tags,
                    @Param("adminUid") Long adminUid,
                    @Param("notContainIds") List<Long> notContainIds

    );

    Optional<SparkBot> findById(Long botId);

    /**
     * Get app square total count
     *
     * @return
     */
    Integer countSquareBots(@Param("content") String content, @Param("favorites") Set<Long> favorites, @Param("tags") Long tags);

    /**
     * Query whether public app has been added as personal app
     *
     * @param botId
     * @param userId
     * @return
     */
    Optional<SparkBot> isPersonal(@Param("botId") Long botId, @Param("userId") String userId);

    Page<SparkBotVO> getBotsContainPubAndPriv(@Param("content") String content, @Param("userId") String userId, @Param("favorites") Set<Long> favorites);

    /**
     * Whether model is being referenced
     *
     * @param uid
     * @param domain
     * @return
     */
    Integer checkDomainIsUsage(String uid, String domain);
}
